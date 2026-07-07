package com.superdash.immich.okhttp

import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URI

data class ImmichServerOrigin(
    val scheme: String,
    val host: String,
    val port: Int,
)

class ImmichAuthInterceptor(
    private val serverOrigin: StateFlow<ImmichServerOrigin?>,
    private val apiKey: StateFlow<String>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val configuredOrigin = serverOrigin.value
        val key = apiKey.value
        return if (
            configuredOrigin != null &&
            key.isNotBlank() &&
            shouldAuthenticateImmichRequest(configuredOrigin, request.url)
        ) {
            chain.proceed(request.newBuilder().header("x-api-key", key).build())
        } else {
            chain.proceed(request)
        }
    }
}

fun immichServerOrigin(url: String): ImmichServerOrigin? =
    runCatching {
        val uri = URI(url)
        val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val port =
            if (uri.port != -1) {
                uri.port
            } else {
                when (scheme.lowercase()) {
                    "http" -> 80
                    "https" -> 443
                    else -> return@runCatching null
                }
            }
        ImmichServerOrigin(scheme = scheme.lowercase(), host = host, port = port)
    }.getOrNull()

fun shouldAuthenticateImmichRequest(
    configuredOrigin: ImmichServerOrigin,
    requestUrl: HttpUrl,
): Boolean =
    requestUrl.scheme == configuredOrigin.scheme &&
        requestUrl.host == configuredOrigin.host &&
        requestUrl.port == configuredOrigin.port
