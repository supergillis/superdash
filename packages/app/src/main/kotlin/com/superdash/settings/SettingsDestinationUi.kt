package com.superdash.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.graphics.vector.ImageVector

fun SettingsDestination.TopLevel.icon(): ImageVector =
    when (this) {
        SettingsDestination.TopLevel.General -> {
            Icons.Filled.Language
        }
        SettingsDestination.TopLevel.HomeAssistant -> {
            Icons.Filled.Home
        }
        SettingsDestination.TopLevel.Kiosk -> {
            Icons.Filled.Devices
        }
        SettingsDestination.TopLevel.Sidebar -> {
            Icons.AutoMirrored.Filled.ViewSidebar
        }
        SettingsDestination.TopLevel.Voice -> {
            Icons.Filled.Mic
        }
        SettingsDestination.TopLevel.Screensaver -> {
            Icons.Filled.Image
        }
        SettingsDestination.TopLevel.Doorbell -> {
            Icons.Filled.Notifications
        }
        SettingsDestination.TopLevel.Esphome -> {
            Icons.Filled.Memory
        }
        SettingsDestination.TopLevel.Admin -> {
            Icons.Filled.BugReport
        }
    }

@StringRes
fun SettingsDestination.summaryRes(): Int? =
    when (this) {
        is SettingsDestination.TopLevel -> {
            summaryRes
        }
        is SettingsDestination.Child -> {
            null
        }
    }
