package com.superdash.feed

// TODO: should we put suspend getStreamUrl() in this class?

/** Where a feed's live stream comes from at playback time.
 *
 *  [FeedConfig.cameraEntity] is stored as a single free-form string so old
 *  configs don't need migrating, but the kiosk dispatches on the parsed shape:
 *  HA entity ids go through HA's `camera/stream` WS round-trip; raw URLs
 *  (e.g. a go2rtc fragmented-MP4 or RTSP endpoint) skip HA entirely. */
sealed interface CameraSource {
    data class HaEntity(
        val entityId: String,
    ) : CameraSource

    data class DirectUrl(
        val url: String,
    ) : CameraSource
}

// TODO: can we put this in object CameraSource?
private val DIRECT_URL_SCHEMES = listOf("http://", "https://", "rtsp://", "rtsps://")

fun parseCameraSource(value: String): CameraSource =
    if (DIRECT_URL_SCHEMES.any { value.startsWith(it, ignoreCase = true) }) {
        CameraSource.DirectUrl(value)
    } else {
        CameraSource.HaEntity(value)
    }
