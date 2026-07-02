package com.superdash.settings

import com.superdash.core.persistence.KeyValueStore
import com.superdash.core.persistence.Setting
import com.superdash.core.persistence.observe
import com.superdash.core.persistence.write
import com.superdash.kiosk.KioskSettings
import kotlinx.coroutines.flow.Flow

/**
 * App-owned [KioskSettings] backed by [KeyValueStore].
 *
 * Reuses the legacy DataStore keys and defaults. `dashboardPath` applies the
 * same trim/strip-slash transform on both read and write that
 * [SettingsRepository] used historically.
 */
internal class SettingsRepositoryKioskSettings(
    private val store: KeyValueStore,
) : KioskSettings {
    override val keepScreenOn: Flow<Boolean> = store.observe(KEEP_SCREEN_ON)

    override val startOnBoot: Flow<Boolean> = store.observe(START_ON_BOOT)

    override val launchOnWake: Flow<Boolean> = store.observe(LAUNCH_ON_WAKE)

    override val batteryOptPromptShown: Flow<Boolean> = store.observe(BATTERY_OPT_PROMPT_SHOWN)

    override val esphomeEnabled: Flow<Boolean> = store.observe(ESPHOME_ENABLED)

    override val dashboardPath: Flow<String> = store.observe(DASHBOARD_PATH)

    override suspend fun setKeepScreenOn(value: Boolean) = store.write(KEEP_SCREEN_ON, value)

    override suspend fun setStartOnBoot(value: Boolean) = store.write(START_ON_BOOT, value)

    override suspend fun setLaunchOnWake(value: Boolean) = store.write(LAUNCH_ON_WAKE, value)

    override suspend fun setBatteryOptPromptShown(value: Boolean) = store.write(BATTERY_OPT_PROMPT_SHOWN, value)

    override suspend fun setEsphomeEnabled(value: Boolean) = store.write(ESPHOME_ENABLED, value)

    override suspend fun setDashboardPath(value: String) = store.write(DASHBOARD_PATH, value)

    private companion object {
        val KEEP_SCREEN_ON = Setting(key = "keep_screen_on", default = true)
        val START_ON_BOOT = Setting(key = "start_on_boot", default = true)
        val LAUNCH_ON_WAKE = Setting(key = "launch_on_wake", default = false)
        val BATTERY_OPT_PROMPT_SHOWN = Setting(key = "battery_opt_prompt_shown", default = false)
        val ESPHOME_ENABLED = Setting(key = "esphome_enabled", default = false)
        val DASHBOARD_PATH =
            Setting(
                key = "dashboard_path",
                default = "",
                read = { it.trim().trim('/') },
                write = { it.trim().trim('/') },
            )
    }
}
