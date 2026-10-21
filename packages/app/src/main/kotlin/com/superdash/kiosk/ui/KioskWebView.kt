package com.superdash.kiosk.ui

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import com.superdash.core.log.Log
import com.superdash.ha.HaOAuthInterceptor
import com.superdash.ha.HaTokens
import java.util.concurrent.atomic.AtomicReference

private val log = Log("KioskWebView")

@Composable
fun KioskWebView(
    haUrl: String,
    dashboardPath: String,
    tokens: HaTokens?,
    oauthInterceptor: HaOAuthInterceptor,
    onWebViewCreated: (WebView) -> Unit,
    onWebViewReleased: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    // dashboardPath is pre-normalized by SettingsRepository.dashboardPath.
    val pin: String = dashboardPath
    val shellUrl: String = shellUrl(haUrl)
    val iframeUrl: String = pinnedUrl(haUrl, pin)
    var rendererKey by remember { mutableIntStateOf(0) }
    // Outside the key() so it survives pin changes. Pin changes rebuild the
    // WebView but auth hasn't changed, so it's not a fresh-login transition.
    // Seeded with the first composition's tokens so cold start with stored
    // tokens does not count as a login transition. The factory already loads the shell.
    val previousTokensRef = remember { mutableStateOf(tokens) }
    // Read by shouldInterceptRequest on a background thread when serving the shell.
    val tokensRef = remember { AtomicReference(tokens) }
    SideEffect { tokensRef.set(tokens) }
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
                    haUrl = haUrl,
                    pin = pin,
                    tokensProvider = { tokensRef.get() },
                    onRendererGone = { rendererKey++ },
                    oauthInterceptor = oauthInterceptor,
                ).also {
                    webViewRef.value = it
                    onWebViewCreated(it)
                }
            },
            onRelease = { webView ->
                onWebViewReleased(webView)
                webView.destroy()
            },
        )
        // Mid-session token refreshes must not navigate; only a real login does.
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
            if (documentStartScriptSupported()) {
                haOriginOf(haUrl)?.let { origin ->
                    val script: String = buildTokenInjectionScript(haUrl, tokens)
                    tokenScriptRef.value = WebViewCompat.addDocumentStartJavaScript(webView, script, setOf(origin))
                }
            }
            if (wasNull) {
                webView.loadUrl(shellUrl)
            }
        }
    }
}
