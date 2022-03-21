package com.yubyf.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import com.yubyf.datastore.DataStoreDelegate.Companion.getDataStoreDelegate
import com.yubyf.datastore.DataStorePreferences.Companion.getDataStorePreferences
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Implementation of the [SharedPreferences] interface for [DataStore].
 *
 * Obtain a instance through [Context.getDataStorePreferences].
 */
open class DataStorePreferences private constructor(
    context: Context,
    name: String,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    migrate: Boolean = false,
) : SharedPreferences {

    //region Datastore members
    private val innerScope =
        CoroutineScope(scope.coroutineContext + SupervisorJob())
    private val isMainScope =
        innerScope.coroutineContext[ContinuationInterceptor] == Dispatchers.Main[ContinuationInterceptor]
    private val delegate = context.getDataStoreDelegate(name, scope, migrate)
    //endregion

    //region Subscription members
    private val listeners = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Job>()
    //endregion

    //region Override methods
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        val collectorJob = delegate.collect {
            listeners.forEach { (listener, _) ->
                // DataStore does not support partial updates or referential integrity.
                // [Source](https://developer.android.google.cn/topic/libraries/architecture/datastore)
                listener.onSharedPreferenceChanged(
                    this@DataStorePreferences, null)
            }
        }
        listener?.run {
            listeners[listener] = collectorJob
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listener?.run {
            if (listeners.contains(listener)) {
                listeners.remove(listener)?.cancel()
            }
        }
    }

    override fun contains(key: String): Boolean = runBlocking { delegate.containsSuspend(key) }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun getAll(): Map<String, *> = runBlocking { delegate.getAllSuspend() }

    override fun getString(key: String, defValue: String?): String? =
        runBlocking { delegate.getStringSuspend(key, defValue) }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        runBlocking { delegate.getStringSetSuspend(key, defValues) }

    override fun getInt(key: String, defValue: Int): Int =
        runBlocking { delegate.getIntSuspend(key, defValue) }

    override fun getLong(key: String, defValue: Long): Long =
        runBlocking { delegate.getLongSuspend(key, defValue) }

    override fun getFloat(key: String, defValue: Float): Float =
        runBlocking { delegate.getFloatSuspend(key, defValue) }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        runBlocking { delegate.getBooleanSuspend(key, defValue) }
    //endregion

    //region Internal methods
    private fun <T> runBlocking(block: suspend CoroutineScope.() -> T): T {
        return runBlocking(if (isMainScope) EmptyCoroutineContext else innerScope.coroutineContext,
            block)
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
            return runBlocking {
                delegate.editSuspend { preferences ->
                    run {
                        if (clearOp) {
                            preferences.clear()
                        } else {
                            removeOpSet.forEach { preferences -= it }
                        }
                        putOpMap.forEach { (key, value) ->
                            preferences[key] = value
                        }
                        putOpMap.clear()
                    }
                }
            }
        }

        override fun apply() {
            delegate.edit { preferences ->
                run {
                    if (clearOp) {
                        preferences.clear()
                    } else {
                        removeOpSet.forEach { preferences -= it }
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
         * Get a [DataStorePreferences] instance to access the pairs key/value data in [DataStore]
         * with [SharedPreferences] interface.
         *
         * - In Kotlin, this is an extension method of [Context], simply use it like this:
         *
         * `context.getDataStorePreferences("prefName"...)`
         *
         * - In Java, use this as a series of static overloaded methods:
         *
         * ```
         * DataStorePreferences.getDataStorePreferences(context, "prefName")
         * DataStorePreferences.getDataStorePreferences(context, "prefName", coroutineScope)
         * DataStorePreferences.getDataStorePreferences(context, "prefName", coroutineScope, true)
         * ```
         *
         * **The received context should be `ApplicationContext` to avoid possible memory leaks.**
         *
         * @param name    The name of the preferences.
         * @param scope   The scope in which IO operations and transform functions will execute.
         * @param migrate Ture if you want to migrate the [SharedPreferences] file with
         * current [name] to [DataStore] file.
         *
         * @return a [DataStorePreferences] instance that implements the [SharedPreferences] interface as a singleton.
         */
        @JvmStatic
        @JvmOverloads
        fun Context.getDataStorePreferences(
            name: String,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            migrate: Boolean = false,
        ): SharedPreferences {
            val instance = INSTANCES[name]
            return instance ?: synchronized(DataStorePreferences::class) {
                instance ?: DataStorePreferences(this, name, scope, migrate)
                    .also { INSTANCES[name] = it }
            }
        }
    }
}