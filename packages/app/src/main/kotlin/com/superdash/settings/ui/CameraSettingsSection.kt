package com.superdash.settings.ui

import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.superdash.R
import com.superdash.settings.CameraSettingsActions
import com.superdash.settings.CameraSettingsState
import com.superdash.settings.SettingsChoice
import com.superdash.settings.SettingsChoiceRow

@Composable
fun CameraSettingsSection(
    state: CameraSettingsState,
    actions: CameraSettingsActions,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_camera_enabled_title)) },
        supportingContent = { Text(stringResource(R.string.settings_camera_enabled_summary)) },
        trailingContent = {
            Switch(
                checked = state.enabled,
                onCheckedChange = { wanted ->
                    if (wanted) {
                        actions.onRequestCameraEnable()
                    } else {
                        actions.onCameraDisable()
                    }
                },
            )
        },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_camera_allow_remote_enable_title)) },
        supportingContent = { Text(stringResource(R.string.settings_camera_allow_remote_enable_summary)) },
        trailingContent = {
            Switch(
                checked = state.allowRemoteEnable,
                onCheckedChange = actions.onAllowRemoteEnableChange,
            )
        },
    )
    if (state.enabled) {
        SettingsChoiceRow(
            label = stringResource(R.string.settings_camera_facing_label),
            choices =
                listOf(
                    SettingsChoice("front", stringResource(R.string.settings_camera_facing_front)),
                    SettingsChoice("back", stringResource(R.string.settings_camera_facing_back)),
                ),
            selectedValue = state.facing,
            fallback = state.facing,
            onSelect = actions.onFacingChange,
        )
        SettingsChoiceRow(
            label = stringResource(R.string.settings_camera_resolution_label),
            choices =
                listOf(
                    SettingsChoice("640x480", "640×480"),
                    SettingsChoice("1280x720", "1280×720"),
                    SettingsChoice("1920x1080", "1920×1080"),
                ),
            selectedValue = state.resolution,
            fallback = state.resolution,
            onSelect = actions.onResolutionChange,
        )
        SettingsChoiceRow(
            label = stringResource(R.string.settings_camera_motion_mode_label),
            choices =
                listOf(
                    SettingsChoice("off", stringResource(R.string.settings_camera_motion_mode_off)),
                    SettingsChoice("motion", stringResource(R.string.settings_camera_motion_mode_motion)),
                    SettingsChoice("person", stringResource(R.string.settings_camera_motion_mode_person)),
                ),
            selectedValue = state.motionMode,
            fallback = state.motionMode,
            onSelect = actions.onMotionModeChange,
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_camera_sensitivity_label)) },
            supportingContent = {
                Slider(
                    value = state.motionSensitivity.toFloat(),
                    onValueChange = { value -> actions.onMotionSensitivityChange(value.toInt()) },
                    valueRange = 0f..100f,
                )
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_camera_wake_on_motion_title)) },
            supportingContent = { Text(stringResource(R.string.settings_camera_wake_on_motion_summary)) },
            trailingContent = {
                Switch(
                    checked = state.wakeOnMotion,
                    onCheckedChange = actions.onWakeOnMotionChange,
                )
            },
        )
    }
}
