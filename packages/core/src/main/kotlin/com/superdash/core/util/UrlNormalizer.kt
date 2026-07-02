package com.superdash.core.util

import java.net.URI

/** Normalises a user-entered URL to a canonical form: http(s) scheme prefixed,
 *  trailing slash stripped, whitespace trimmed. Returns null for unparseable / blank input.
 */
object UrlNormalizer {
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        val withScheme =
            if (trimmed.contains("://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
        val parsed = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (parsed.scheme !in setOf("http", "https")) {
            return null
        }
        if (parsed.host.isNullOrBlank()) {
            return null
        }
        val portPart =
            if (parsed.port == -1) {
                ""
            } else {
                ":${parsed.port}"
            }
        val pathPart = parsed.path.trimEnd('/')
        return "${parsed.scheme}://${parsed.host}$portPart$pathPart"
    }
}
