package com.superdash.ha

import com.superdash.core.log.Log
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val log = Log("HaOAuthCodeExchange")

/** Exchanges an OAuth authorization code for tokens and persists them.
 *  Returns false on any failure so the caller can start a new authorize
 *  flow. A used code is never retried. */
suspend fun exchangeAndSaveAuthCode(
    httpClient: HttpClient,
    tokenStore: HaTokenStoreLike,
    haUrl: String,
    code: String,
): Boolean {
    try {
        val tokens =
            withContext(Dispatchers.IO) {
                HaOAuthFlow.exchangeCode(httpClient, haUrl, code)
            }
        tokenStore.save(tokens)
        log.i("OAuth code exchange succeeded")
        return true
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        log.e("OAuth code exchange failed", t)
        return false
    }
}
