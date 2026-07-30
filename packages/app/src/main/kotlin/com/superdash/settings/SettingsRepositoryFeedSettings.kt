package com.superdash.settings

import com.superdash.core.persistence.KeyValueStore
import com.superdash.core.persistence.Setting
import com.superdash.core.persistence.mutate
import com.superdash.core.persistence.observe
import com.superdash.core.persistence.write
import com.superdash.feed.FeedConfig
import com.superdash.feed.FeedSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App-owned [FeedSettings] backed by [KeyValueStore].
 *
 * Uses the same DataStore keys, defaults, and coerce ranges as the legacy
 * fields on [SettingsRepository] so the upgrade is zero-migration.
 */
internal class SettingsRepositoryFeedSettings(
    private val store: KeyValueStore,
) : FeedSettings {
    override val enabled: Flow<Boolean> = store.observe(ENABLED)

    override val autoCloseSec: Flow<Int> = store.observe(AUTO_CLOSE_SEC)

    override val feeds: Flow<List<FeedConfig>> =
        store.observe(FEEDS).map { FeedConfig.decodeList(it) }

    override suspend fun setEnabled(value: Boolean) = store.write(ENABLED, value)

    override suspend fun setAutoCloseSec(value: Int) = store.write(AUTO_CLOSE_SEC, value)

    override suspend fun upsertFeed(config: FeedConfig) {
        store.mutate(FEEDS) { encoded ->
            val current = FeedConfig.decodeList(encoded)
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
            FeedConfig.encodeList(updated)
        }
    }

    override suspend fun removeFeed(id: String) {
        store.mutate(FEEDS) { encoded ->
            val current = FeedConfig.decodeList(encoded)
            FeedConfig.encodeList(current.filterNot { it.id == id })
        }
    }

    private companion object {
        val ENABLED = Setting(key = "doorbell_enabled", default = false)
        val AUTO_CLOSE_SEC =
            Setting(key = "doorbell_auto_close_sec", default = 60, write = { it.coerceIn(0, 300) })
        val FEEDS = Setting(key = "doorbells", default = "[]")
    }
}
