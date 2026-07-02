package com.superdash.settings

import androidx.compose.ui.unit.dp

object SettingsLayout {
    val wideBreakpoint = 840.dp
    val navigationPaneWidth = 280.dp
    val detailContentMaxWidth = 720.dp

    fun initialDestination(isWide: Boolean): SettingsDestination? =
        if (isWide) {
            SettingsDestination.TopLevel.HomeAssistant
        } else {
            null
        }

    fun navigateUp(destination: SettingsDestination?): SettingsDestination? =
        when (destination) {
            is SettingsDestination.Child -> {
                destination.parent
            }
            is SettingsDestination.TopLevel -> {
                null
            }
            null -> {
                null
            }
        }

    fun selectedTopLevel(destination: SettingsDestination?): SettingsDestination.TopLevel? =
        when (destination) {
            is SettingsDestination.Child -> {
                destination.parent
            }
            is SettingsDestination.TopLevel -> {
                destination
            }
            null -> {
                null
            }
        }

    fun navigationTestTag(destination: SettingsDestination.TopLevel): String =
        when (destination) {
            SettingsDestination.TopLevel.HomeAssistant -> {
                "settings_nav_home_assistant"
            }
            SettingsDestination.TopLevel.Kiosk -> {
                "settings_nav_kiosk"
            }
            SettingsDestination.TopLevel.Sidebar -> {
                "settings_nav_sidebar"
            }
            SettingsDestination.TopLevel.Voice -> {
                "settings_nav_voice"
            }
            SettingsDestination.TopLevel.Screensaver -> {
                "settings_nav_screensaver"
            }
            SettingsDestination.TopLevel.Doorbell -> {
                "settings_nav_doorbell"
            }
            SettingsDestination.TopLevel.Esphome -> {
                "settings_nav_esphome"
            }
            SettingsDestination.TopLevel.Admin -> {
                "settings_nav_admin"
            }
        }
}
