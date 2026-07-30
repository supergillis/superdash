package com.superdash.settings

import com.superdash.core.persistence.KeyValueStore
import com.superdash.core.persistence.Setting
import com.superdash.core.persistence.mutate
import com.superdash.core.persistence.observe
import com.superdash.core.persistence.write
import com.superdash.feed.FeedConfig
import com.superdash.feed.FeedSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

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

    override val feeds: Flow<List<FeedConfig>> =
        store
            .observe(FEEDS)
            .onStart { migrateAutoCloseIfNeeded() }
            .map { FeedConfig.decodeList(it) }

    override suspend fun setEnabled(value: Boolean) = store.write(ENABLED, value)

    override suspend fun upsertFeed(config: FeedConfig) {
        val sanitised = config.copy(autoCloseSec = config.autoCloseSec.coerceIn(0, 300))
        store.mutate(FEEDS) { encoded ->
            val current = FeedConfig.decodeList(encoded)
            val updated =
                if (current.any { it.id == sanitised.id }) {
                    current.map {
                        if (it.id == sanitised.id) {
                            sanitised
                        } else {
                            it
                        }
                    }
                } else {
                    current + sanitised
                }
            FeedConfig.encodeList(updated)
        }
        // A direct per-feed write means the store is already on the new model; without
        // this, a write that lands before `feeds` is ever collected would get clobbered
        // by the legacy stamp the next time `migrateAutoCloseIfNeeded` runs.
        store.write(MIGRATED_V2, true)
    }

    override suspend fun removeFeed(id: String) {
        store.mutate(FEEDS) { encoded ->
            val current = FeedConfig.decodeList(encoded)
            FeedConfig.encodeList(current.filterNot { it.id == id })
        }
        store.write(MIGRATED_V2, true)
    }

    /** Auto-close moved from one global setting to a field on every feed. Stamp the
     *  old global onto stored feeds once so upgrades keep their configured timeout. */
    private suspend fun migrateAutoCloseIfNeeded() {
        if (store.flow(MIGRATED_V2.key, MIGRATED_V2.default).first()) {
            return
        }
        val legacyAutoCloseSec = store.flow(LEGACY_AUTO_CLOSE_SEC.key, LEGACY_AUTO_CLOSE_SEC.default).first()
        store.mutate(FEEDS) { encoded ->
            val current = FeedConfig.decodeList(encoded)
            FeedConfig.encodeList(current.map { it.copy(autoCloseSec = legacyAutoCloseSec) })
        }
        store.write(MIGRATED_V2, true)
    }

    private companion object {
        val ENABLED = Setting(key = "doorbell_enabled", default = false)
        val FEEDS = Setting(key = "doorbells", default = "[]")
        val MIGRATED_V2 = Setting(key = "feeds_migrated_v2", default = false)
        val LEGACY_AUTO_CLOSE_SEC = Setting(key = "doorbell_auto_close_sec", default = 60)
    }
}
