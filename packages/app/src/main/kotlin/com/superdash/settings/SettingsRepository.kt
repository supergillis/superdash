package com.superdash.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.superdash.core.persistence.DataStoreKeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettings by preferencesDataStore(name = "app_settings")

class SettingsRepository(
    context: Context,
) {
    internal val dataStore = context.applicationContext.appSettings
    private val store get() = dataStore
    private val kioskSettings = SettingsRepositoryKioskSettings(DataStoreKeyValueStore(dataStore))

    // null or "" means URL not set (NeedsSetup); nonblank means Configured.
    val haUrl: Flow<String?> = store.data.map { it[HA_URL] }

    suspend fun setHaUrl(value: String?) = store.edit { it[HA_URL] = value ?: "" }

    /** Async snapshot accessor for use ONLY in BroadcastReceiver.goAsync() bodies. */
    suspend fun snapshot(): Snapshot =
        Snapshot(
            haUrl = haUrl.first(),
            keepScreenOn = kioskSettings.keepScreenOn.first(),
            startOnBoot = kioskSettings.startOnBoot.first(),
            launchOnWake = kioskSettings.launchOnWake.first(),
        )

    data class Snapshot(
        val haUrl: String?,
        val keepScreenOn: Boolean,
        val startOnBoot: Boolean,
        val launchOnWake: Boolean,
    )

    companion object {
        private val HA_URL = stringPreferencesKey("ha_url")
    }
}
