package com.superdash.immich.okhttp

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmichAuthInterceptorTest {
    @Test
    fun `same origin receives auth header`() {
        val origin = ImmichServerOrigin(scheme = "https", host = "photos.local", port = 2283)

        assertTrue(shouldAuthenticateImmichRequest(origin, "https://photos.local:2283/api/assets/a".toHttpUrl()))
    }

    @Test
    fun `same host on different port does not receive auth header`() {
        val origin = ImmichServerOrigin(scheme = "https", host = "photos.local", port = 2283)

        assertFalse(shouldAuthenticateImmichRequest(origin, "https://photos.local:8123/api/assets/a".toHttpUrl()))
    }

    @Test
    fun `same host on different scheme does not receive auth header`() {
        val origin = ImmichServerOrigin(scheme = "https", host = "photos.local", port = 443)

        assertFalse(shouldAuthenticateImmichRequest(origin, "http://photos.local/api/assets/a".toHttpUrl()))
    }
}
