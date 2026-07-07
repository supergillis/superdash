package com.superdash.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.superdash.R
import com.superdash.ha.EntityState
import com.superdash.screensaver.ScreensaverMode
import com.superdash.screensaver.overlay.OverlayPosition
import com.superdash.settings.ScreensaverSettingsActions
import com.superdash.settings.ScreensaverSettingsState
import com.superdash.settings.SettingsChoice
import com.superdash.settings.SettingsChoiceRow

@Composable
fun ScreensaverSettingsSection(
    state: ScreensaverSettingsState,
    haEntities: List<EntityState>,
    actions: ScreensaverSettingsActions,
    mediaSourcePicker: @Composable (
        onConfirm: (id: String, title: String) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
    onOpenImmichSettings: () -> Unit = {},
) {
    var editingWeatherEntity by remember { mutableStateOf(false) }
    var editingCalendarEntity by remember { mutableStateOf(false) }
    var editingPowerUsageEntity by remember { mutableStateOf(false) }
    var editingSolarPowerEntity by remember { mutableStateOf(false) }
    var editingGridPowerEntity by remember { mutableStateOf(false) }
    val offLabel = stringResource(R.string.settings_value_off)
    val picsumLabel = stringResource(R.string.settings_screensaver_mode_picsum)
    val immichLabel = stringResource(R.string.settings_screensaver_mode_immich)
    val mediaLibraryLabel = stringResource(R.string.settings_screensaver_mode_media_library)
    val clockLabel = stringResource(R.string.settings_screensaver_mode_clock)
    val blackLabel = stringResource(R.string.settings_screensaver_mode_black)
    val notSet = stringResource(R.string.settings_value_not_set)
    val dayModeChoices =
        listOf(
            ScreensaverMode.Off to offLabel,
            ScreensaverMode.Photos to picsumLabel,
            ScreensaverMode.Immich to immichLabel,
            ScreensaverMode.MediaLibrary to mediaLibraryLabel,
            ScreensaverMode.Clock to clockLabel,
            ScreensaverMode.Black to blackLabel,
        ).map { (mode, label) -> SettingsChoice(mode, label) }
    val nightModeChoices =
        listOf(
            ScreensaverMode.Off to offLabel,
            ScreensaverMode.Black to blackLabel,
            ScreensaverMode.Clock to clockLabel,
            ScreensaverMode.Photos to picsumLabel,
            ScreensaverMode.Immich to immichLabel,
            ScreensaverMode.MediaLibrary to mediaLibraryLabel,
        ).map { (mode, label) -> SettingsChoice(mode, label) }
    val overlayPositionChoices =
        listOf(
            OverlayPosition.BottomLeft to stringResource(R.string.settings_screensaver_overlay_bottom_left),
            OverlayPosition.BottomRight to stringResource(R.string.settings_screensaver_overlay_bottom_right),
            OverlayPosition.TopLeft to stringResource(R.string.settings_screensaver_overlay_top_left),
            OverlayPosition.TopRight to stringResource(R.string.settings_screensaver_overlay_top_right),
            OverlayPosition.Random to stringResource(R.string.settings_screensaver_overlay_random),
        ).map { (position, label) -> SettingsChoice(position, label) }
    val mediaLibraryOrderChoices =
        listOf(
            SettingsChoice("shuffle", stringResource(R.string.settings_screensaver_order_shuffle)),
            SettingsChoice("chronological", stringResource(R.string.settings_screensaver_order_chronological)),
        )

    TextButton(
        onClick = actions.onTestScreensaver,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text(stringResource(R.string.settings_screensaver_test_button)) }
    val showSharedSettings = shouldShowScreensaverSharedSettings(state.dayMode, state.nightMode)
    val showMediaLibrarySettings = shouldShowScreensaverMediaLibrarySettings(state.dayMode, state.nightMode)
    val showImmichSettings = shouldShowScreensaverImmichSettings(state.dayMode, state.nightMode)

    SettingsChoiceRow(
        label = stringResource(R.string.settings_screensaver_day_mode_label),
        choices = dayModeChoices,
        selectedValue = state.dayMode,
        fallback = state.dayMode.key,
        onSelect = { value -> actions.onDayScreensaverModeChange(value.key) },
    )
    SettingsChoiceRow(
        label = stringResource(R.string.settings_screensaver_night_mode_label),
        choices = nightModeChoices,
        selectedValue = state.nightMode,
        fallback = state.nightMode.key,
        onSelect = { value -> actions.onNightScreensaverModeChange(value.key) },
    )
    Text(
        if (state.idleTimeoutSec == 0) {
            stringResource(R.string.settings_screensaver_idle_timeout_off)
        } else {
            stringResource(
                R.string.settings_screensaver_idle_timeout,
                state.idleTimeoutSec / 60,
                state.idleTimeoutSec % 60,
            )
        },
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Slider(
        value = state.idleTimeoutSec.toFloat(),
        onValueChange = { value ->
            val snapped = (value / 30).toInt() * 30
            actions.onIdleTimeoutSecChange(snapped)
        },
        valueRange = 0f..1800f,
        steps = 59,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    if (showSharedSettings) {
        val weatherEntityLabel = stringResource(R.string.settings_screensaver_weather_entity)
        SettingsValueRow(
            label = weatherEntityLabel,
            value = state.weatherEntityId.takeIf { it.isNotBlank() } ?: notSet,
            onClick = { editingWeatherEntity = true },
        )
        if (editingWeatherEntity) {
            HaEntityPickerDialog(
                title = weatherEntityLabel,
                entities = haEntities,
                selectedEntityId = state.weatherEntityId,
                allowedDomains = setOf("weather"),
                manualLabel = weatherEntityLabel,
                onDismiss = { editingWeatherEntity = false },
                onSelectManual = { value ->
                    actions.onWeatherEntityIdChange(value)
                    editingWeatherEntity = false
                },
                onSelect = { value ->
                    actions.onWeatherEntityIdChange(value)
                    editingWeatherEntity = false
                },
            )
        }
        val calendarEntityLabel = stringResource(R.string.settings_screensaver_calendar_entity)
        SettingsValueRow(
            label = calendarEntityLabel,
            value = state.calendarEntityId.takeIf { it.isNotBlank() } ?: notSet,
            onClick = { editingCalendarEntity = true },
        )
        if (editingCalendarEntity) {
            HaEntityPickerDialog(
                title = calendarEntityLabel,
                entities = haEntities,
                selectedEntityId = state.calendarEntityId,
                allowedDomains = setOf("calendar"),
                manualLabel = calendarEntityLabel,
                onDismiss = { editingCalendarEntity = false },
                onSelectManual = { value ->
                    actions.onCalendarEntityIdChange(value)
                    editingCalendarEntity = false
                },
                onSelect = { value ->
                    actions.onCalendarEntityIdChange(value)
                    editingCalendarEntity = false
                },
            )
        }
        val powerUsageEntityLabel = stringResource(R.string.settings_screensaver_power_usage_entity)
        SettingsValueRow(
            label = powerUsageEntityLabel,
            value = state.powerUsageEntityId.takeIf { it.isNotBlank() } ?: notSet,
            onClick = { editingPowerUsageEntity = true },
        )
        if (editingPowerUsageEntity) {
            HaEntityPickerDialog(
                title = powerUsageEntityLabel,
                entities = haEntities,
                selectedEntityId = state.powerUsageEntityId,
                allowedDomains = setOf("sensor"),
                manualLabel = powerUsageEntityLabel,
                onDismiss = { editingPowerUsageEntity = false },
                onSelectManual = { value ->
                    actions.onPowerUsageEntityIdChange(value)
                    editingPowerUsageEntity = false
                },
                onSelect = { value ->
                    actions.onPowerUsageEntityIdChange(value)
                    editingPowerUsageEntity = false
                },
            )
        }
        val solarPowerEntityLabel = stringResource(R.string.settings_screensaver_solar_power_entity)
        SettingsValueRow(
            label = solarPowerEntityLabel,
            value = state.solarPowerEntityId.takeIf { it.isNotBlank() } ?: notSet,
            onClick = { editingSolarPowerEntity = true },
        )
        if (editingSolarPowerEntity) {
            HaEntityPickerDialog(
                title = solarPowerEntityLabel,
                entities = haEntities,
                selectedEntityId = state.solarPowerEntityId,
                allowedDomains = setOf("sensor"),
                manualLabel = solarPowerEntityLabel,
                onDismiss = { editingSolarPowerEntity = false },
                onSelectManual = { value ->
                    actions.onSolarPowerEntityIdChange(value)
                    editingSolarPowerEntity = false
                },
                onSelect = { value ->
                    actions.onSolarPowerEntityIdChange(value)
                    editingSolarPowerEntity = false
                },
            )
        }
        SettingsValueRow(
            label = stringResource(R.string.settings_screensaver_grid_power_entity_label),
            value = state.gridPowerEntityId.takeIf { it.isNotBlank() } ?: notSet,
            onClick = { editingGridPowerEntity = true },
        )
        if (editingGridPowerEntity) {
            val gridPowerEntityTitle = stringResource(R.string.settings_screensaver_grid_power_entity_title)
            HaEntityPickerDialog(
                title = gridPowerEntityTitle,
                entities = haEntities,
                selectedEntityId = state.gridPowerEntityId,
                allowedDomains = setOf("sensor"),
                manualLabel = gridPowerEntityTitle,
                onDismiss = { editingGridPowerEntity = false },
                onSelectManual = { value ->
                    actions.onGridPowerEntityIdChange(value)
                    editingGridPowerEntity = false
                },
                onSelect = { value ->
                    actions.onGridPowerEntityIdChange(value)
                    editingGridPowerEntity = false
                },
            )
        }
        SettingsChoiceRow(
            label = stringResource(R.string.settings_screensaver_overlay_position_label),
            choices = overlayPositionChoices,
            selectedValue = state.overlayPosition,
            fallback = state.overlayPosition.key,
            onSelect = { value -> actions.onOverlayPositionChange(value.key) },
        )
        Text(
            stringResource(R.string.settings_screensaver_picture_spacing, state.pictureSpacingDp),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Slider(
            value = state.pictureSpacingDp.toFloat(),
            onValueChange = { value ->
                val snapped = (value / 4).toInt() * 4
                actions.onPictureSpacingDpChange(snapped)
            },
            valueRange = 0f..48f,
            steps = 11,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (showMediaLibrarySettings) {
            var pickerOpen by remember { mutableStateOf(false) }
            val sourceValue =
                state.mediaLibrarySourceTitle
                    ?.takeIf { title -> title.isNotBlank() }
                    ?: state.mediaLibrarySourceId
                    ?: notSet
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_screensaver_source_label)) },
                supportingContent = { Text(sourceValue) },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.mediaLibrarySourceId != null) {
                            TextButton(onClick = { actions.onMediaLibrarySourceChange(null, null) }) {
                                Text(stringResource(R.string.settings_action_clear))
                            }
                        }
                        TextButton(onClick = { pickerOpen = true }) {
                            Text(
                                if (state.mediaLibrarySourceId == null) {
                                    stringResource(R.string.settings_screensaver_source_pick)
                                } else {
                                    stringResource(R.string.settings_screensaver_source_change)
                                },
                            )
                        }
                    }
                },
            )
            SettingsChoiceRow(
                label = stringResource(R.string.settings_screensaver_display_order_label),
                choices = mediaLibraryOrderChoices,
                selectedValue = state.mediaLibraryOrderKey,
                fallback = state.mediaLibraryOrderKey,
                onSelect = actions.onMediaLibraryOrderChange,
            )
            if (pickerOpen) {
                mediaSourcePicker(
                    { id, title ->
                        actions.onMediaLibrarySourceChange(id, title)
                        pickerOpen = false
                    },
                    { pickerOpen = false },
                )
            }
        }

        if (showImmichSettings) {
            SettingsActionRow(
                label = stringResource(R.string.settings_immich_photos_title),
                supportingText = stringResource(R.string.settings_screensaver_immich_photos_summary),
                onClick = onOpenImmichSettings,
            )
        }
    }
}

internal fun shouldShowScreensaverSharedSettings(
    dayMode: ScreensaverMode,
    nightMode: ScreensaverMode,
): Boolean = dayMode != ScreensaverMode.Off || nightMode != ScreensaverMode.Off

internal fun shouldShowScreensaverMediaLibrarySettings(
    dayMode: ScreensaverMode,
    nightMode: ScreensaverMode,
): Boolean = dayMode == ScreensaverMode.MediaLibrary || nightMode == ScreensaverMode.MediaLibrary

internal fun shouldShowScreensaverImmichSettings(
    dayMode: ScreensaverMode,
    nightMode: ScreensaverMode,
): Boolean = dayMode == ScreensaverMode.Immich || nightMode == ScreensaverMode.Immich
