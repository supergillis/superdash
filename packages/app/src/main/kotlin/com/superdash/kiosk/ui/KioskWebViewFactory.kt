package com.superdash.kiosk.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.superdash.BuildConfig
import com.superdash.core.log.Log
import com.superdash.ha.HaOAuthInterceptor
import com.superdash.ha.HaTokens
import com.superdash.kiosk.PinPath
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URI

private val log = Log("KioskWebViewFactory")

/** Path of the kiosk shell asset, served at the HA origin via
 *  [WebViewClient.shouldInterceptRequest]. Must be a path that HA itself
 *  won't claim. We use a leading-`_` segment to make collision unlikely. */
internal const val SHELL_PATH = "/_superdash/kiosk-shell.html"

/** Placeholder substituted in the shell asset with the actual HA URL the
 *  iframe should load. */
private const val SHELL_HA_URL_PLACEHOLDER = "__SUPERDASH_HA_URL__"

/** Top-level URL of the kiosk shell. Same origin as HA so the iframe
 *  inside the shell can talk to HA via same-origin DOM access. */
internal fun shellUrl(haUrl: String): String = haUrl.trimEnd('/') + SHELL_PATH

/** `<haUrl>` for empty pin, otherwise `<haUrl>/<pin>` (no double-slash). */
internal fun pinnedUrl(haUrl: String, pin: String): String =
    if (pin.isEmpty()) {
        haUrl
    } else {
        haUrl.trimEnd('/') + "/" + pin
    }

internal fun haOriginOf(haUrl: String): String? =
    runCatching {
        val uri = URI(haUrl)
        val portPart: String =
            if (uri.port == -1) {
                ""
            } else {
                ":" + uri.port
            }
        uri.scheme + "://" + uri.host + portPart
    }.getOrNull()

internal object KioskAuthNavigation {
    fun shouldSnapMainFrameToShell(
        urlHost: String?,
        urlPath: String?,
        haOriginHost: String?,
        shellPath: String,
    ): Boolean {
        if (urlHost != haOriginHost) {
            return false
        }
        if (urlPath == shellPath) {
            return false
        }
        if (urlPath?.startsWith("/auth/") == true) {
            return false
        }
        return true
    }
}

internal fun buildTokenInjectionScript(haUrl: String, tokens: HaTokens): String {
    val expiresInSec: Long = ((tokens.expiresAtEpochMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    val payload: String =
        JSONObject()
            .apply {
                put("access_token", tokens.accessToken)
                put("refresh_token", tokens.refreshToken)
                put("expires_in", expiresInSec)
                put("hassUrl", haUrl)
                put("clientId", "$haUrl/")
                put("expires", tokens.expiresAtEpochMs)
            }.toString()
    return """
        (function() {
            try {
                localStorage.setItem('hassTokens', JSON.stringify($payload));
            } catch (e) {}
        })();
        """.trimIndent()
}

/** Exposes `window.__superdashPinnedDashboard` for kiosk.js's History-API trap.
 *  Must run before kiosk.js. Caller guarantees [pin] is non-empty. */
private fun buildPinScript(pin: String): String {
    val pinJson: String =
        JSONObject()
            .put("pin", pin)
            .toString()
    return """
        (function() {
            try {
                var cfg = $pinJson;
                window.__superdashPinnedDashboard = cfg.pin;
            } catch (e) {}
        })();
        """.trimIndent()
}

/** Reads the kiosk shell asset, substitutes the iframe target URL, and
 *  returns it as an HTML response. Returns null if the asset is missing,
 *  in which case normal handling proceeds and HA returns its own 404. */
private fun buildShellResponse(ctx: Context, iframeUrl: String): WebResourceResponse? {
    val template: String =
        runCatching {
            ctx.assets.open("kiosk-shell.html").use { it.readBytes().decodeToString() }
        }.getOrElse {
            log.w("kiosk-shell.html asset missing", it)
            return null
        }
    val html: String = template.replace(SHELL_HA_URL_PLACEHOLDER, iframeUrl)
    val bytes: ByteArray = html.toByteArray(Charsets.UTF_8)
    return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(bytes))
}

@SuppressLint("SetJavaScriptEnabled")
internal fun buildKioskWebView(
    ctx: Context,
    shellUrl: String,
    iframeUrl: String,
    pin: String,
    onRendererGone: () -> Unit,
    oauthInterceptor: HaOAuthInterceptor,
    onPageFinished: (WebView, String) -> Unit,
): WebView =
    WebView(ctx).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(settings, false)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val origin = haOriginOf(shellUrl) ?: "*"
            if (pin.isNotEmpty()) {
                WebViewCompat.addDocumentStartJavaScript(
                    this,
                    buildPinScript(pin),
                    setOf(origin),
                )
            }
            WebViewCompat.addDocumentStartJavaScript(this, KioskWebViewScripts.kioskJs(ctx), setOf(origin))
        } else {
            log.w("DOCUMENT_START_SCRIPT not supported on this WebView; kiosk CSS not injected")
        }

        val haOriginHost: String? = runCatching { URI(shellUrl) }.getOrNull()?.host
        webViewClient =
            object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                    val url = req.url
                    if (url.host == haOriginHost && url.path == SHELL_PATH) {
                        return buildShellResponse(ctx, iframeUrl)
                    }
                    return null
                }

                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                    if (oauthInterceptor.handleNavigation(req.url)) {
                        return true
                    }
                    if (req.url.host != haOriginHost) {
                        if (req.hasGesture()) {
                            view.context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, req.url),
                            )
                        }
                        return true
                    }
                    // Subframe (HA iframe) navigation: only enforce the pin
                    // for full-page navs that escape the dashboard subtree.
                    // SPA history-API navs inside HA are caught by kiosk.js.
                    if (!req.isForMainFrame) {
                        if (pin.isNotEmpty() && !PinPath.isInside(req.url.path, pin) && req.url.path != SHELL_PATH) {
                            // Reload the shell so the iframe re-targets the
                            // pinned dashboard URL. post() defers past the
                            // in-flight nav cancellation.
                            view.post { view.loadUrl(shellUrl) }
                            return true
                        }
                        return false
                    }
                    // Main-frame nav. The shell is the only top-level URL we
                    // ever load. Anything else is a full-page redirect trying
                    // to escape, so snap back. post() defers past the
                    // in-flight nav cancellation.
                    if (
                        KioskAuthNavigation.shouldSnapMainFrameToShell(
                            urlHost = req.url.host,
                            urlPath = req.url.path,
                            haOriginHost = haOriginHost,
                            shellPath = SHELL_PATH,
                        )
                    ) {
                        view.post { view.loadUrl(shellUrl) }
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    onPageFinished(view, url)
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    log.w("WebView renderer gone", null, "didCrash" to detail.didCrash())
                    onRendererGone()
                    return true
                }
            }
        webChromeClient =
            object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    val resources = request.resources.joinToString(",")
                    if (request.origin.host != haOriginHost) {
                        log.w(
                            "WebView permission deny (origin mismatch)",
                            null,
                            "origin" to request.origin,
                            "expected" to haOriginHost,
                            "resources" to resources,
                        )
                        request.deny()
                        return
                    }
                    val allowed =
                        request.resources
                            .filter { resource ->
                                resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                                    resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                            }.toTypedArray()
                    if (allowed.isEmpty()) {
                        log.w(
                            "WebView permission deny (no allowlisted resources)",
                            null,
                            "origin" to request.origin,
                            "resources" to resources,
                        )
                        request.deny()
                        return
                    }
                    log.i(
                        "WebView permission grant",
                        "origin" to request.origin,
                        "granted" to allowed.joinToString(","),
                        "requested" to resources,
                    )
                    request.grant(allowed)
                }

                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    when (message.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR ->
                            log.w(
                                "WebView console",
                                null,
                                "message" to message.message(),
                                "sourceId" to message.sourceId(),
                                "lineNumber" to message.lineNumber(),
                            )
                        ConsoleMessage.MessageLevel.WARNING ->
                            log.w(
                                "WebView console",
                                null,
                                "message" to message.message(),
                                "sourceId" to message.sourceId(),
                                "lineNumber" to message.lineNumber(),
                            )
                        else ->
                            log.i(
                                "WebView console",
                                "message" to message.message(),
                                "sourceId" to message.sourceId(),
                                "lineNumber" to message.lineNumber(),
                            )
                    }
                    return true
                }
            }
        loadUrl(shellUrl)
    }
