package com.superdash.kiosk.ui

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.superdash.core.log.Log
import com.superdash.ha.HaOAuthInterceptor
import com.superdash.ha.HaTokens
import com.superdash.ha.JsBridge

private val log = Log("KioskWebView")

@Composable
fun KioskWebView(
    haUrl: String,
    dashboardPath: String,
    tokens: HaTokens?,
    oauthInterceptor: HaOAuthInterceptor,
    bridge: JsBridge,
    modifier: Modifier = Modifier,
) {
    // dashboardPath is pre-normalized by SettingsRepository.dashboardPath.
    val pin: String = dashboardPath
    val shellUrl: String = shellUrl(haUrl)
    val iframeUrl: String = pinnedUrl(haUrl, pin)
    var rendererKey by remember { mutableIntStateOf(0) }
    // Outside the key() so it survives pin changes. Pin changes rebuild the
    // WebView but auth hasn't changed, so it's not a fresh-login transition.
    val previousTokensRef = remember { mutableStateOf<HaTokens?>(null) }
    key(rendererKey, haUrl, pin) {
        val webViewRef = remember { mutableStateOf<WebView?>(null) }
        val tokenScriptRef = remember { mutableStateOf<ScriptHandler?>(null) }
        val authLaunchAttemptedRef = remember { mutableStateOf(false) }
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                buildKioskWebView(
                    ctx = ctx,
                    shellUrl = shellUrl,
                    iframeUrl = iframeUrl,
                    pin = pin,
                    onRendererGone = { rendererKey++ },
                    oauthInterceptor = oauthInterceptor,
                    onPageFinished = { webView, finishedUrl ->
                        // Only install the bridge once the shell has finished
                        // loading. The shell relays the port into the iframe
                        // via postMessage. Installing on the iframe load
                        // instead would race with shell teardown on reloads.
                        val origin: String = haOriginOf(haUrl) ?: return@buildKioskWebView
                        if (finishedUrl.startsWith(shellUrl)) {
                            bridge.install(webView, origin)
                        }
                    },
                ).also { webViewRef.value = it }
            },
            onRelease = { webView -> webView.destroy() },
        )
        // Re-register the token document-start script on every token change
        // so future page loads see fresh tokens. Only loadUrl on the first
        // null→non-null transition. Mid-session refreshes must not navigate.
        LaunchedEffect(tokens) {
            val webView: WebView = webViewRef.value ?: return@LaunchedEffect
            val wasNull = previousTokensRef.value == null
            previousTokensRef.value = tokens
            tokenScriptRef.value?.remove()
            tokenScriptRef.value = null
            if (tokens == null) {
                if (!authLaunchAttemptedRef.value) {
                    authLaunchAttemptedRef.value = true
                    val authorizeUrl =
                        runCatching { oauthInterceptor.buildAuthorizeUrl().toString() }
                            .getOrElse { t ->
                                log.w("OAuth authorize URL unavailable", t)
                                return@LaunchedEffect
                            }
                    log.i("native HA tokens missing; launching OAuth")
                    webView.loadUrl(authorizeUrl)
                }
                return@LaunchedEffect
            }
            authLaunchAttemptedRef.value = false
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                val origin: String = haOriginOf(haUrl) ?: return@LaunchedEffect
                val script: String = buildTokenInjectionScript(haUrl, tokens)
                tokenScriptRef.value = WebViewCompat.addDocumentStartJavaScript(webView, script, setOf(origin))
                if (wasNull) {
                    webView.loadUrl(shellUrl)
                }
            }
        }
    }
}
