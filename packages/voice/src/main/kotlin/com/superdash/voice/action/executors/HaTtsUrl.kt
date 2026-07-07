package com.superdash.voice.action.executors

internal fun resolveHaMediaUrl(mediaUrl: String, haBaseUrl: String?): String {
    if (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://")) {
        return mediaUrl
    }
    val base = haBaseUrl?.trim()?.trimEnd('/').orEmpty()
    if (base.isBlank()) {
        return mediaUrl
    }
    val path =
        if (mediaUrl.startsWith("/")) {
            mediaUrl
        } else {
            "/$mediaUrl"
        }
    return base + path
}
