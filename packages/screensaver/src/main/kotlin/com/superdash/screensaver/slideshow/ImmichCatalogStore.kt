package com.superdash.screensaver.slideshow

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.superdash.core.log.Log
import com.superdash.immich.ImmichCatalogEntry
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val log = Log("ImmichCatalogStore")

/** Persists the Immich slideshow catalog across process restarts. */
interface ImmichCatalogStore {
    suspend fun load(): Snapshot?

    suspend fun save(album: String, entries: List<ImmichCatalogEntry>, fetchedAtMs: Long)

    suspend fun clear()

    data class Snapshot(
        val album: String,
        val entries: List<ImmichCatalogEntry>,
        val fetchedAtMs: Long,
    )
}

class InMemoryImmichCatalogStore : ImmichCatalogStore {
    private var snapshot: ImmichCatalogStore.Snapshot? = null

    override suspend fun load() = snapshot

    override suspend fun save(album: String, entries: List<ImmichCatalogEntry>, fetchedAtMs: Long) {
        snapshot = ImmichCatalogStore.Snapshot(album, entries, fetchedAtMs)
    }

    override suspend fun clear() {
        snapshot = null
    }
}

private val Context.immichCatalog by preferencesDataStore(name = "immich_cache")

class DataStoreImmichCatalogStore(
    context: Context,
) : ImmichCatalogStore {
    private val store: DataStore<Preferences> = context.applicationContext.immichCatalog
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): ImmichCatalogStore.Snapshot? {
        val prefs = store.data.first()
        val payload = prefs[CATALOG_PAYLOAD] ?: return null
        val entries =
            runCatching { json.decodeFromString<List<ImmichCatalogEntry>>(payload) }
                .getOrElse {
                    log.w("catalog payload unparseable, dropping", it)
                    return null
                }
        return ImmichCatalogStore.Snapshot(
            album = prefs[CATALOG_ALBUM] ?: "",
            entries = entries,
            fetchedAtMs = prefs[CATALOG_FETCHED_AT] ?: 0L,
        )
    }

    override suspend fun save(album: String, entries: List<ImmichCatalogEntry>, fetchedAtMs: Long) {
        val payload = json.encodeToString(entries)
        store.edit {
            it[CATALOG_PAYLOAD] = payload
            it[CATALOG_ALBUM] = album
            it[CATALOG_FETCHED_AT] = fetchedAtMs
        }
    }

    override suspend fun clear() {
        store.edit {
            it.remove(CATALOG_PAYLOAD)
            it.remove(CATALOG_ALBUM)
            it.remove(CATALOG_FETCHED_AT)
        }
    }

    private companion object {
        val CATALOG_PAYLOAD = stringPreferencesKey("catalog_payload")
        val CATALOG_ALBUM = stringPreferencesKey("catalog_album")
        val CATALOG_FETCHED_AT = longPreferencesKey("catalog_fetched_at")
    }
}
