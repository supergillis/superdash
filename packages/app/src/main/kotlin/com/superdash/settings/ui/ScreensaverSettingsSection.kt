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
import androidx.compose.ui.unit.dp
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
    val dayModeChoices =
        listOf(
            ScreensaverMode.Off to "Off",
            ScreensaverMode.Photos to "Picsum",
            ScreensaverMode.Immich to "Immich",
            ScreensaverMode.MediaLibrary to "Media Library",
            ScreensaverMode.Clock to "Clock",
            ScreensaverMode.Black to "Black",
        ).map { (mode, label) -> SettingsChoice(mode, label) }
    val nightModeChoices =
        listOf(
            ScreensaverMode.Off to "Off",
            ScreensaverMode.Black to "Black",
            ScreensaverMode.Clock to "Clock",
            ScreensaverMode.Photos to "Picsum",
            ScreensaverMode.Immich to "Immich",
            ScreensaverMode.MediaLibrary to "Media Library",
        ).map { (mode, label) -> SettingsChoice(mode, label) }
    val overlayPositionChoices =
        listOf(
            OverlayPosition.BottomLeft to "Bottom left",
            OverlayPosition.BottomRight to "Bottom right",
            OverlayPosition.TopLeft to "Top left",
            OverlayPosition.TopRight to "Top right",
            OverlayPosition.Random to "Random",
        ).map { (position, label) -> SettingsChoice(position, label) }
    val mediaLibraryOrderChoices =
        listOf(
            SettingsChoice("shuffle", "Shuffle"),
            SettingsChoice("chronological", "Chronological"),
        )

    TextButton(
        onClick = actions.onTestScreensaver,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text("Test screensaver") }
    val showSharedSettings = shouldShowScreensaverSharedSettings(state.dayMode, state.nightMode)
    val showMediaLibrarySettings = shouldShowScreensaverMediaLibrarySettings(state.dayMode, state.nightMode)
    val showImmichSettings = shouldShowScreensaverImmichSettings(state.dayMode, state.nightMode)

    SettingsChoiceRow(
        label = "Day mode",
        choices = dayModeChoices,
        selectedValue = state.dayMode,
        fallback = state.dayMode.key,
        onSelect = { value -> actions.onDayScreensaverModeChange(value.key) },
    )
    SettingsChoiceRow(
        label = "Night mode",
        choices = nightModeChoices,
        selectedValue = state.nightMode,
        fallback = state.nightMode.key,
        onSelect = { value -> actions.onNightScreensaverModeChange(value.key) },
    )
    Text(
        if (state.idleTimeoutSec == 0) {
            "Idle timeout: Off"
        } else {
            "Idle timeout: ${state.idleTimeoutSec / 60} min ${state.idleTimeoutSec % 60}s"
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
        SettingsValueRow(
            label = "Weather entity",
            value = state.weatherEntityId.takeIf { it.isNotBlank() } ?: "Not set",
            onClick = { editingWeatherEntity = true },
        )
        if (editingWeatherEntity) {
            HaEntityPickerDialog(
                title = "Weather entity",
                entities = haEntities,
                selectedEntityId = state.weatherEntityId,
                allowedDomains = setOf("weather"),
                manualLabel = "Weather entity",
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
        SettingsValueRow(
            label = "Calendar entity",
            value = state.calendarEntityId.takeIf { it.isNotBlank() } ?: "Not set",
            onClick = { editingCalendarEntity = true },
        )
        if (editingCalendarEntity) {
            HaEntityPickerDialog(
                title = "Calendar entity",
                entities = haEntities,
                selectedEntityId = state.calendarEntityId,
                allowedDomains = setOf("calendar"),
                manualLabel = "Calendar entity",
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
        SettingsValueRow(
            label = "Power usage entity",
            value = state.powerUsageEntityId.takeIf { it.isNotBlank() } ?: "Not set",
            onClick = { editingPowerUsageEntity = true },
        )
        if (editingPowerUsageEntity) {
            HaEntityPickerDialog(
                title = "Power usage entity",
                entities = haEntities,
                selectedEntityId = state.powerUsageEntityId,
                allowedDomains = setOf("sensor"),
                manualLabel = "Power usage entity",
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
        SettingsValueRow(
            label = "Solar power entity",
            value = state.solarPowerEntityId.takeIf { it.isNotBlank() } ?: "Not set",
            onClick = { editingSolarPowerEntity = true },
        )
        if (editingSolarPowerEntity) {
            HaEntityPickerDialog(
                title = "Solar power entity",
                entities = haEntities,
                selectedEntityId = state.solarPowerEntityId,
                allowedDomains = setOf("sensor"),
                manualLabel = "Solar power entity",
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
            label = "Grid power entity (+ import / − export)",
            value = state.gridPowerEntityId.takeIf { it.isNotBlank() } ?: "Not set",
            onClick = { editingGridPowerEntity = true },
        )
        if (editingGridPowerEntity) {
            HaEntityPickerDialog(
                title = "Grid power entity",
                entities = haEntities,
                selectedEntityId = state.gridPowerEntityId,
                allowedDomains = setOf("sensor"),
                manualLabel = "Grid power entity",
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
            label = "Overlay position",
            choices = overlayPositionChoices,
            selectedValue = state.overlayPosition,
            fallback = state.overlayPosition.key,
            onSelect = { value -> actions.onOverlayPositionChange(value.key) },
        )
        Text(
            "Picture spacing: ${state.pictureSpacingDp} dp",
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
                    ?: "Not set"
            ListItem(
                headlineContent = { Text("Source") },
                supportingContent = { Text(sourceValue) },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.mediaLibrarySourceId != null) {
                            TextButton(onClick = { actions.onMediaLibrarySourceChange(null, null) }) {
                                Text("Clear")
                            }
                        }
                        TextButton(onClick = { pickerOpen = true }) {
                            Text(if (state.mediaLibrarySourceId == null) "Pick" else "Change")
                        }
                    }
                },
            )
            SettingsChoiceRow(
                label = "Display order",
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
                label = "Immich photos",
                supportingText = "Server, API key, album, and catalog refresh",
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
