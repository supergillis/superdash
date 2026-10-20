package com.superdash.kiosk.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class KioskShellAccessTest {
    @Test fun `serves the shell only to the main frame at the exact HA origin`() {
        data class Case(
            val name: String,
            val isMainFrame: Boolean,
            val requestOrigin: WebOrigin,
            val path: String,
            val expected: Boolean,
        )
        val haOrigin = webOrigin("https", "ha.local", -1)
        val cases =
            listOf(
                Case("main frame same origin", true, webOrigin("https", "ha.local", -1), SHELL_PATH, true),
                Case("explicit default port", true, webOrigin("HTTPS", "HA.local", 443), SHELL_PATH, true),
                Case("subframe", false, webOrigin("https", "ha.local", -1), SHELL_PATH, false),
                Case("other port", true, webOrigin("https", "ha.local", 8123), SHELL_PATH, false),
                Case("http instead of https", true, webOrigin("http", "ha.local", -1), SHELL_PATH, false),
                Case("other host", true, webOrigin("https", "evil.local", -1), SHELL_PATH, false),
                Case("other path", true, webOrigin("https", "ha.local", -1), "/lovelace", false),
            )
        cases.forEach { case ->
            assertEquals(
                case.name,
                case.expected,
                KioskShellAccess.shouldServeShell(case.isMainFrame, case.requestOrigin, haOrigin, case.path),
            )
        }
    }
}
