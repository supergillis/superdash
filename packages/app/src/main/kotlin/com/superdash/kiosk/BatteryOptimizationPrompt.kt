package com.superdash.kiosk

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService
import com.superdash.core.log.Log
import kotlinx.coroutines.flow.first

private val log = Log("BatteryOpt")

object BatteryOptimizationPrompt {
    /** Fires once per install if not already whitelisted, then sets the sticky DataStore
     *  flag so we never prompt again automatically. User can re-trigger from Settings. */
    suspend fun maybePromptOnce(activity: Activity, settings: KioskSettings) {
        val powerManager = activity.getSystemService<PowerManager>() ?: return
        if (powerManager.isIgnoringBatteryOptimizations(activity.packageName)) {
            return
        }
        if (settings.batteryOptPromptShown.first()) {
            return
        }
        log.i("prompting user to whitelist superdash")
        settings.setBatteryOptPromptShown(true)
        @Suppress("BatteryLife")
        val intent =
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${activity.packageName}"),
            )
        activity.startActivity(intent)
    }

    fun openSettingsForUser(activity: Activity) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        activity.startActivity(intent)
    }
}
