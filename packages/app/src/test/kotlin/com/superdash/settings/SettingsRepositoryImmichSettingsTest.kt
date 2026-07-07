package com.superdash.settings

import com.superdash.core.persistence.InMemoryKeyValueStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryImmichSettingsTest {
    @Test
    fun `url defaults to empty`() =
        runTest {
            val settings = SettingsRepositoryImmichSettings(InMemoryKeyValueStore())
            assertEquals("", settings.url.first())
        }

    @Test
    fun `apiKey defaults to empty`() =
        runTest {
            val settings = SettingsRepositoryImmichSettings(InMemoryKeyValueStore())
            assertEquals("", settings.apiKey.first())
        }

    @Test
    fun `album defaults to empty`() =
        runTest {
            val settings = SettingsRepositoryImmichSettings(InMemoryKeyValueStore())
            assertEquals("", settings.album.first())
        }

    @Test
    fun `catalogTtlHours defaults to 24`() =
        runTest {
            val settings = SettingsRepositoryImmichSettings(InMemoryKeyValueStore())
            assertEquals(24, settings.catalogTtlHours.first())
        }

    @Test
    fun `setUrl persists value`() =
        runTest {
            val settings = SettingsRepositoryImmichSettings(InMemoryKeyValueStore())
            settings.setUrl("http://immich.local:2283")
            assertEquals("http://immich.local:2283", settings.url.first())
        }

    @Test
    fun `setApiKey persists value`() =
        runTest {
            val settings = SettingsRepositoryImmichSettings(InMemoryKeyValueStore())
            settings.setApiKey("secret")
            assertEquals("secret", settings.apiKey.first())
        }

    @Test
    fun `setAlbum persists value`() =
        runTest {
            val settings = SettingsRepositoryImmichSettings(InMemoryKeyValueStore())
            settings.setAlbum("Screensaver")
            assertEquals("Screensaver", settings.album.first())
        }

    @Test
    fun `setCatalogTtlHours persists value`() =
        runTest {
            val settings = SettingsRepositoryImmichSettings(InMemoryKeyValueStore())
            settings.setCatalogTtlHours(48)
            assertEquals(48, settings.catalogTtlHours.first())
        }

    @Test
    fun `legacy datastore keys are reused for zero-migration`() =
        runTest {
            val store = InMemoryKeyValueStore()
            // Seed using the legacy keys that previously lived on SettingsRepository.
            store.set("immich_url", "http://legacy:2283")
            store.set("immich_api_key", "legacy-key")
            store.set("immich_album", "Legacy")
            store.set("immich_catalog_ttl_hours", 12)
            val settings = SettingsRepositoryImmichSettings(store)
            assertEquals("http://legacy:2283", settings.url.first())
            assertEquals("legacy-key", settings.apiKey.first())
            assertEquals("Legacy", settings.album.first())
            assertEquals(12, settings.catalogTtlHours.first())
        }

    @Test
    fun `api key is concealed at rest and revealed on read`() =
        runTest {
            val store = InMemoryKeyValueStore()
            val settings = SettingsRepositoryImmichSettings(store, PrefixSecret)
            settings.setApiKey("bearer-token")

            // Raw stored value is concealed (read it back through an Identity view).
            assertEquals(
                "enc:bearer-token",
                SettingsRepositoryImmichSettings(store, SecretString.Identity).apiKey.first(),
            )
            assertEquals("bearer-token", settings.apiKey.first())
        }

    @Test
    fun `legacy plaintext api key is read back unchanged`() =
        runTest {
            val store = InMemoryKeyValueStore()
            // A value written before encryption existed has no prefix.
            SettingsRepositoryImmichSettings(store, SecretString.Identity).setApiKey("legacy-plaintext")

            assertEquals(
                "legacy-plaintext",
                SettingsRepositoryImmichSettings(store, PrefixSecret).apiKey.first(),
            )
        }

    private object PrefixSecret : SecretString {
        override fun conceal(plain: String): String = if (plain.isEmpty()) plain else "enc:$plain"

        override fun reveal(stored: String): String =
            if (stored.startsWith("enc:")) stored.removePrefix("enc:") else stored
    }
}
