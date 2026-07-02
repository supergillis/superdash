package com.superdash.settings

import com.superdash.core.persistence.KeyValueStore
import com.superdash.core.persistence.Setting
import com.superdash.core.persistence.observe
import com.superdash.core.persistence.write
import com.superdash.screensaver.ScreensaverSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App-owned [ScreensaverSettings] backed by [KeyValueStore].
 *
 * Reuses the legacy DataStore keys and defaults verbatim. `mediaLibrarySource`
 * uses the empty-string-as-null convention that [SettingsRepository] applies
 * on read.
 */
internal class SettingsRepositoryScreensaverSettings(
    private val store: KeyValueStore,
) : ScreensaverSettings {
    override val dayMode: Flow<String> = store.observe(DAY_MODE)

    override val nightMode: Flow<String> = store.observe(NIGHT_MODE)

    override val nightModeActive: Flow<Boolean> = store.observe(NIGHT_MODE_ACTIVE)

    override val idleTimeoutSec: Flow<Int> = store.observe(IDLE_TIMEOUT_SEC)

    override val overlayPosition: Flow<String> = store.observe(OVERLAY_POSITION)

    override val pictureSpacingDp: Flow<Int> = store.observe(PICTURE_SPACING_DP)

    override val mediaLibraryOrder: Flow<String> = store.observe(MEDIA_LIBRARY_ORDER)

    override val mediaLibrarySourceId: Flow<String?> =
        store.observe(ML_SOURCE_ID).map { it.takeIf { value -> value.isNotBlank() } }

    override val mediaLibrarySourceTitle: Flow<String?> =
        store.observe(ML_SOURCE_TITLE).map { it.takeIf { value -> value.isNotBlank() } }

    override val weatherEntityId: Flow<String> = store.observe(WEATHER_ENTITY_ID)

    override val calendarEntityId: Flow<String> = store.observe(CALENDAR_ENTITY_ID)

    override val powerUsageEntityId: Flow<String> = store.observe(POWER_USAGE_ENTITY_ID)

    override val solarPowerEntityId: Flow<String> = store.observe(SOLAR_POWER_ENTITY_ID)

    override val gridPowerEntityId: Flow<String> = store.observe(GRID_POWER_ENTITY_ID)

    override suspend fun setDayMode(value: String) = store.write(DAY_MODE, value)

    override suspend fun setNightMode(value: String) = store.write(NIGHT_MODE, value)

    override suspend fun setNightModeActive(value: Boolean) = store.write(NIGHT_MODE_ACTIVE, value)

    override suspend fun setIdleTimeoutSec(value: Int) = store.write(IDLE_TIMEOUT_SEC, value)

    override suspend fun setOverlayPosition(value: String) = store.write(OVERLAY_POSITION, value)

    override suspend fun setPictureSpacingDp(value: Int) = store.write(PICTURE_SPACING_DP, value)

    override suspend fun setMediaLibraryOrder(value: String) = store.write(MEDIA_LIBRARY_ORDER, value)

    override suspend fun setMediaLibrarySource(
        id: String?,
        title: String?,
    ) {
        store.write(ML_SOURCE_ID, id.orEmpty())
        store.write(ML_SOURCE_TITLE, title.orEmpty())
    }

    override suspend fun setWeatherEntityId(value: String) = store.write(WEATHER_ENTITY_ID, value)

    override suspend fun setCalendarEntityId(value: String) = store.write(CALENDAR_ENTITY_ID, value)

    override suspend fun setPowerUsageEntityId(value: String) = store.write(POWER_USAGE_ENTITY_ID, value)

    override suspend fun setSolarPowerEntityId(value: String) = store.write(SOLAR_POWER_ENTITY_ID, value)

    override suspend fun setGridPowerEntityId(value: String) = store.write(GRID_POWER_ENTITY_ID, value)

    private companion object {
        val DAY_MODE = Setting(key = "day_screensaver_mode", default = "photos")
        val NIGHT_MODE = Setting(key = "night_screensaver_mode", default = "black")
        val NIGHT_MODE_ACTIVE = Setting(key = "night_mode_active", default = false)
        val IDLE_TIMEOUT_SEC = Setting(key = "idle_timeout_sec", default = 300)
        val OVERLAY_POSITION = Setting(key = "overlay_position", default = "bottom_left")
        val PICTURE_SPACING_DP =
            Setting(key = "picture_spacing_dp", default = 8, write = { it.coerceIn(0, 48) })
        val MEDIA_LIBRARY_ORDER = Setting(key = "media_library_order", default = "shuffle")
        val ML_SOURCE_ID = Setting(key = "media_library_source_id", default = "")
        val ML_SOURCE_TITLE = Setting(key = "media_library_source_title", default = "")
        val WEATHER_ENTITY_ID = Setting(key = "weather_entity_id", default = "weather.home")
        val CALENDAR_ENTITY_ID = Setting(key = "calendar_entity_id", default = "")
        val POWER_USAGE_ENTITY_ID = Setting(key = "power_usage_entity_id", default = "")
        val SOLAR_POWER_ENTITY_ID = Setting(key = "solar_power_entity_id", default = "")
        val GRID_POWER_ENTITY_ID = Setting(key = "grid_power_entity_id", default = "")
    }
}
