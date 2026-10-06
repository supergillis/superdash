package com.superdash.feed

import com.superdash.core.log.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

private val log = Log("FeedStreamResolver")

/** HA's `camera/stream` never answers when it cannot open the camera's stream
 *  source, so an unbounded wait leaves the overlay spinning forever. */
internal const val HLS_FETCH_TIMEOUT_MS = 15_000L

/** Resolves a feed's playable stream URL plus bearer token (if any).
 *
 *  Direct URLs are handed back as-is; HA entity ids round-trip through
 *  [fetchHlsUrl] and the returned HA-relative path is concatenated with
 *  [haBaseUrl]. The bearer token is fetched only on the HA path.
 *
 *  Maps thrown exceptions to [FeedStreamState.Failed] so callers can
 *  render an error state directly. [CancellationException] is re-thrown
 *  so cooperative cancellation (e.g. a `LaunchedEffect` re-keying) stops
 *  cleanly instead of being converted into a Failed state. */
suspend fun resolveFeedStream(
    config: FeedConfig,
    haBaseUrl: String,
    fetchHlsUrl: suspend (cameraEntity: String) -> String,
    bearerTokenProvider: suspend () -> String?,
): FeedStreamState =
    try {
        when (val source = parseCameraSource(config.cameraEntity)) {
            is CameraSource.DirectUrl -> {
                FeedStreamState.Ready(
                    streamUrl = source.url,
                    bearerToken = null,
                )
            }
            is CameraSource.HaEntity -> {
                val token = bearerTokenProvider()
                val relative = withTimeoutOrNull(HLS_FETCH_TIMEOUT_MS) { fetchHlsUrl(source.entityId) }
                if (relative == null) {
                    log.w("stream resolve timed out", null, "camera" to config.cameraEntity)
                    // A null reason shows the localized "camera unavailable" message.
                    FeedStreamState.Failed(null)
                } else {
                    FeedStreamState.Ready(
                        streamUrl = haBaseUrl.trimEnd('/') + relative,
                        bearerToken = token,
                    )
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        log.w("stream resolve failed", e, "camera" to config.cameraEntity)
        FeedStreamState.Failed(e.message)
    }
