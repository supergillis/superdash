package com.superdash.immich

import com.superdash.core.log.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private val log = Log("ImmichApi")

class ImmichApiClient(
    private val httpClient: HttpClient,
    serverUrl: String,
    private val apiKey: String,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val baseUrl: String = serverUrl.trimEnd('/')

    /** Paginate Immich search/metadata to build a slim catalog of every asset
     *  the slideshow can show. When [albumId] is non-null, the same paginated pass
     *  carries an `albumIds` filter so the server returns only that album's assets;
     *  otherwise it returns the whole library. Either way there is no `type` filter —
     *  Immich returns IMAGE + VIDEO + AUDIO + OTHER in one mixed stream — and
     *  AUDIO/OTHER are filtered out client-side.
     *
     *  Album assets go through search/metadata (not `GET /api/albums/{id}`) because
     *  Immich v3 dropped the inline `assets` array from the album-details response;
     *  the `albumIds` search filter works on both v2 and v3. */
    suspend fun listCatalog(albumId: String? = null): List<ImmichCatalogEntry> =
        paginate(albumId)
            .filter { it.type == IMMICH_IMAGE || it.type == IMMICH_VIDEO }
            .map { it.toCatalogEntry() }

    private suspend fun paginate(albumId: String? = null): List<ImmichAsset> {
        val out = mutableListOf<ImmichAsset>()
        var page: String? = "1"
        var pagesFetched = 0
        while (page != null) {
            val response = fetchPageWithRetry(page, albumId) ?: break
            out += response.assets.items
            pagesFetched++
            // Guard against a misbehaving server that returns a non-null nextPage
            // forever (1000 assets/page × 200 pages = 200k assets, well above any
            // realistic Immich library we expect to slideshow).
            if (pagesFetched >= MAX_PAGES) {
                log.w("paginate hit MAX_PAGES cap", null, "pages" to pagesFetched)
                break
            }
            page = response.assets.nextPage
        }
        return out
    }

    private suspend fun fetchPageWithRetry(
        page: String,
        albumId: String?,
    ): ImmichSearchPage? {
        var lastError: Exception? = null
        repeat(CATALOG_PAGE_ATTEMPTS) { attempt ->
            try {
                return fetchSearchPage(page, albumId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < CATALOG_PAGE_ATTEMPTS - 1) {
                    sleep(CATALOG_PAGE_RETRY_DELAY_MS)
                }
            }
        }
        log.w("catalog page failed after retries", lastError, "page" to page)
        return null
    }

    private suspend fun fetchSearchPage(
        page: String,
        albumId: String?,
    ): ImmichSearchPage {
        val response: HttpResponse =
            httpClient.post {
                url("$baseUrl/api/search/metadata")
                header("x-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("page", page.toInt())
                        put("size", 1000)
                        put("withExif", true)
                        if (albumId != null) {
                            putJsonArray("albumIds") { add(albumId) }
                        }
                    },
                )
            }
        if (response.status.value !in 200..299) {
            throw IllegalStateException("search/metadata returned ${response.status.value}")
        }
        return response.body()
    }

    suspend fun getAsset(id: String): ImmichAsset =
        authGet("/api/assets/$id").body()

    suspend fun listAlbums(): List<ImmichAlbum> =
        authGet("/api/albums").body<List<ImmichAlbum>>()

    suspend fun getAlbumByName(name: String): ImmichAlbum? =
        listAlbums().find { it.albumName.equals(name, ignoreCase = true) }

    /** Builds the thumbnail URL. Auth is supplied via the `x-api-key` header at
     *  request time by the Coil OkHttp interceptor (see SuperdashApp), not via a
     *  query parameter. The Immich `?key=` query parameter only authenticates
     *  shared-link tokens, not API keys. */
    fun getThumbnailUrl(assetId: String): String = "$baseUrl/api/assets/$assetId/thumbnail?size=preview"

    fun getVideoPlaybackUrl(assetId: String): String = "$baseUrl/api/assets/$assetId/video/playback"

    fun authHeaders(): Map<String, String> = mapOf("x-api-key" to apiKey)

    /** Reachability + auth probe. Three steps:
     *
     *  1. Unauthenticated `/api/server/ping` confirms the server is reachable.
     *  2. Authenticated `/api/albums` confirms the API key has the album.read
     *     scope (slideshow needs this to find the configured album by name).
     *  3. Authenticated thumbnail HEAD on the first asset of the first album
     *     confirms the API key has the asset.view scope (without it, Coil
     *     hits 403 on every thumbnail and the slideshow shows black).
     *
     *  Each step's failure produces a distinct result so the Settings test
     *  button can guide the user to the exact fix (rotate URL, regenerate
     *  key, or add missing scope). */
    suspend fun probe(): ImmichProbeResult {
        val pingOk =
            runCatching {
                httpClient.get { url("$baseUrl/api/server/ping") }.body<ImmichPingResponse>().res == "pong"
            }.getOrElse { error ->
                return ImmichProbeResult.Unreachable(error.message ?: error::class.simpleName ?: "unknown")
            }
        if (!pingOk) {
            return ImmichProbeResult.Unreachable("server did not respond with pong")
        }
        val albumsResponse =
            runCatching { authGet("/api/albums") }
                .getOrElse { error ->
                    return ImmichProbeResult.Unreachable(error.message ?: "albums probe failed")
                }
        if (albumsResponse.status.value !in 200..299) {
            return ImmichProbeResult.MissingScope("album.read", albumsResponse.status.value)
        }
        return ImmichProbeResult.Authenticated
    }

    /** Probes whether the API key has `asset.view` by HEAD-ing one asset's
     *  thumbnail. Returns null if the album has no assets. Resolves the first
     *  asset via the same albumIds-filtered search the catalog uses, so it stays
     *  correct on Immich v3 (which no longer inlines assets in album details). */
    suspend fun canViewAsset(albumId: String): Boolean? {
        val firstAsset =
            runCatching { fetchSearchPage(page = "1", albumId = albumId).assets.items.firstOrNull()?.id }
                .getOrNull()
                ?: return null
        return runCatching { authHead("/api/assets/$firstAsset/thumbnail?size=preview").status.value in 200..299 }
            .getOrElse { false }
    }

    private suspend fun authGet(path: String): HttpResponse =
        httpClient.get {
            url("$baseUrl$path")
            header("x-api-key", apiKey)
        }

    private suspend fun authHead(path: String): HttpResponse =
        httpClient.head {
            url("$baseUrl$path")
            header("x-api-key", apiKey)
        }

    private companion object {
        const val IMMICH_IMAGE = "IMAGE"
        const val IMMICH_VIDEO = "VIDEO"
        const val CATALOG_PAGE_ATTEMPTS = 3
        const val CATALOG_PAGE_RETRY_DELAY_MS = 1_000L

        // Hard cap on pagination loops. At 1000 assets/page this admits 200k assets,
        // far beyond a realistic slideshow library. Guards against a misbehaving server
        // returning a non-null nextPage forever.
        const val MAX_PAGES = 200
    }
}

sealed interface ImmichProbeResult {
    data object Authenticated : ImmichProbeResult

    data class MissingScope(
        val scope: String,
        val statusCode: Int,
    ) : ImmichProbeResult

    data class Unreachable(
        val reason: String,
    ) : ImmichProbeResult
}
