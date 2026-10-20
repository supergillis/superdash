package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import java.net.URI
import java.net.URLEncoder
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

object HaOAuthFlow {
    data class CallbackParams(
        val code: String,
        val state: String?,
    )

    fun authorizeUrl(haBaseUrl: String, state: String? = null): URI {
        val clientId = "$haBaseUrl/"
        val redirectUri = "$haBaseUrl/?auth_callback=1"
        val parts =
            buildList {
                add("client_id=" + URLEncoder.encode(clientId, "UTF-8"))
                add("redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8"))
                if (state != null) {
                    add("state=" + URLEncoder.encode(state, "UTF-8"))
                }
            }
        return URI("$haBaseUrl/auth/authorize?" + parts.joinToString("&"))
    }

    /** Returns the auth code + state if [url] is our callback for [haBaseUrl], else null. */
    fun isCallback(url: URI, haBaseUrl: String): CallbackParams? {
        val base = URI(haBaseUrl)
        if (!url.scheme.equals(base.scheme, ignoreCase = true)) {
            return null
        }
        // RFC 3986: host is case-insensitive. Avoid dropping mixed-case HA callbacks.
        if (url.host == null || !url.host.equals(base.host, ignoreCase = true)) {
            return null
        }
        if (effectivePort(url) != effectivePort(base)) {
            return null
        }
        val rawQuery = url.rawQuery ?: return null
        val params = parseQuery(rawQuery)
        if (params["auth_callback"] != "1") {
            return null
        }
        val code = params["code"] ?: return null
        return CallbackParams(code = code, state = params["state"])
    }

    suspend fun exchangeCode(
        client: HttpClient,
        haBaseUrl: String,
        code: String,
        clock: Clock = Clock.System,
    ): HaTokens {
        val response =
            postTokenRequest(
                client,
                haBaseUrl,
                mapOf("grant_type" to "authorization_code", "code" to code),
            )
        if (!response.status.isSuccess()) {
            throw response.toTokenExchangeException()
        }
        val tokenResponse: HaTokenResponse = response.body()
        return HaTokens(
            accessToken = tokenResponse.accessToken,
            refreshToken = tokenResponse.refreshToken ?: error("HA token exchange returned no refresh_token"),
            expiresAtEpochMs = (clock.now() + tokenResponse.expiresIn.seconds).toEpochMilliseconds(),
        )
    }

    private fun effectivePort(uri: URI): Int =
        when {
            uri.port != -1 -> uri.port
            uri.scheme.equals("https", ignoreCase = true) -> 443
            else -> 80
        }

    private fun parseQuery(query: String): Map<String, String> =
        query.split("&").associate { pair ->
            val parts = pair.split("=", limit = 2)
            val key = parts[0]
            val value = parts.getOrNull(1) ?: ""
            key to java.net.URLDecoder.decode(value, "UTF-8")
        }
}
