package com.superdash.screensaver

import kotlinx.coroutines.flow.Flow

/**
 * Typed settings view owned by the screensaver feature.
 *
 * Lives in the feature package so the feature never imports the persistence
 * layer. The `app` module provides the implementation.
 */
interface ScreensaverSettings {
    val dayMode: Flow<String>

    val nightMode: Flow<String>

    val nightModeActive: Flow<Boolean>

    val idleTimeoutSec: Flow<Int>

    val overlayPosition: Flow<String>

    val pictureSpacingDp: Flow<Int>

    val mediaLibraryOrder: Flow<String>

    val mediaLibrarySourceId: Flow<String?>

    val mediaLibrarySourceTitle: Flow<String?>

    val weatherEntityId: Flow<String>

    val calendarEntityId: Flow<String>

    val powerUsageEntityId: Flow<String>

    val solarPowerEntityId: Flow<String>

    val gridPowerEntityId: Flow<String>

    suspend fun setDayMode(value: String)

    suspend fun setNightMode(value: String)

    suspend fun setNightModeActive(value: Boolean)

    suspend fun setIdleTimeoutSec(value: Int)

    suspend fun setOverlayPosition(value: String)

    suspend fun setPictureSpacingDp(value: Int)

    suspend fun setMediaLibraryOrder(value: String)

    suspend fun setMediaLibrarySource(id: String?, title: String?)

    suspend fun setWeatherEntityId(value: String)

    suspend fun setCalendarEntityId(value: String)

    suspend fun setPowerUsageEntityId(value: String)

    suspend fun setSolarPowerEntityId(value: String)

    suspend fun setGridPowerEntityId(value: String)
}
