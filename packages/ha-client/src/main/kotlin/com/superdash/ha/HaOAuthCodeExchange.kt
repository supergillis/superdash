package com.superdash.ha

import com.superdash.core.log.Log
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val log = Log("HaOAuthCodeExchange")

/** Exchanges an OAuth authorization code for tokens and persists them.
 *  Logs success/failure; never throws. The Activity caller launches this
 *  from `lifecycleScope` and has nothing useful to do with an error beyond
 *  what we log here. */
suspend fun exchangeAndSaveAuthCode(
    httpClient: HttpClient,
    tokenStore: HaTokenStore,
    haUrl: String,
    code: String,
) {
    try {
        val tokens =
            withContext(Dispatchers.IO) {
                HaOAuthFlow.exchangeCode(httpClient, haUrl, code)
            }
        tokenStore.save(tokens)
        log.i("OAuth code exchange succeeded")
    } catch (t: Throwable) {
        log.e("OAuth code exchange failed", t)
    }
}
