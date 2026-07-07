package com.superdash.ha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** HA's token-endpoint response shape. */
@Serializable
data class HaTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String = "Bearer",
)
