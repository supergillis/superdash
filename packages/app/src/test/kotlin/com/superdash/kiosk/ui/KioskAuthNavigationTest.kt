package com.superdash.kiosk.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskAuthNavigationTest {
    @Test fun `allows HA auth pages to load in the main frame`() {
        assertFalse(
            KioskAuthNavigation.shouldSnapMainFrameToShell(
                urlHost = "ha.local",
                urlPath = "/auth/authorize",
                haOriginHost = "ha.local",
                shellPath = "/_superdash/kiosk-shell.html",
            ),
        )
    }

    @Test fun `snaps non shell HA main frame pages back to the shell`() {
        assertTrue(
            KioskAuthNavigation.shouldSnapMainFrameToShell(
                urlHost = "ha.local",
                urlPath = "/lovelace/default_view",
                haOriginHost = "ha.local",
                shellPath = "/_superdash/kiosk-shell.html",
            ),
        )
    }
}
