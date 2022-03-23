package com.yubyf.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yubyf.datastore.DataStoreDelegate.Companion.getDataStoreDelegate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Delegate for [DataStore] with [Preferences].
 *
 * Obtain a instance through [Context.getDataStoreDelegate].
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
open class DataStoreDelegate private constructor(
    context: Context,
    name: String,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    migrate: Boolean = false,
) {
    //region Datastore members
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name,
        produceMigrations = { context ->
            if (migrate) listOf(SharedPreferencesMigration(context, name)) else emptyList()
        }
    )
    private val scope =
        CoroutineScope(scope.coroutineContext + SupervisorJob())
    private val dataStore = context.dataStore
    private val dataFlow = dataStore.data

    // Convert to SharedFlow optimized for sharing data among all collectors.
    private val dataSharedFlow = MutableSharedFlow<Pair<Preferences, Preferences.Key<*>?>>()

    /**
     * Cache the latest preferences map to collect the changed keys of preferences.
     */
    private var latestPreferences = ConcurrentHashMap<Preferences.Key<*>, Any>()
    //endregion

    //region Synchronization members
    private val mutex = Mutex()
    private val deferredMap = ConcurrentHashMap<String, Deferred<*>>()
    //endregion

    init {
        scope.launch {
            // Populate the preferences cache map at the start.
            latestPreferences.clear()
            dataFlow.onStart { awaitAllDeferred() }.firstOrNull()?.asMap()?.let {
                latestPreferences += it
            }
            dataFlow.collect { preferences ->
                val currPreferences = preferences.asMap()
                val latestKeys = latestPreferences.keys.toHashSet()
                if (currPreferences.isEmpty()) {
                    if (latestKeys.size > 1) {
                        // Clear operation.
                        dataSharedFlow.emit(Pair(preferences, null))
                    } else if (latestKeys.size == 1) {
                        // Due to the data update mechanism of DataStore,
                        // it is confusing whether the current operation is a remove operation
                        // or a clear operation when preferences containing only one element
                        // becomes empty.
                        // We consider the operation here to be a remove operation.
                        dataSharedFlow.emit(Pair(preferences, latestKeys.first()))
                    }
                } else {
                    currPreferences.forEach { (key, value) ->
                        if (latestKeys.contains(key)) {
                            if (value != latestPreferences[key]) {
                                // Modify operation.
                                dataSharedFlow.emit(Pair(preferences, key))
                            }
                        } else {
                            // Add operation.
                            dataSharedFlow.emit(Pair(preferences, key))
                        }
                        latestKeys.remove(key)
                    }
                    // Remove operations.
                    latestKeys.forEach {
                        dataSharedFlow.emit(Pair(preferences, it))
                    }
                }
                // Populate the preferences cache map after file changes.
                latestPreferences.clear()
                latestPreferences += currPreferences
            }
        }
    }

    //region Public methods
    /**
     * Collect the data flow with a provided [action] launched in coroutine with [scope].
     * The action block will run when a change happens to a preference.
     *
     * @param action The action block that will run.
     *
     * @return a reference to the launched coroutine as a [Job].
     * The coroutine is cancelled when the resulting job is [cancelled][Job.cancel].
     */
    fun collect(action: suspend (prefs: Preferences, key: Preferences.Key<*>?) -> Unit): Job {
        return scope.launch {
            dataSharedFlow.collect { (preferences, key) ->
                action.invoke(preferences, key)
            }
        }
    }

    /**
     * Suspending collect the data flow with a provided [action].
     * The action block will run when a change happens to a preference.
     *
     * @param action The action block that will run.
     */
    suspend fun collectSuspend(action: suspend (prefs: Preferences, key: Preferences.Key<*>?) -> Unit) {
        return dataSharedFlow.collect { (preferences, key) ->
            action.invoke(preferences, key)
        }
    }

    /**
     * Checks whether the preferences contains a preference.
     *
     * @param key The name of the preference to check.
     * @return Returns a flow containing the result of whether the preference exists in the list.
     */
    fun contains(key: String): Flow<Boolean> = dataFlow.onStart { awaitAllDeferred() }.map {
        // The [equals()] and [hashCode()] methods of [Preferences$Key] compare only their names.
        it.contains(preferencesKey<String>(key))
    }

    /**
     * Suspending function for [contains].
     * @see [contains]
     */
    suspend fun containsSuspend(key: String): Boolean = contains(key).firstOrNull() == true

    /**
     * Retrieve all values from the preferences.
     *
     * @return Returns a flow containing a read-only map of pairs key/value representing the preferences.
     */
    fun getAll(): Flow<Map<String, *>> {
        return dataFlow.onStart { awaitAllDeferred() }.conflate().transform {
            emit(it.asMap().mapKeys { (key, _) -> key.name })
        }
    }

    /**
     * Suspending function for [getAll].
     * @see [getAll]
     */
    suspend fun getAllSuspend(): Map<String, *> =
        getAll().firstOrNull() ?: HashMap<String, Nothing>()

    /**
     * Retrieve a [String] value from the preferences.
     *
     * @param key The name of the preference to retrieve.
     * @param defValue Value to return if this preference does not exist.
     *
     * @return Returns a flow containing the preference value which will be defValue
     * if it does not exist.
     */
    fun getString(key: String, defValue: String? = null): Flow<String?> =
        get(stringPreferencesKey(key), defValue)

    /**
     * Suspending function for [getString].
     * @see [getString]
     */
    suspend fun getStringSuspend(key: String, defValue: String? = null): String? =
        getString(key, defValue).firstOrNull()

    /**
     * Retrieve a read-only [Set] of String values from the preferences.
     *
     * @param key The name of the preference to retrieve.
     * @param defValues Values to return if this preference does not exist.
     *
     * @return Returns a flow containing the preference values which will be defValues
     * if they do not exist.
     */
    fun getStringSet(key: String, defValues: Set<String>? = null): Flow<Set<String>?> =
        get(stringSetPreferencesKey(key), defValues)

    /**
     * Suspending function for [getStringSet].
     * @see [getStringSet]
     */
    suspend fun getStringSetSuspend(key: String, defValues: Set<String>? = null): Set<String>? =
        getStringSet(key, defValues).firstOrNull()

    /**
     * Retrieve an [Int] value from the preferences.
     *
     * @param key The name of the preference to retrieve.
     * @param defValue Value to return if this preference does not exist.
     *
     * @return Returns a flow containing the preference value which will be defValue
     * if it does not exist.
     */
    fun getInt(key: String, defValue: Int = 0): Flow<Int> =
        getSafe(intPreferencesKey(key), defValue)

    /**
     * Suspending function for [getInt].
     * @see [getInt]
     */
    suspend fun getIntSuspend(key: String, defValue: Int = 0): Int =
        getInt(key, defValue).firstOrNull() ?: defValue

    /**
     * Retrieve an [Long] value from the preferences.
     *
     * @param key The name of the preference to retrieve.
     * @param defValue Value to return if this preference does not exist.
     *
     * @return Returns a flow containing the preference value which will be defValue
     * if it does not exist.
     */
    fun getLong(key: String, defValue: Long = 0): Flow<Long> =
        getSafe(longPreferencesKey(key), defValue)

    /**
     * Suspending function for [getLong].
     * @see [getLong]
     */
    suspend fun getLongSuspend(key: String, defValue: Long = 0): Long =
        getLong(key, defValue).firstOrNull() ?: defValue

    /**
     * Retrieve an [Float] value from the preferences.
     *
     * @param key The name of the preference to retrieve.
     * @param defValue Value to return if this preference does not exist.
     *
     * @return Returns a flow containing the preference value which will be defValue
     * if it does not exist.
     */
    fun getFloat(key: String, defValue: Float = 0F): Flow<Float> =
        getSafe(floatPreferencesKey(key), defValue)

    /**
     * Suspending function for [getFloat].
     * @see [getFloat]
     */
    suspend fun getFloatSuspend(key: String, defValue: Float = 0F): Float =
        getFloat(key, defValue).firstOrNull() ?: defValue

    /**
     * Retrieve an [Boolean] value from the preferences.
     *
     * @param key The name of the preference to retrieve.
     * @param defValue Value to return if this preference does not exist.
     *
     * @return Returns a flow containing the preference value which will be defValue
     * if it does not exist.
     */
    fun getBoolean(key: String, defValue: Boolean = false): Flow<Boolean> =
        getSafe(booleanPreferencesKey(key), defValue)

    /**
     * Suspending function for [getBoolean].
     * @see [getBoolean]
     */
    suspend fun getBooleanSuspend(key: String, defValue: Boolean = false): Boolean =
        getBoolean(key, defValue).firstOrNull() ?: defValue

    /**
     * Put a [T] value to the preferences.
     *
     * @param key The name of the preference to modify.
     * @param value The new value for the preference. Passing `null`
     *   for this argument is equivalent to calling [remove] with this key.
     */
    fun <T> put(key: String, value: T?) {
        edit { it[key] = value }
    }

    /**
     * Put multiple values to the preferences at once.
     *
     * @param map The pairs key/value data to be written. Passing `null` for a value
     * is equivalent to calling [remove] with its key.
     */
    fun bulkPut(map: Map<String, *>) {
        edit { map.forEach { (key, value) -> it[key] = value } }
    }

    /**
     * Remove a preference with specific [key] in the preferences.
     *
     * @param key The name of the preference to remove.
     */
    fun remove(key: String) {
        edit { it -= key }
    }

    /**
     * Remove all values from the preferences.
     */
    fun clear() {
        edit { it.clear() }
    }

    /**
     * Edit the value in DataStore asynchronous. Multiple edit operations are serialized.
     *
     * @param transaction block which accepts MutablePreferences that contains all the preferences
     * currently in DataStore. Same as parameter in `DataStore<Preferences>.edit()` method.
     */
    @Suppress("DeferredResultUnused")
    fun edit(transaction: (MutablePreferences) -> Unit) {
        deferredMap.forEach { (key, deferred) ->
            if (!deferred.isActive) deferredMap.remove(key)
        }
        val key = UUID.randomUUID().toString()
        runBlocking {
            mutex.withLock {
                deferredMap[key] = scope.async {
                    mutex.withLock {
                        dataStore.edit(transaction)
                        deferredMap.remove(key)
                    }
                }
            }
        }
    }

    /**
     * Edit the value in DataStore synchronous. Multiple edit operations are serialized.
     *
     * @param transaction block which accepts MutablePreferences that contains all the preferences
     * currently in DataStore. Same as parameter in `DataStore<Preferences>.edit()` method.
     */
    suspend fun editSuspend(transaction: (MutablePreferences) -> Unit): Boolean {
        awaitAllDeferred()
        mutex.withLock {
            dataStore.edit(transaction)
        }
        return true
    }
    //endregion

    //region Internal methods
    private fun <T> get(key: Preferences.Key<T>, default: T?): Flow<T?> {
        return dataFlow.onStart { awaitAllDeferred() }.map { it[key] ?: default }
    }

    private fun <T> getSafe(key: Preferences.Key<T>, default: T): Flow<T> {
        return dataFlow.onStart { awaitAllDeferred() }.map { it[key] ?: default }
    }

    @Suppress("DeferredResultUnused")
    private suspend fun awaitAllDeferred() {
        deferredMap.forEach { (key, deferred) ->
            if (deferred.isActive) deferred.await()
            deferredMap.remove(key)
        }
    }

    /**
     * An inline function to get a key for an [T] preference.
     *
     * @param name the name of the preference
     * @return the Preferences.Key<T> for [name]
     */
    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> preferencesKey(name: String): Preferences.Key<T> {
        return when (T::class) {
            Int::class -> intPreferencesKey(name) as Preferences.Key<T>
            String::class -> stringPreferencesKey(name) as Preferences.Key<T>
            Boolean::class -> booleanPreferencesKey(name) as Preferences.Key<T>
            Float::class -> floatPreferencesKey(name) as Preferences.Key<T>
            Long::class -> longPreferencesKey(name) as Preferences.Key<T>
            Set::class -> stringSetPreferencesKey(name) as Preferences.Key<T>
            else -> throw IllegalArgumentException("Type not supported: ${T::class.java}")
        }
    }
    //endregion

    companion object {
        @Volatile
        private var INSTANCES: WeakHashMap<String, DataStoreDelegate> =
            WeakHashMap<String, DataStoreDelegate>()

        /**
         * Get a [getDataStoreDelegate] instance to access the pairs key/value data in [DataStore]
         *
         * - In Kotlin, this is an extension method of [Context], simply use it like this:
         *
         * `context.getDataStoreDelegate("prefName"...)`
         *
         * - In Java, use this as a series of static overloaded methods:
         *
         * ```
         * DataStorePreferences.getDataStoreDelegate(context, "prefName")
         * DataStorePreferences.getDataStoreDelegate(context, "prefName", coroutineScope)
         * DataStorePreferences.getDataStoreDelegate(context, "prefName", coroutineScope, true)
         * ```
         *
         * **The received context should be `ApplicationContext` to avoid possible memory leaks.**
         *
         * @param name    The name of the preferences.
         * @param scope   The scope in which IO operations and transform functions will execute.
         * @param migrate Ture if you want to migrate the [SharedPreferences] file with
         * current [name] to [DataStore] file.
         *
         * @return a [DataStore] delegate with [Preferences] as a singleton.
         */
        @JvmStatic
        @JvmOverloads
        fun Context.getDataStoreDelegate(
            name: String,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            migrate: Boolean = false,
        ): DataStoreDelegate {
            val instance = INSTANCES[name]
            return instance ?: synchronized(DataStoreDelegate::class) {
                instance ?: DataStoreDelegate(this, name, scope, migrate)
                    .also { INSTANCES[name] = it }
            }
        }
    }
}