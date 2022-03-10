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
@Suppress("unused")
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
    //endregion

    //region Synchronization members
    private val mutex = Mutex()
    private val deferredMap = ConcurrentHashMap<String, Deferred<*>>()
    //endregion

    //region Subscription
    private var subscriptionJob: Job? = null
    //endregion

    //region Public methods
    fun subscribe(action: suspend (value: Preferences) -> Unit) {
        if (subscriptionJob?.isActive != true) {
            subscriptionJob = scope.launch { dataFlow.cancellable().collect(action) }
        }
    }

    fun unSubscribe() {
        subscriptionJob?.cancel()
        subscriptionJob = null
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
     * Retrieve all values from the preferences.
     *
     * @return Returns a flow containing a read-only map of pairs key/value representing the preferences.
     */
    fun getAll(): Flow<Map<String, *>> {
        return dataFlow.onStart { awaitAllDeferred() }.transform {
            emit(it.asMap().mapKeys { (key, _) -> key.name })
        }
    }

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
     * Put a [T] value to the preferences.
     *
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.  Passing `null`
     *   for this argument is equivalent to calling [remove] with this key.
     */
    fun <T> put(key: String, value: T?) {
        editAsync { it[key] = value }
    }

    /**
     * Remove a preference with specific [key] in the preferences.
     *
     * @param key The name of the preference to remove.
     */
    fun remove(key: String) {
        editAsync { it.remove(key) }
    }

    /**
     * Remove all values from the preferences.
     */
    fun clear() {
        editAsync { it.clear() }
    }

    /**
     * Edit the value in DataStore asynchronous. Multiple edit operations are serialized.
     *
     * @param transaction block which accepts MutablePreferences that contains all the preferences
     * currently in DataStore. Same as parameter in `DataStore<Preferences>.edit()` method.
     */
    @Suppress("DeferredResultUnused")
    fun editAsync(transaction: (MutablePreferences) -> Unit) {
        deferredMap.forEach { (key, deferred) ->
            if (!deferred.isActive) deferredMap.remove(key)
        }
        val key = UUID.randomUUID().toString()
        deferredMap[key] = scope.async {
            mutex.withLock {
                dataStore.edit(transaction)
                deferredMap.remove(key)
            }
        }
    }

    /**
     * Edit the value in DataStore synchronous. Multiple edit operations are serialized.
     *
     * @param transaction block which accepts MutablePreferences that contains all the preferences
     * currently in DataStore. Same as parameter in `DataStore<Preferences>.edit()` method.
     */
    suspend fun editSync(transaction: (MutablePreferences) -> Unit): Boolean {
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