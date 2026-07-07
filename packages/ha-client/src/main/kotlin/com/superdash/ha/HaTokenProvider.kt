package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
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
            httpClient.submitForm(
                url = "${baseUrl()}/auth/token",
                formParameters =
                    parameters {
                        append("grant_type", "refresh_token")
                        append("refresh_token", cur.refreshToken)
                        append("client_id", "${baseUrl()}/")
                    },
            )
        if (response.status.value in 400..499) {
            // HA rejected the refresh token itself (revoked / invalid_grant). That is
            // a genuine reauth condition, not a transient error: throw the type the
            // reconnect loop parks on so the user is prompted to re-authenticate,
            // instead of retrying a dead token forever (which can trip HA's IP ban).
            // Transient failures (network, 5xx) still propagate to the caller's backoff.
            throw NotAuthenticatedException
        }
        val resp: HaTokenResponse = response.body()
        val updated =
            HaTokens(
                accessToken = resp.accessToken,
                refreshToken = resp.refreshToken ?: cur.refreshToken,
                expiresAtEpochMs = (clock.now() + resp.expiresIn.seconds).toEpochMilliseconds(),
            )
        store.save(updated)
        return updated
    }
}
