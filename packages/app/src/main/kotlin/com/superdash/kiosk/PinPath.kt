package com.superdash.kiosk

import java.net.URLDecoder

/** Matches `/<pin>` or `/<pin>/<view>`, the HA view-tab URL shape. Deeper paths
 *  are rejected. Used by [com.superdash.kiosk.ui.KioskWebView]'s navigation guard. */
object PinPath {
    fun isInside(path: String?, pin: String): Boolean {
        if (path == null) {
            return false
        }
        // Decode percent-escapes so `%2F` (encoded `/`) and `%2E%2E` (`..`) cannot
        // slip past the `tail.contains('/')` and depth checks. Reject malformed
        // sequences outright rather than silently treating them as literal.
        val decoded =
            runCatching { URLDecoder.decode(path, "UTF-8") }.getOrNull() ?: return false
        // After decoding, normalize any backslashes (Windows-style separators that
        // some HA proxies emit) to forward slashes before the depth check.
        val normalized = decoded.replace('\\', '/')
        val prefix = "/$pin"
        if (normalized == prefix) {
            return true
        }
        if (!normalized.startsWith("$prefix/")) {
            return false
        }
        val tail: String = normalized.substring(prefix.length + 1)
        if (tail.contains('/')) {
            return false
        }
        // Reject any component that resolves to a parent reference even after
        // decoding. `..` itself is caught by the slash check (`/../` contains `/`),
        // but a bare `..` as the last segment would slip through depth-1.
        if (tail == ".." || tail == ".") {
            return false
        }
        return true
    }
}
