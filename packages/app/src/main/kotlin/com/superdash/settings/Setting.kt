package com.superdash.settings

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class Setting<T>(
    private val store: DataStore<Preferences>,
    private val key: Preferences.Key<T>,
    private val default: T,
    private val read: (T) -> T = { it },
    private val write: (T) -> T = { it },
) {
    val flow: Flow<T> = store.data.map { read(it[key] ?: default) }

    suspend fun set(value: T) = store.edit { it[key] = write(value) }

    suspend fun get(): T = flow.first()
}

internal inline fun <reified T : Any> DataStore<Preferences>.setting(
    name: String,
    default: T,
    noinline read: (T) -> T = { it },
    noinline write: (T) -> T = { it },
): Setting<T> {
    @Suppress("UNCHECKED_CAST")
    val key: Preferences.Key<T> =
        when (T::class) {
            Boolean::class -> booleanPreferencesKey(name)
            Int::class -> intPreferencesKey(name)
            Long::class -> longPreferencesKey(name)
            Float::class -> floatPreferencesKey(name)
            Double::class -> doublePreferencesKey(name)
            String::class -> stringPreferencesKey(name)
            else -> error("unsupported settings type: ${T::class}")
        } as Preferences.Key<T>
    return Setting(this, key, default, read, write)
}
