package com.superdash.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class HaOAuthInterceptorTest {
    @Test
    fun `replayed callback after success is rejected, not silently accepted`() {
        // A second handleNavigation call (browser retry, WebView double-fire) must
        // not be accepted as a stateless callback just because the first call
        // already consumed the expected state.
        val codes = mutableListOf<String>()
        val interceptor =
            HaOAuthInterceptor(
                haBaseUrl = { "https://ha.local" },
                onAuthCode = { code -> codes.add(code) },
            )
        interceptor.primePendingStateForTest("NONCE")

        val firstUrl = URI("https://ha.local/?auth_callback=1&code=CODE1&state=NONCE")
        val secondUrl = URI("https://ha.local/?auth_callback=1&code=CODE2&state=ATTACKER")

        val firstResult = interceptor.handleNavigationForTest(firstUrl)
        val secondResult = interceptor.handleNavigationForTest(secondUrl)

        assertTrue(firstResult)
        assertTrue(secondResult)
        // The second callback must be rejected (no onAuthCode for CODE2).
        assertEquals(listOf("CODE1"), codes)
    }

    @Test
    fun `callback without prior authorize is rejected`() {
        // Defense in depth: if no authorize URL was built by this interceptor,
        // we have no expected state to compare and must fail closed.
        val codes = mutableListOf<String>()
        val interceptor =
            HaOAuthInterceptor(
                haBaseUrl = { "https://ha.local" },
                onAuthCode = { code -> codes.add(code) },
            )

        val url = URI("https://ha.local/?auth_callback=1&code=CODE&state=WHATEVER")
        val handled = interceptor.handleNavigationForTest(url)

        assertTrue(handled)
        assertFalse(codes.contains("CODE"))
        assertEquals(emptyList<String>(), codes)
    }
}
