package com.superdash.ha

import android.net.Uri
import com.superdash.core.log.Log
import java.net.URI

private val log = Log("HaOAuth")

/** Inspects WebView navigations for the HA OAuth callback URL.
 *  When found, calls onAuthCode and asks the WebViewClient to swallow the navigation. */
class HaOAuthInterceptor(
    private val haBaseUrl: () -> String?,
    private val onAuthCode: (code: String) -> Unit,
) {
    private var pendingState: String? = null
    private val secureRandom = java.security.SecureRandom()

    /** Returns true iff this URL is our auth callback and was handled. */
    fun handleNavigation(url: Uri): Boolean {
        val javaUri = runCatching { URI(url.toString()) }.getOrNull() ?: return false
        return handleParsed(javaUri)
    }

    fun buildAuthorizeUrl(): URI {
        val base = haBaseUrl() ?: error("haBaseUrl not set")
        val state = generateState()
        pendingState = state
        return HaOAuthFlow.authorizeUrl(base, state = state)
    }

    private fun generateState(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        java.security.MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    /** Test seam: same logic as [handleNavigation] but accepts a [URI] so unit
     *  tests can avoid depending on [android.net.Uri]. */
    internal fun handleNavigationForTest(javaUri: URI): Boolean = handleParsed(javaUri)

    /** Test seam: prime the expected state without going through
     *  [buildAuthorizeUrl] (which depends on [android.util.Base64]). */
    internal fun primePendingStateForTest(state: String) {
        pendingState = state
    }

    private fun handleParsed(javaUri: URI): Boolean {
        val base = haBaseUrl() ?: return false
        val callback = HaOAuthFlow.isCallback(javaUri, base) ?: return false
        val expected = pendingState
        // Fail closed when we have no expected state. A null expected means
        // either no authorize URL was built by this interceptor, or a previous
        // handleNavigation already consumed it. In both cases accepting any
        // state would let a replayed or unsolicited callback through.
        if (expected == null) {
            log.w("OAuth callback with no pending state; rejecting", null)
            return true
        }
        if (callback.state == null || !constantTimeEquals(expected, callback.state)) {
            // Keep pendingState so a legitimate retry (e.g. transient WebView
            // double-dispatch with the correct state) can still complete.
            log.w(
                "OAuth state mismatch; rejecting callback",
                null,
                "expected" to expected,
                "got" to (callback.state ?: "<null>"),
            )
            return true
        }
        pendingState = null
        log.i("intercepted callback", "codeLength" to callback.code.length)
        onAuthCode(callback.code)
        return true
    }
}
