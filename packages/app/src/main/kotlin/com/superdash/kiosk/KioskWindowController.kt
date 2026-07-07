package com.superdash.kiosk

import android.app.Activity
import android.app.KeyguardManager
import android.view.WindowManager
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.superdash.core.log.Log
import com.superdash.settings.SettingsRepository

private val log = Log("KioskWindow")

class KioskWindowController(
    private val activity: Activity,
    private val settings: SettingsRepository,
) {
    fun apply(launchedFromBoot: Boolean, snapshot: SettingsRepository.Snapshot) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        if (snapshot.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        if (snapshot.startOnBoot) {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            val keyguardManager = activity.getSystemService<KeyguardManager>()
            if (launchedFromBoot &&
                keyguardManager != null &&
                !keyguardManager.isKeyguardSecure &&
                keyguardManager.isKeyguardLocked
            ) {
                keyguardManager.requestDismissKeyguard(activity, null)
                log.i("requested keyguard dismiss (not secure, locked, launched from boot)")
            }
        }
    }
}
