package com.yubyf.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yubyf.datastore.DataStorePreferences.Companion.getDataStorePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Implementation of the [SharedPreferences] interface for [DataStore].
 *
 * The primary constructor is private and the instance is obtained through [getDataStorePreferences].
 */
open class DataStorePreferences private constructor(
    context: Context,
    name: String,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    migrate: Boolean = false,
) : SharedPreferences {

    //region Datastore members
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name,
        produceMigrations = { context ->
            if (migrate) listOf(SharedPreferencesMigration(context, name)) else emptyList()
        }
    )
    private val innerScope =
        CoroutineScope(scope.coroutineContext + SupervisorJob())
    private val isMainScope =
        innerScope.coroutineContext[ContinuationInterceptor] == Dispatchers.Main[ContinuationInterceptor]
    private val dataStore = context.dataStore
    private val dataFlow = dataStore.data
    //endregion

    //region Synchronization members
    private val mutex = Mutex()
    private val deferredMap = ConcurrentHashMap<String, Deferred<*>>()
    //endregion

    //region Subscription members
    private val listeners = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Any>()
    private val empty = Any()
    private var subscriptionJob: Job? = null
    //endregion

    //region Override methods
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (subscriptionJob?.isActive != true) {
            subscriptionJob = innerScope.launch {
                dataFlow.cancellable().collect {
                    listeners.forEach { (listener, _) ->
                        // DataStore does not support partial updates or referential integrity.
                        // [Source](https://developer.android.google.cn/topic/libraries/architecture/datastore)
                        listener.onSharedPreferenceChanged(
                            this@DataStorePreferences, null)
                    }
                }
            }
        }
        listener?.run {
            listeners[listener] = empty
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listener?.run {
            if (listeners.contains(listener)) {
                listeners.remove(listener)
            }
            if (listeners.isEmpty()) {
                subscriptionJob?.cancel()
                subscriptionJob = null
            }
        }
    }

    override fun contains(key: String): Boolean {
        return runBlocking(if (isMainScope) EmptyCoroutineContext else innerScope.coroutineContext) {
            awaitAll()
            dataFlow.map {
                // The [equals()] and [hashCode()] methods of [Preferences$Key] compare only their names.
                it.contains(preferencesKey<String>(key))
            }.firstOrNull() ?: false
        }
    }

    override fun edit(): SharedPreferences.Editor {
        return Editor()
    }

    override fun getAll(): MutableMap<String, *> {
        val map = mutableMapOf<String, Any>()
        return runBlocking(if (isMainScope) EmptyCoroutineContext else innerScope.coroutineContext) {
            awaitAll()
            dataFlow.onEach {
                it.asMap().forEach { (key, value) -> map[key.name] = value }
            }.first()
            map
        }
    }

    override fun getString(key: String, defValue: String?): String? =
        get(stringPreferencesKey(key), defValue)

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        get(stringSetPreferencesKey(key), defValues)

    override fun getInt(key: String, defValue: Int): Int =
        getSafe(intPreferencesKey(key), defValue)

    override fun getLong(key: String, defValue: Long): Long =
        getSafe(longPreferencesKey(key), defValue)

    override fun getFloat(key: String, defValue: Float): Float =
        getSafe(floatPreferencesKey(key), defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        getSafe(booleanPreferencesKey(key), defValue)
    //endregion

    //region Internal methods
    private fun <T> get(key: Preferences.Key<T>, default: T? = null): T? {
        return runBlocking(if (isMainScope) EmptyCoroutineContext else innerScope.coroutineContext) {
            awaitAll()
            dataFlow.map {
                it[key] ?: default
            }.firstOrNull()
        }
    }

    private fun <T> getSafe(key: Preferences.Key<T>, default: T): T = get(key) ?: default

    @Suppress("DeferredResultUnused")
    private fun editAsync(action: (MutablePreferences) -> Unit) {
        deferredMap.forEach { (key, deferred) ->
            if (!deferred.isActive) deferredMap.remove(key)
        }
        val key = UUID.randomUUID().toString()
        deferredMap[key] = innerScope.async {
            mutex.withLock {
                dataStore.edit(action)
                deferredMap.remove(key)
            }
        }
    }

    private fun editSync(action: (MutablePreferences) -> Unit): Boolean =
        runBlocking(if (isMainScope) EmptyCoroutineContext else innerScope.coroutineContext) {
            awaitAll()
            dataStore.edit(action)
            true
        }

    @Suppress("DeferredResultUnused")
    private suspend fun awaitAll() {
        deferredMap.forEach { (key, deferred) ->
            if (deferred.isActive) deferred.await()
            deferredMap.remove(key)
        }
    }
    //endregion

    inner class Editor : SharedPreferences.Editor {

        private val putOpMap = linkedMapOf<String, Any?>()
        private val removeOpSet = mutableSetOf<String>()
        private var clearOp = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            putOpMap[key] = value
            return this
        }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor {
            putOpMap[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            putOpMap[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            putOpMap[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            putOpMap[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            putOpMap[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removeOpSet.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearOp = true
            return this
        }

        override fun commit(): Boolean {
            return this@DataStorePreferences.editSync { preferences ->
                run {
                    if (clearOp) {
                        preferences.clear()
                    } else {
                        removeOpSet.forEach { preferences.remove(it) }
                    }
                    putOpMap.forEach { (key, value) ->
                        preferences[key] = value
                    }
                    putOpMap.clear()
                }
            }
        }

        override fun apply() {
            this@DataStorePreferences.editAsync { preferences ->
                run {
                    if (clearOp) {
                        preferences.clear()
                    } else {
                        removeOpSet.forEach { preferences.remove(it) }
                    }
                    putOpMap.forEach { (key, value) ->
                        preferences[key] = value
                    }
                    putOpMap.clear()
                }
            }
        }

    }

    companion object {
        @Volatile
        private var INSTANCES: WeakHashMap<String, DataStorePreferences> =
            WeakHashMap<String, DataStorePreferences>()

        /**
         * Get a [DataStorePreferences] instance to access the key-value data in [DataStore]
         * with [SharedPreferences] interface.
         *
         * - In Kotlin, this is a extension method of [Context], simply use it like this:
         *
         *      `context.getDataStorePreferences("prefName"...)`
         *
         * - In Java, use this as a series of static overloaded methods:
         *
         *      - `DataStorePreferences.getDataStorePreferences(context, "prefName")`
         *      - `DataStorePreferences.getDataStorePreferences(context, "prefName", coroutineScope)`
         *      - `DataStorePreferences.getDataStorePreferences(context, "prefName", coroutineScope, true)`
         *
         * **The received context should be `ApplicationContext` to avoid possible memory leaks.**
         *
         * @param name    The name of the preferences.
         * @param scope   The scope in which IO operations and transform functions will execute.
         * @param migrate Ture if you want to migrate the [SharedPreferences] file with
         * current [name] to [DataStore] file.
         *
         * @return a [DataStore] delegate that implements the [SharedPreferences] interface as a singleton.
         */
        @JvmStatic
        @JvmOverloads
        fun Context.getDataStorePreferences(
            name: String,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            migrate: Boolean = false,
        ): DataStorePreferences {
            val instance = INSTANCES[name]
            return instance ?: synchronized(DataStorePreferences::class) {
                instance ?: DataStorePreferences(this, name, scope, migrate)
                    .also { INSTANCES[name] = it }
            }
        }
    }
}

//region Extension functions for [MutablePreferences]
private fun MutablePreferences.remove(key: String) = remove(stringPreferencesKey(key))

@Suppress("UNCHECKED_CAST")
private operator fun MutablePreferences.set(key: String, value: Any?) {
    val preferenceKey: Preferences.Key<*>
    when (value) {
        is Int -> {
            preferenceKey = intPreferencesKey(key)
            this[preferenceKey] = value
        }
        is String -> {
            preferenceKey = stringPreferencesKey(key)
            this[preferenceKey] = value
        }
        is Boolean -> {
            preferenceKey = booleanPreferencesKey(key)
            this[preferenceKey] = value
        }
        is Float -> {
            preferenceKey = floatPreferencesKey(key)
            this[preferenceKey] = value
        }
        is Long -> {
            preferenceKey = longPreferencesKey(key)
            this[preferenceKey] = value
        }
        is Set<*> -> {
            preferenceKey = stringSetPreferencesKey(key)
            this[preferenceKey] = value as Set<String>
        }
        else -> {
            value ?: run {
                preferenceKey = stringPreferencesKey(key)
                this.remove(preferenceKey)
                return
            }
            throw IllegalArgumentException("Type not supported: ${value::class.java}")
        }
    }
}
//endregion

/**
 * An inline function to get a key for an [T] preference.
 *
 * @param name the name of the preference
 * @return the Preferences.Key<T> for [name]
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> preferencesKey(name: String): Preferences.Key<T> {
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