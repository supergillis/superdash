package com.superdash.doorbell

import com.superdash.core.log.Log
import kotlinx.coroutines.CancellationException

private val log = Log("DoorbellStreamResolver")

/** Resolves a doorbell's playable stream URL plus bearer token (if any).
 *
 *  Direct URLs are handed back as-is; HA entity ids round-trip through
 *  [fetchHlsUrl] and the returned HA-relative path is concatenated with
 *  [haBaseUrl]. The bearer token is fetched only on the HA path.
 *
 *  Maps thrown exceptions to [DoorbellStreamState.Failed] so callers can
 *  render an error state directly. [CancellationException] is re-thrown
 *  so cooperative cancellation (e.g. a `LaunchedEffect` re-keying) stops
 *  cleanly instead of being converted into a Failed state. */
suspend fun resolveDoorbellStream(
    config: DoorbellConfig,
    haBaseUrl: String,
    fetchHlsUrl: suspend (cameraEntity: String) -> String,
    bearerTokenProvider: suspend () -> String?,
): DoorbellStreamState =
    try {
        when (val source = parseCameraSource(config.cameraEntity)) {
            is CameraSource.DirectUrl -> {
                DoorbellStreamState.Ready(
                    streamUrl = source.url,
                    bearerToken = null,
                )
            }
            is CameraSource.HaEntity -> {
                val token = bearerTokenProvider()
                val relative = fetchHlsUrl(source.entityId)
                DoorbellStreamState.Ready(
                    streamUrl = haBaseUrl.trimEnd('/') + relative,
                    bearerToken = token,
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        log.w("stream resolve failed", e, "camera" to config.cameraEntity)
        DoorbellStreamState.Failed(e.message)
    }
