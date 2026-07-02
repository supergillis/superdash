package com.superdash.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlNormalizerTest {
    @Test fun `strips trailing slash and trims whitespace`() {
        assertEquals("http://homeassistant.local:8123", UrlNormalizer.normalize("homeassistant.local:8123"))
        assertEquals("http://homeassistant.local:8123", UrlNormalizer.normalize("homeassistant.local:8123/"))
        assertEquals("https://my.home.example", UrlNormalizer.normalize("https://my.home.example"))
        assertEquals("https://my.home.example", UrlNormalizer.normalize(" https://my.home.example  "))
        assertEquals("http://192.168.1.10:8123", UrlNormalizer.normalize("http://192.168.1.10:8123/"))
        assertEquals("http://192.168.1.10:8123", UrlNormalizer.normalize("  http://192.168.1.10:8123 "))
        assertEquals("https://example.com/lovelace", UrlNormalizer.normalize("https://example.com/lovelace/"))
        assertEquals("http://nas.local", UrlNormalizer.normalize("nas.local"))
    }

    @Test fun `empty input returns null`() = assertNull(UrlNormalizer.normalize(""))

    @Test fun `blank input returns null`() = assertNull(UrlNormalizer.normalize("   "))

    @Test fun `garbage that cant be parsed returns null`() = assertNull(UrlNormalizer.normalize("ht!tp://broken"))
}
