package com.superdash.ha

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class HaTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
) {
    val expiresAt: Instant get() = Instant.fromEpochMilliseconds(expiresAtEpochMs)
}

object NotAuthenticatedException : Exception("superdash is not authenticated to HA")
