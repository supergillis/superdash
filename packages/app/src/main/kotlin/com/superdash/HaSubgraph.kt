package com.superdash

import android.app.Application
import com.superdash.core.json.coreJson
import com.superdash.ha.HaAssistClient
import com.superdash.ha.HaMediaSourceClient
import com.superdash.ha.HaServiceCallClient
import com.superdash.ha.HaTokenProvider
import com.superdash.ha.HaTokenStore
import com.superdash.ha.HaWebSocketClient
import com.superdash.ha.media.CameraStreamSource
import com.superdash.settings.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HaSubgraph(
    private val application: Application,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) {
    val httpClient: HttpClient =
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(coreJson) }
            install(WebSockets)
        }

    val tokenStore: HaTokenStore = HaTokenStore(application)

    val haUrlFlow: StateFlow<String?> =
        settings.haUrl.stateIn(scope, SharingStarted.Eagerly, null)

    val tokenProvider: HaTokenProvider =
        HaTokenProvider(
            store = tokenStore,
            httpClient = httpClient,
            baseUrl = { haUrlFlow.value ?: error("HA URL not set when token refresh attempted") },
        )

    val client: HaWebSocketClient =
        HaWebSocketClient(
            haUrl = haUrlFlow,
            tokens = tokenProvider,
            httpClient = httpClient,
        )

    val assistClient: HaAssistClient = HaAssistClient(client)

    val cameraStreamSource: CameraStreamSource =
        CameraStreamSource(call = { type, params -> client.callResult(type, params) })

    val mediaSource: HaMediaSourceClient =
        HaMediaSourceClient.fromHaWebSocketClient(client)

    val serviceCalls: HaServiceCallClient =
        HaServiceCallClient.fromHaWebSocketClient(client)
}
