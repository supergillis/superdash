package com.superdash.settings

import com.superdash.core.persistence.KeyValueStore
import com.superdash.core.persistence.Setting
import com.superdash.core.persistence.observe
import com.superdash.core.persistence.write
import com.superdash.immich.ImmichSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App-owned [ImmichSettings] backed by [KeyValueStore].
 *
 * Reuses the legacy DataStore keys and defaults previously declared on
 * [SettingsRepository] so the carve-out is zero-migration.
 */
internal class SettingsRepositoryImmichSettings(
    private val store: KeyValueStore,
    // The API key is a bearer credential, so it is encrypted at rest.
    private val secret: SecretString = SecretString.Identity,
) : ImmichSettings {
    override val url: Flow<String> = store.observe(URL)

    override val apiKey: Flow<String> = store.observe(API_KEY).map { stored -> secret.reveal(stored) }

    override val album: Flow<String> = store.observe(ALBUM)

    override val catalogTtlHours: Flow<Int> = store.observe(CATALOG_TTL_HOURS)

    override suspend fun setUrl(value: String) = store.write(URL, value)

    override suspend fun setApiKey(value: String) = store.write(API_KEY, secret.conceal(value))

    override suspend fun setAlbum(value: String) = store.write(ALBUM, value)

    override suspend fun setCatalogTtlHours(value: Int) = store.write(CATALOG_TTL_HOURS, value)

    private companion object {
        val URL = Setting(key = "immich_url", default = "")
        val API_KEY = Setting(key = "immich_api_key", default = "")
        val ALBUM = Setting(key = "immich_album", default = "")
        val CATALOG_TTL_HOURS = Setting(key = "immich_catalog_ttl_hours", default = 24)
    }
}
