package com.superdash.kiosk.boot

import android.content.Intent
import com.superdash.settings.SettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BootStartupHandlerTest {
    @Test
    fun `launches when decision allows`() =
        runTest {
            var launchCount = 0
            val handler =
                BootStartupHandler(
                    loadSnapshot = { snapshot(startOnBoot = true) },
                    launch = { launchCount++ },
                )

            handler.handle(Intent.ACTION_BOOT_COMPLETED)

            assertEquals(1, launchCount)
        }

    @Test
    fun `skips when decision denies`() =
        runTest {
            var launchCount = 0
            val handler =
                BootStartupHandler(
                    loadSnapshot = { snapshot(startOnBoot = false) },
                    launch = { launchCount++ },
                )

            handler.handle(Intent.ACTION_BOOT_COMPLETED)

            assertEquals(0, launchCount)
        }

    private fun snapshot(
        startOnBoot: Boolean,
        launchOnWake: Boolean = false,
    ): SettingsRepository.Snapshot =
        SettingsRepository.Snapshot(
            haUrl = "http://homeassistant.local:8123",
            keepScreenOn = true,
            startOnBoot = startOnBoot,
            launchOnWake = launchOnWake,
        )
}
