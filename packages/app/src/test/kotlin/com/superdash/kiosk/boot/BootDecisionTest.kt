package com.superdash.kiosk.boot

import android.content.Intent
import com.superdash.settings.SettingsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootDecisionTest {
    private fun snap(startOnBoot: Boolean = true, launchOnWake: Boolean = false) =
        SettingsRepository.Snapshot(
            haUrl = "http://x",
            keepScreenOn = true,
            startOnBoot = startOnBoot,
            launchOnWake = launchOnWake,
        )

    @Test fun `boot completed with start on boot on launches`() =
        assertTrue(
            BootDecision.shouldLaunch(Intent.ACTION_BOOT_COMPLETED, snap(startOnBoot = true)),
        )

    @Test fun `boot completed with start on boot off skips`() =
        assertFalse(
            BootDecision.shouldLaunch(Intent.ACTION_BOOT_COMPLETED, snap(startOnBoot = false)),
        )

    @Test fun `quickboot with start on boot launches`() =
        assertTrue(
            BootDecision.shouldLaunch("android.intent.action.QUICKBOOT_POWERON", snap(startOnBoot = true)),
        )

    @Test fun `htc quickboot with start on boot launches`() =
        assertTrue(
            BootDecision.shouldLaunch("com.htc.intent.action.QUICKBOOT_POWERON", snap(startOnBoot = true)),
        )

    @Test fun `user present with launch on wake launches`() =
        assertTrue(
            BootDecision.shouldLaunch(Intent.ACTION_USER_PRESENT, snap(launchOnWake = true)),
        )

    @Test fun `user present default skips`() =
        assertFalse(
            BootDecision.shouldLaunch(Intent.ACTION_USER_PRESENT, snap(launchOnWake = false)),
        )

    @Test fun `unknown action skips`() =
        assertFalse(
            BootDecision.shouldLaunch("com.example.RANDOM", snap()),
        )

    @Test fun `null action skips`() =
        assertFalse(
            BootDecision.shouldLaunch(null, snap()),
        )
}
