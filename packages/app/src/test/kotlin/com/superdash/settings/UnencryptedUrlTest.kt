package com.superdash.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class UnencryptedUrlTest {
    @Test fun `only http urls are reported as unencrypted`() {
        val expectedByUrl =
            mapOf(
                "http://ha.example.com" to true,
                "http://ha.example.com:8123/lovelace" to true,
                "  http://ha.example.com  " to true,
                "HTTP://ha.example.com" to true,
                "Http://ha.example.com" to true,
                "https://ha.example.com" to false,
                "HTTPS://ha.example.com" to false,
                "ha.example.com" to false,
                "ftp://ha.example.com" to false,
                "http:/ha.example.com" to false,
                "xhttp://ha.example.com" to false,
                "not a url" to false,
                "" to false,
                "   " to false,
                null to false,
            )

        val actualByUrl = expectedByUrl.mapValues { (url, _) -> isUnencryptedUrl(url) }

        assertEquals(expectedByUrl, actualByUrl)
    }
}
