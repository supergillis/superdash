package com.superdash.settings

import com.superdash.core.persistence.KeyValueStore
import com.superdash.core.persistence.Setting
import com.superdash.core.persistence.mutate
import com.superdash.core.persistence.observe
import com.superdash.core.persistence.write
import com.superdash.doorbell.DoorbellConfig
import com.superdash.doorbell.DoorbellSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App-owned [DoorbellSettings] backed by [KeyValueStore].
 *
 * Uses the same DataStore keys, defaults, and coerce ranges as the legacy
 * fields on [SettingsRepository] so the upgrade is zero-migration.
 */
internal class SettingsRepositoryDoorbellSettings(
    private val store: KeyValueStore,
) : DoorbellSettings {
    override val enabled: Flow<Boolean> = store.observe(ENABLED)

    override val autoCloseSec: Flow<Int> = store.observe(AUTO_CLOSE_SEC)

    override val doorbells: Flow<List<DoorbellConfig>> =
        store.observe(DOORBELLS).map { DoorbellConfig.decodeList(it) }

    override suspend fun setEnabled(value: Boolean) = store.write(ENABLED, value)

    override suspend fun setAutoCloseSec(value: Int) = store.write(AUTO_CLOSE_SEC, value)

    override suspend fun upsertDoorbell(config: DoorbellConfig) {
        store.mutate(DOORBELLS) { encoded ->
            val current = DoorbellConfig.decodeList(encoded)
            val updated =
                if (current.any { it.id == config.id }) {
                    current.map {
                        if (it.id == config.id) {
                            config
                        } else {
                            it
                        }
                    }
                } else {
                    current + config
                }
            DoorbellConfig.encodeList(updated)
        }
    }

    override suspend fun removeDoorbell(id: String) {
        store.mutate(DOORBELLS) { encoded ->
            val current = DoorbellConfig.decodeList(encoded)
            DoorbellConfig.encodeList(current.filterNot { it.id == id })
        }
    }

    private companion object {
        val ENABLED = Setting(key = "doorbell_enabled", default = false)
        val AUTO_CLOSE_SEC =
            Setting(key = "doorbell_auto_close_sec", default = 60, write = { it.coerceIn(0, 300) })
        val DOORBELLS = Setting(key = "doorbells", default = "[]")
    }
}
