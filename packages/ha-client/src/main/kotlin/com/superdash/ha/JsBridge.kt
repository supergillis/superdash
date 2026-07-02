package com.superdash.ha

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebMessagePortCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.superdash.core.log.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private val log = Log("JsBridge")

/** Single-channel JS↔Kotlin bridge built on WebMessagePort. Carries a
 *  ping/pong on first connect to verify wiring, and is used for real
 *  events thereafter (wake-word events, screensaver triggers, etc.). */
class JsBridge {
    private var nativePort: WebMessagePortCompat? = null
    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incoming: SharedFlow<String> = _incoming.asSharedFlow()

    /** Call from KioskWebView's onPageFinished. Idempotent: safe to call multiple times;
     *  each call closes any prior port and installs a fresh one. */
    fun install(webView: WebView, haOrigin: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.CREATE_WEB_MESSAGE_CHANNEL)) {
            log.w("CREATE_WEB_MESSAGE_CHANNEL unsupported on this WebView; bridge disabled")
            return
        }
        nativePort?.close()
        val ports = WebViewCompat.createWebMessageChannel(webView)
        val native = ports[0]
        val web = ports[1]
        nativePort = native
        native.setWebMessageCallback(
            object : WebMessagePortCompat.WebMessageCallbackCompat() {
                override fun onMessage(port: WebMessagePortCompat, msg: WebMessageCompat?) {
                    msg?.data?.let { _incoming.tryEmit(it) }
                }
            },
        )
        WebViewCompat.postWebMessage(
            webView,
            WebMessageCompat("__superdash_init__", arrayOf(web)),
            Uri.parse(haOrigin),
        )
        log.i("installed channel", "origin" to haOrigin)
    }

    /** Send a JSON-string payload to JS. Caller is responsible for the schema. */
    fun send(payload: String) {
        val port = nativePort
        if (port == null) {
            log.w("send called before install; payload dropped")
            return
        }
        port.postMessage(WebMessageCompat(payload))
    }

    fun close() {
        nativePort?.close()
        nativePort = null
    }
}
