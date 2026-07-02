package com.superdash.kiosk.boot

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.superdash.MainActivity
import com.superdash.core.log.Log
import com.superdash.settings.SettingsRepository

private val log = Log("BootLauncher")

object BootDecision {
    private val LAUNCH_ACTIONS =
        setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )

    fun shouldLaunch(action: String?, snapshot: SettingsRepository.Snapshot): Boolean {
        if (action == null) {
            return false
        }
        if (action == Intent.ACTION_USER_PRESENT) {
            return snapshot.launchOnWake
        }
        if (action in LAUNCH_ACTIONS) {
            return snapshot.startOnBoot
        }
        return false
    }
}

object BootLauncher {
    /** Acquires a 10s WakeLock and launches MainActivity. WakeLock is timed (auto-releases),
     *  so no Handler.postDelayed cleanup needed. */
    fun launch(context: Context) {
        val powerManager = context.getSystemService<PowerManager>() ?: return
        val mask =
            PowerManager.PARTIAL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE
        val wakeLock = powerManager.newWakeLock(mask, "superdash:BootWakeLock")
        try {
            wakeLock.acquire(10_000L)
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ).putExtra(EXTRA_LAUNCHED_FROM_BOOT, true),
            )
            log.i("started MainActivity")
        } catch (t: Throwable) {
            log.e("launch failed", t)
        }
        // wakeLock auto-releases after 10s timeout.
    }

    const val EXTRA_LAUNCHED_FROM_BOOT = "launched_from_boot"
}
