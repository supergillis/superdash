package com.superdash.core.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreKeyValueStore(
    private val store: DataStore<Preferences>,
) : KeyValueStore {
    override fun <T : Any> flow(
        key: String,
        default: T,
    ): Flow<T> {
        val preferencesKey = keyFor(key, default)
        return store.data.map { prefs ->
            prefs[preferencesKey] ?: default
        }
    }

    override suspend fun <T : Any> set(
        key: String,
        value: T,
    ) {
        val preferencesKey = keyFor(key, value)
        store.edit { prefs ->
            prefs[preferencesKey] = value
        }
    }

    override suspend fun <T : Any> mutate(
        key: String,
        default: T,
        transform: (T) -> T,
    ): T {
        val preferencesKey = keyFor(key, default)
        var updated: T? = null
        store.edit { prefs ->
            val current = prefs[preferencesKey] ?: default
            val next = transform(current)
            prefs[preferencesKey] = next
            updated = next
        }
        return updated ?: error("settings mutation did not run")
    }

    private fun <T : Any> keyFor(
        key: String,
        sample: T,
    ): Preferences.Key<T> {
        @Suppress("UNCHECKED_CAST")
        return when (sample) {
            is Boolean -> booleanPreferencesKey(key)
            is Int -> intPreferencesKey(key)
            is Long -> longPreferencesKey(key)
            is Float -> floatPreferencesKey(key)
            is Double -> doublePreferencesKey(key)
            is String -> stringPreferencesKey(key)
            else -> error("unsupported settings type: ${sample::class}")
        } as Preferences.Key<T>
    }
}
