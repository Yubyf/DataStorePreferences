package com.yubyf.datastore

import androidx.datastore.preferences.core.*

/**
 * Extension functions for [MutablePreferences]
 */
fun MutablePreferences.remove(key: String) = remove(stringPreferencesKey(key))

@Suppress("UNCHECKED_CAST")
operator fun MutablePreferences.set(key: String, value: Any?) {
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