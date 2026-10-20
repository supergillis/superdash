package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Decoupled from HaTokenStore so tests can substitute an in-memory implementation. */
interface HaTokenStoreLike {
    suspend fun load(): HaTokens?

    suspend fun save(tokens: HaTokens)

    suspend fun clear()
}

class HaTokenProvider(
    private val store: HaTokenStoreLike,
    private val httpClient: HttpClient,
    private val baseUrl: () -> String,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()
    private val skew = 60.seconds

    suspend fun get(): String =
        mutex.withLock {
            val cur = store.load() ?: throw NotAuthenticatedException
            if (clock.now() > cur.expiresAt - skew) {
                doRefresh(cur).accessToken
            } else {
                cur.accessToken
            }
        }

    suspend fun forceRefresh(): String =
        mutex.withLock {
            doRefresh(store.load() ?: throw NotAuthenticatedException).accessToken
        }

    private suspend fun doRefresh(cur: HaTokens): HaTokens {
        val response =
            postTokenRequest(
                httpClient,
                baseUrl(),
                mapOf("grant_type" to "refresh_token", "refresh_token" to cur.refreshToken),
            )
        if (response.status.value in 400..499) {
            // HA rejected the refresh token itself (revoked / invalid_grant). That is
            // a genuine reauth condition, not a transient error: throw the type the
            // reconnect loop parks on so the user is prompted to re-authenticate,
            // instead of retrying a dead token forever (which can trip HA's IP ban).
            // Transient failures (network, 5xx) still propagate to the caller's backoff.
            throw NotAuthenticatedException
        }
        if (!response.status.isSuccess()) {
            throw response.toTokenExchangeException()
        }
        val tokenResponse: HaTokenResponse = response.body()
        val updated =
            HaTokens(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken ?: cur.refreshToken,
                expiresAtEpochMs = (clock.now() + tokenResponse.expiresIn.seconds).toEpochMilliseconds(),
            )
        store.save(updated)
        return updated
    }
}
