package com.superdash.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI

class HaOAuthFlowTest {
    @Test
    fun `authorizeUrl includes the provided state`() {
        val url = HaOAuthFlow.authorizeUrl("https://ha.local", state = "abc123")
        assertEquals("abc123", queryParam(url, "state"))
    }

    @Test
    fun `authorizeUrl url-encodes state`() {
        val url = HaOAuthFlow.authorizeUrl("https://ha.local", state = "a/b c")
        assertEquals("a/b c", queryParam(url, "state"))
    }

    @Test
    fun `isCallback returns code and state when both match`() {
        val callback = URI("https://ha.local/?auth_callback=1&code=AUTHCODE&state=NONCE")
        val result = HaOAuthFlow.isCallback(callback, "https://ha.local")
        assertEquals("AUTHCODE", result?.code)
        assertEquals("NONCE", result?.state)
    }

    @Test
    fun `isCallback returns null when auth_callback missing`() {
        val callback = URI("https://ha.local/?code=X&state=Y")
        assertNull(HaOAuthFlow.isCallback(callback, "https://ha.local"))
    }

    @Test
    fun `isCallback state is null when not present in callback`() {
        val callback = URI("https://ha.local/?auth_callback=1&code=X")
        val result = HaOAuthFlow.isCallback(callback, "https://ha.local")
        assertEquals("X", result?.code)
        assertNull(result?.state)
    }

    @Test
    fun `isCallback returns null for different host`() {
        val callback = URI("https://other.local/?auth_callback=1&code=abc123")
        assertNull(HaOAuthFlow.isCallback(callback, "https://ha.local"))
    }

    @Test
    fun `isCallback matches host case-insensitively`() {
        // RFC 3986: host is case-insensitive. HA may surface mixed-case hosts.
        val callback = URI("https://HA.Local/?auth_callback=1&code=abc123")
        val result = HaOAuthFlow.isCallback(callback, "https://ha.local")
        assertEquals("abc123", result?.code)
    }

    @Test
    fun `isCallback returns null for different port`() {
        val callback = URI("https://ha.local:9999/?auth_callback=1&code=abc123")
        assertNull(HaOAuthFlow.isCallback(callback, "https://ha.local"))
    }

    @Test
    fun `isCallback returns null when no code param`() {
        val callback = URI("https://ha.local/?auth_callback=1")
        assertNull(HaOAuthFlow.isCallback(callback, "https://ha.local"))
    }

    private fun queryParam(uri: URI, key: String): String? =
        uri.rawQuery
            ?.split("&")
            ?.map { it.split("=", limit = 2) }
            ?.firstOrNull { it[0] == key }
            ?.let { java.net.URLDecoder.decode(it[1], "UTF-8") }
}
