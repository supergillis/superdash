package com.superdash

import android.content.Context
import com.superdash.immich.ImmichApiClient
import com.superdash.immich.ImmichSettings
import com.superdash.immich.okhttp.ImmichServerOrigin
import com.superdash.immich.okhttp.immichServerOrigin
import com.superdash.screensaver.slideshow.DataStoreImmichCatalogStore
import com.superdash.screensaver.slideshow.ImmichCatalogStore
import com.superdash.screensaver.slideshow.ImmichSlideshowSource
import com.superdash.settings.RefreshImmichCatalogResult
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

class ImmichSubgraph(
    private val scope: CoroutineScope,
    private val settings: ImmichSettings,
    private val httpClient: HttpClient,
    appContext: Context,
) {
    val client: StateFlow<ImmichApiClient?> =
        combine(
            settings.url,
            settings.apiKey,
        ) { url, apiKey ->
            if (url.isNotBlank() && apiKey.isNotBlank()) {
                ImmichApiClient(httpClient, url, apiKey)
            } else {
                null
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val serverOrigin: StateFlow<ImmichServerOrigin?> =
        settings.url
            .map { url ->
                immichServerOrigin(url)
            }.stateIn(scope, SharingStarted.Eagerly, null)

    val apiKey: StateFlow<String> =
        settings.apiKey.stateIn(scope, SharingStarted.Eagerly, "")

    val catalogStore: ImmichCatalogStore = DataStoreImmichCatalogStore(appContext)

    /**
     * Weak reference to the currently active [ImmichSlideshowSource].
     * Held weakly so that Compose disposal (which calls [ImmichSlideshowSource.close]) is the
     * authoritative lifetime owner; the subgraph never prevents garbage-collection.
     *
     * Wrapped in [AtomicReference] because [registerSource] is called from Compose (main)
     * while [refreshCatalogNow] reads it from a coroutine on [scope].
     */
    private val activeSource: AtomicReference<WeakReference<ImmichSlideshowSource>?> = AtomicReference(null)

    /** Called by [ScreensaverHost] after constructing a new [ImmichSlideshowSource]. */
    fun registerSource(source: ImmichSlideshowSource) {
        activeSource.set(WeakReference(source))
    }

    /**
     * Triggers an immediate catalog refresh. If a live [ImmichSlideshowSource] is registered
     * (the screensaver is currently composed), refresh through it so the next slide reflects
     * the new catalog. Otherwise refresh directly into the catalog store; the next source
     * instance will pick it up on construction.
     */
    suspend fun refreshCatalogNow(): RefreshImmichCatalogResult {
        val source = activeSource.get()?.get()
        if (source != null) {
            return runCatching { source.refreshCatalog() }
                .fold(
                    onSuccess = { count -> RefreshImmichCatalogResult.Success(count) },
                    onFailure = { e -> RefreshImmichCatalogResult.Failure(e.message ?: "unknown error") },
                )
        }
        val apiClient =
            client.value
                ?: return RefreshImmichCatalogResult.Failure("immich not configured")
        val album = settings.album.first()
        return runCatching {
            val fetched =
                if (album.isBlank()) {
                    apiClient.listCatalog()
                } else {
                    val resolved =
                        apiClient.getAlbumByName(album)
                            ?: throw IllegalStateException("immich album not found: $album")
                    apiClient.listCatalog(albumId = resolved.id)
                }
            if (fetched.isEmpty()) {
                throw IllegalStateException("immich returned an empty catalog")
            }
            catalogStore.save(album, fetched, System.currentTimeMillis())
            fetched.size
        }.fold(
            onSuccess = { count -> RefreshImmichCatalogResult.Success(count) },
            onFailure = { e -> RefreshImmichCatalogResult.Failure(e.message ?: "unknown error") },
        )
    }
}
