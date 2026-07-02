package com.superdash.kiosk

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface SidebarSettings {
    val position: Flow<SidebarPosition>

    val pinned: Flow<Boolean>

    val showLabels: Flow<Boolean>

    val shortcuts: Flow<List<SidebarShortcut>>

    suspend fun setPosition(value: SidebarPosition)

    suspend fun setPinned(value: Boolean)

    suspend fun setShowLabels(value: Boolean)

    suspend fun setShortcuts(value: List<SidebarShortcut>)
}

@Serializable
enum class SidebarPosition(
    val key: String,
    val label: String,
) {
    @SerialName("left")
    Left("left", "Left"),

    @SerialName("right")
    Right("right", "Right"),

    @SerialName("top")
    Top("top", "Top"),

    @SerialName("bottom")
    Bottom("bottom", "Bottom"),
    ;

    companion object {
        fun fromKey(value: String): SidebarPosition =
            entries.firstOrNull { position -> position.key == value } ?: Left
    }
}

@Serializable
data class SidebarShortcut(
    val id: String,
    val title: String,
    val icon: String,
    val shortLabel: String? = null,
    val shortLabelCustomized: Boolean = false,
    val action: SidebarAction,
)

@Serializable
sealed interface SidebarAction {
    @Serializable
    @SerialName("open_settings")
    data object OpenSettings : SidebarAction

    @Serializable
    @SerialName("reload_dashboard")
    data object ReloadDashboard : SidebarAction

    @Serializable
    @SerialName("show_screensaver")
    data object ShowScreensaver : SidebarAction

    @Serializable
    @SerialName("dismiss_screensaver")
    data object DismissScreensaver : SidebarAction

    @Serializable
    @SerialName("set_night_mode_active")
    data class SetNightModeActive(
        val active: Boolean,
    ) : SidebarAction

    @Serializable
    @SerialName("open_dashboard_path")
    data class OpenDashboardPath(
        val path: String,
    ) : SidebarAction
}

internal val SidebarAction.defaultShortLabel: String
    get() =
        when (this) {
            SidebarAction.OpenSettings -> "Settings"
            SidebarAction.ReloadDashboard -> "Reload"
            SidebarAction.ShowScreensaver -> "Sleep"
            SidebarAction.DismissScreensaver -> "Wake"
            is SidebarAction.SetNightModeActive ->
                if (active) {
                    "Night"
                } else {
                    "Day"
                }
            is SidebarAction.OpenDashboardPath -> "View"
        }

internal fun SidebarAction.emitsUserTouchedFromSidebar(): Boolean =
    when (this) {
        SidebarAction.ShowScreensaver,
        SidebarAction.DismissScreensaver,
        -> false
        SidebarAction.OpenSettings,
        SidebarAction.ReloadDashboard,
        is SidebarAction.SetNightModeActive,
        is SidebarAction.OpenDashboardPath,
        -> true
    }

internal fun SidebarAction.touchesIdleFromSidebar(): Boolean = this is SidebarAction.DismissScreensaver

internal fun SidebarShortcut.selectedInSidebar(nightModeActive: Boolean): Boolean =
    when (val shortcutAction = action) {
        is SidebarAction.SetNightModeActive -> shortcutAction.active == nightModeActive
        else -> false
    }

object SidebarSettingsDefaults {
    val position: SidebarPosition = SidebarPosition.Left

    val pinned: Boolean = false

    val showLabels: Boolean = false

    val settingsShortcut =
        SidebarShortcut(
            id = "settings",
            title = "Settings",
            icon = "settings",
            action = SidebarAction.OpenSettings,
            shortLabel = SidebarAction.OpenSettings.defaultShortLabel,
        )

    val shortcuts: List<SidebarShortcut> =
        listOf(
            settingsShortcut,
            SidebarShortcut(
                id = "show-screensaver",
                title = "Show screensaver",
                icon = "screensaver",
                action = SidebarAction.ShowScreensaver,
                shortLabel = SidebarAction.ShowScreensaver.defaultShortLabel,
            ),
            SidebarShortcut(
                id = "night-mode-on",
                title = "Night mode on",
                icon = "moon",
                action = SidebarAction.SetNightModeActive(active = true),
                shortLabel = SidebarAction.SetNightModeActive(active = true).defaultShortLabel,
            ),
            SidebarShortcut(
                id = "night-mode-off",
                title = "Night mode off",
                icon = "sun",
                action = SidebarAction.SetNightModeActive(active = false),
                shortLabel = SidebarAction.SetNightModeActive(active = false).defaultShortLabel,
            ),
            SidebarShortcut(
                id = "reload-dashboard",
                title = "Reload dashboard",
                icon = "refresh",
                action = SidebarAction.ReloadDashboard,
                shortLabel = SidebarAction.ReloadDashboard.defaultShortLabel,
            ),
        )
}
