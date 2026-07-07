package com.superdash.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
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
import com.superdash.kiosk.SidebarAction
import com.superdash.kiosk.SidebarPosition
import com.superdash.kiosk.SidebarShortcut
import com.superdash.kiosk.defaultShortLabel
import com.superdash.settings.SettingsChoice
import com.superdash.settings.SettingsChoiceRow
import com.superdash.settings.SidebarSettingsActions
import com.superdash.settings.SidebarSettingsState

@Composable
fun SidebarSettingsSection(
    state: SidebarSettingsState,
    actions: SidebarSettingsActions,
) {
    var editingShortcut by remember { mutableStateOf<SidebarShortcut?>(null) }
    var addingShortcut by remember { mutableStateOf(false) }
    val positionChoices =
        SidebarPosition.entries.map { position ->
            SettingsChoice(position, stringResource(position.labelRes))
        }

    SettingsChoiceRow(
        label = stringResource(R.string.settings_sidebar_position_label),
        choices = positionChoices,
        selectedValue = state.position,
        onSelect = actions.onPositionChange,
    )
    SettingsSwitchRow(
        label = stringResource(R.string.settings_sidebar_pinned_label),
        checked = state.pinned,
        onCheckedChange = actions.onPinnedChange,
        supportingText = stringResource(R.string.settings_sidebar_pinned_summary),
    )
    SettingsSwitchRow(
        label = stringResource(R.string.settings_sidebar_show_labels_label),
        checked = state.showLabels,
        onCheckedChange = actions.onShowLabelsChange,
        supportingText = stringResource(R.string.settings_sidebar_show_labels_summary),
    )
    SettingsSwitchRow(
        label = stringResource(R.string.settings_sidebar_edge_handle_label),
        checked = state.edgeHandle,
        onCheckedChange = actions.onEdgeHandleChange,
        supportingText = stringResource(R.string.settings_sidebar_edge_handle_summary),
    )
    state.shortcuts.forEachIndexed { index, shortcut ->
        val isSettingsShortcut = shortcut.action is SidebarAction.OpenSettings
        val previousShortcut = state.shortcuts.getOrNull(index - 1)
        SidebarShortcutRow(
            shortcut = shortcut,
            canMoveUp =
                !isSettingsShortcut &&
                    index > 0 &&
                    previousShortcut?.action !is SidebarAction.OpenSettings,
            canMoveDown = !isSettingsShortcut && index < state.shortcuts.lastIndex,
            onEdit = { editingShortcut = shortcut },
            onMoveUp = { actions.onShortcutsChange(state.shortcuts.move(index, index - 1)) },
            onMoveDown = { actions.onShortcutsChange(state.shortcuts.move(index, index + 1)) },
            onDelete =
                if (isSettingsShortcut) {
                    null
                } else {
                    { actions.onShortcutsChange(state.shortcuts.removeAt(index)) }
                },
        )
    }
    Button(
        onClick = { addingShortcut = true },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(R.string.settings_sidebar_add_shortcut_button))
    }

    val shortcutBeingEdited = editingShortcut
    if (shortcutBeingEdited != null) {
        SidebarShortcutDialog(
            title = stringResource(R.string.settings_sidebar_edit_shortcut_title),
            shortcut = shortcutBeingEdited,
            onDismiss = { editingShortcut = null },
            onSave = { updated ->
                actions.onShortcutsChange(
                    state.shortcuts.map { shortcut ->
                        if (shortcut.id == updated.id) {
                            updated
                        } else {
                            shortcut
                        }
                    },
                )
                editingShortcut = null
            },
        )
    }

    if (addingShortcut) {
        val nextId = nextDashboardShortcutId(state.shortcuts)
        SidebarShortcutDialog(
            title = stringResource(R.string.settings_sidebar_add_shortcut_title),
            shortcut = newDashboardShortcut(nextId),
            onDismiss = { addingShortcut = false },
            onSave = { shortcut ->
                actions.onShortcutsChange(state.shortcuts + shortcut)
                addingShortcut = false
            },
        )
    }
}

@Composable
private fun SidebarShortcutRow(
    shortcut: SidebarShortcut,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    ListItem(
        headlineContent = { Text(shortcut.title) },
        supportingContent = { Text(sidebarActionText(shortcut.action)) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.settings_sidebar_move_up),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.settings_sidebar_move_down),
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.settings_action_edit))
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_action_delete))
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onEdit),
    )
}

@Composable
private fun SidebarShortcutDialog(
    title: String,
    shortcut: SidebarShortcut,
    onDismiss: () -> Unit,
    onSave: (SidebarShortcut) -> Unit,
) {
    var titleDraft by remember(shortcut.id) { mutableStateOf(shortcut.title) }
    var shortLabelDraft by remember(shortcut.id) {
        val initialValue = shortcut.shortLabel.orEmpty()
        mutableStateOf(
            SidebarLabelDraft(
                value = initialValue,
                customized = shortcut.shortLabelCustomized,
            ),
        )
    }
    var iconDraft by remember(shortcut.id) { mutableStateOf(shortcut.icon) }
    var actionKind by remember(shortcut.id) { mutableStateOf(SidebarActionKind.fromAction(shortcut.action)) }
    var dashboardPath by remember(shortcut.id) {
        mutableStateOf((shortcut.action as? SidebarAction.OpenDashboardPath)?.path ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { value -> titleDraft = value },
                    label = { Text(stringResource(R.string.settings_sidebar_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = shortLabelDraft.value,
                    onValueChange = { value ->
                        shortLabelDraft = SidebarLabelDraft(value = value, customized = true)
                    },
                    label = { Text(stringResource(R.string.settings_sidebar_label_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsChoiceRow(
                    label = stringResource(R.string.settings_sidebar_icon_label),
                    choices = rememberIconChoices(),
                    selectedValue = iconDraft,
                    onSelect = { value -> iconDraft = value },
                )
                if (shortcut.action !is SidebarAction.OpenSettings) {
                    SettingsChoiceRow(
                        label = stringResource(R.string.settings_sidebar_action_label),
                        choices = rememberActionChoices(),
                        selectedValue = actionKind,
                        onSelect = { value ->
                            actionKind = value
                            shortLabelDraft =
                                updatedLabelDraftForActionChange(
                                    draft = shortLabelDraft,
                                    selectedAction = value.toAction(dashboardPath),
                                )
                        },
                    )
                }
                if (actionKind == SidebarActionKind.Dashboard) {
                    OutlinedTextField(
                        value = dashboardPath,
                        onValueChange = { value -> dashboardPath = value },
                        label = { Text(stringResource(R.string.settings_sidebar_dashboard_path_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedAction = actionKind.toAction(dashboardPath)
                    onSave(
                        savedSidebarShortcut(
                            shortcut = shortcut,
                            titleDraft = titleDraft,
                            shortLabelDraft = shortLabelDraft,
                            iconDraft = iconDraft,
                            selectedAction = selectedAction,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.settings_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_action_cancel))
            }
        },
    )
}

private enum class SidebarActionKind(
    @StringRes val labelRes: Int,
) {
    OpenSettings(R.string.settings_sidebar_action_settings),
    ReloadDashboard(R.string.settings_sidebar_action_reload_dashboard),
    ShowScreensaver(R.string.settings_sidebar_action_show_screensaver),
    DismissScreensaver(R.string.settings_sidebar_action_dismiss_screensaver),
    NightModeOn(R.string.settings_sidebar_action_night_mode_on),
    NightModeOff(R.string.settings_sidebar_action_night_mode_off),
    Dashboard(R.string.settings_sidebar_action_open_dashboard_view),
    ;

    fun toAction(path: String): SidebarAction =
        when (this) {
            OpenSettings -> SidebarAction.OpenSettings
            ReloadDashboard -> SidebarAction.ReloadDashboard
            ShowScreensaver -> SidebarAction.ShowScreensaver
            DismissScreensaver -> SidebarAction.DismissScreensaver
            NightModeOn -> SidebarAction.SetNightModeActive(active = true)
            NightModeOff -> SidebarAction.SetNightModeActive(active = false)
            Dashboard -> SidebarAction.OpenDashboardPath(path.trim().trim('/'))
        }

    companion object {
        fun fromAction(action: SidebarAction): SidebarActionKind =
            when (action) {
                SidebarAction.OpenSettings -> OpenSettings
                SidebarAction.ReloadDashboard -> ReloadDashboard
                SidebarAction.ShowScreensaver -> ShowScreensaver
                SidebarAction.DismissScreensaver -> DismissScreensaver
                is SidebarAction.SetNightModeActive ->
                    if (action.active) {
                        NightModeOn
                    } else {
                        NightModeOff
                    }
                is SidebarAction.OpenDashboardPath -> Dashboard
            }
    }
}

private data class SidebarIconOption(
    val id: String,
    @StringRes val labelRes: Int,
)

private val iconOptions =
    listOf(
        SidebarIconOption("settings", R.string.settings_sidebar_action_settings),
        SidebarIconOption("screensaver", R.string.settings_screensaver_title),
        SidebarIconOption("moon", R.string.settings_sidebar_icon_moon),
        SidebarIconOption("sun", R.string.settings_sidebar_icon_sun),
        SidebarIconOption("refresh", R.string.settings_sidebar_icon_refresh),
        SidebarIconOption("dashboard", R.string.settings_sidebar_icon_dashboard),
    )

private val availableActionKinds =
    SidebarActionKind.entries.filterNot { kind ->
        kind == SidebarActionKind.OpenSettings || kind == SidebarActionKind.DismissScreensaver
    }

@Composable
private fun rememberIconChoices(): List<SettingsChoice<String>> =
    iconOptions.map { option -> SettingsChoice(option.id, stringResource(option.labelRes)) }

@Composable
private fun rememberActionChoices(): List<SettingsChoice<SidebarActionKind>> =
    availableActionKinds.map { kind -> SettingsChoice(kind, stringResource(kind.labelRes)) }

internal fun sidebarActionChoiceLabelIds(): List<Int> = availableActionKinds.map { kind -> kind.labelRes }

internal fun sidebarIconChoiceLabelIds(): List<Int> = iconOptions.map { option -> option.labelRes }

@Composable
private fun sidebarActionText(action: SidebarAction): String =
    when (action) {
        SidebarAction.OpenSettings -> stringResource(R.string.settings_sidebar_action_open_settings)
        SidebarAction.ReloadDashboard -> stringResource(R.string.settings_sidebar_action_reload_dashboard)
        SidebarAction.ShowScreensaver -> stringResource(R.string.settings_sidebar_action_show_screensaver)
        SidebarAction.DismissScreensaver -> stringResource(R.string.settings_sidebar_action_dismiss_screensaver)
        is SidebarAction.SetNightModeActive ->
            if (action.active) {
                stringResource(R.string.settings_sidebar_action_night_mode_on)
            } else {
                stringResource(R.string.settings_sidebar_action_night_mode_off)
            }
        is SidebarAction.OpenDashboardPath ->
            stringResource(R.string.settings_sidebar_action_open_dashboard_view_path, action.path)
    }

private fun List<SidebarShortcut>.move(
    fromIndex: Int,
    toIndex: Int,
): List<SidebarShortcut> {
    val updated = toMutableList()
    val item = updated.removeAt(fromIndex)
    updated.add(toIndex, item)
    return updated
}

private fun List<SidebarShortcut>.removeAt(index: Int): List<SidebarShortcut> =
    toMutableList().also { list -> list.removeAt(index) }

internal fun nextDashboardShortcutId(shortcuts: List<SidebarShortcut>): String {
    val highestSuffix =
        shortcuts
            .mapNotNull { shortcut ->
                shortcut.id
                    .takeIf { id -> id.startsWith("dashboard-") }
                    ?.substringAfter("dashboard-")
                    ?.toIntOrNull()
            }.maxOrNull()
            ?: 0
    return "dashboard-${highestSuffix + 1}"
}

internal fun newDashboardShortcut(id: String): SidebarShortcut {
    val nextIndex = id.substringAfter("dashboard-").toInt()
    val action = SidebarAction.OpenDashboardPath("lovelace/$nextIndex")
    return SidebarShortcut(
        id = id,
        title = "Dashboard $nextIndex",
        icon = "dashboard",
        action = action,
        shortLabel = action.defaultShortLabel,
        shortLabelCustomized = false,
    )
}

internal data class SidebarLabelDraft(
    val value: String,
    val customized: Boolean,
)

internal fun updatedLabelDraftForActionChange(
    draft: SidebarLabelDraft,
    selectedAction: SidebarAction,
): SidebarLabelDraft =
    if (draft.customized) {
        draft
    } else {
        draft.copy(value = selectedAction.defaultShortLabel)
    }

internal fun savedShortLabel(draft: SidebarLabelDraft): String? =
    draft.value.trim().ifBlank { null }

internal fun savedSidebarShortcut(
    shortcut: SidebarShortcut,
    titleDraft: String,
    shortLabelDraft: SidebarLabelDraft,
    iconDraft: String,
    selectedAction: SidebarAction,
): SidebarShortcut =
    shortcut.copy(
        title = titleDraft.trim().ifBlank { shortcut.title },
        shortLabel = savedShortLabel(shortLabelDraft),
        shortLabelCustomized = shortLabelDraft.customized,
        icon = iconDraft,
        action = selectedAction,
    )
