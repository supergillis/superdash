package com.superdash.kiosk

import kotlinx.coroutines.flow.Flow

/**
 * Typed settings view owned by the kiosk feature.
 *
 * Lives in the feature package so the feature never imports the persistence
 * layer. The `app` module provides the implementation.
 *
 * `dashboardPath` is technically HA-related but is conceptually a kiosk
 * display preference (which dashboard sub-path to render). Only the WebView
 * reads it, so it lives here next to its only consumer.
 */
interface KioskSettings {
    val keepScreenOn: Flow<Boolean>

    val startOnBoot: Flow<Boolean>

    val launchOnWake: Flow<Boolean>

    val batteryOptPromptShown: Flow<Boolean>

    val esphomeEnabled: Flow<Boolean>

    val dashboardPath: Flow<String>

    suspend fun setKeepScreenOn(value: Boolean)

    suspend fun setStartOnBoot(value: Boolean)

    suspend fun setLaunchOnWake(value: Boolean)

    suspend fun setBatteryOptPromptShown(value: Boolean)

    suspend fun setEsphomeEnabled(value: Boolean)

    suspend fun setDashboardPath(value: String)
}
