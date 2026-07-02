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
        val resp: HaTokenResponse =
            httpClient
                .submitForm(
                    url = "${baseUrl()}/auth/token",
                    formParameters =
                        parameters {
                            append("grant_type", "refresh_token")
                            append("refresh_token", cur.refreshToken)
                            append("client_id", "${baseUrl()}/")
                        },
                ).body()
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
