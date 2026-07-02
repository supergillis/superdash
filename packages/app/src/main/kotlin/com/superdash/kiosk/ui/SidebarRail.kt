package com.superdash.kiosk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.superdash.kiosk.SidebarPosition
import com.superdash.kiosk.SidebarShortcut
import com.superdash.kiosk.defaultShortLabel
import com.superdash.kiosk.selectedInSidebar

private val compactRailSize = 64.dp
private val labeledRailSize = 96.dp
private val compactButtonSize = 48.dp
private val labeledItemWidth = 84.dp
private val labeledItemHeight = 64.dp

@Composable
fun SidebarRailLayout(
    position: SidebarPosition,
    pinned: Boolean,
    open: Boolean,
    showLabels: Boolean,
    nightModeActive: Boolean,
    shortcuts: List<SidebarShortcut>,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onPinnedChange: (Boolean) -> Unit,
    onShortcutClick: (SidebarShortcut) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    overlays: @Composable BoxScope.() -> Unit,
) {
    val hideBackgroundSemantics = !pinned && open
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .detectEdgeSwipe(edge = position, onTriggered = onOpen),
    ) {
        if (pinned) {
            ReservedSidebarContentLayout(
                position = position,
                showLabels = showLabels,
                content = content,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .hideFromAccessibilityWhen(hideBackgroundSemantics),
            ) {
                content()
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .hideFromAccessibilityWhen(hideBackgroundSemantics),
        ) {
            overlays()
        }
        if (pinned) {
            SidebarRail(
                position = position,
                pinned = true,
                showLabels = showLabels,
                nightModeActive = nightModeActive,
                shortcuts = shortcuts,
                onPinnedChange = onPinnedChange,
                onShortcutClick = onShortcutClick,
                modifier = Modifier.align(position.alignment),
            )
        } else if (open) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(onClickLabel = "Dismiss sidebar", onClick = onDismiss)
                        .semantics { contentDescription = "Dismiss sidebar" },
            )
            SidebarRail(
                position = position,
                pinned = false,
                showLabels = showLabels,
                nightModeActive = nightModeActive,
                shortcuts = shortcuts,
                onPinnedChange = onPinnedChange,
                onShortcutClick = onShortcutClick,
                modifier = Modifier.align(position.alignment),
            )
        }
    }
}

private fun Modifier.hideFromAccessibilityWhen(value: Boolean): Modifier =
    if (value) {
        semantics { hideFromAccessibility() }
    } else {
        this
    }

@Composable
private fun ReservedSidebarContentLayout(
    position: SidebarPosition,
    showLabels: Boolean,
    content: @Composable () -> Unit,
) {
    val railSize =
        if (showLabels) {
            labeledRailSize
        } else {
            compactRailSize
        }
    when (position) {
        SidebarPosition.Left ->
            Row(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.width(railSize).fillMaxHeight())
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        SidebarPosition.Right ->
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
                Spacer(modifier = Modifier.width(railSize).fillMaxHeight())
            }
        SidebarPosition.Top ->
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(railSize).fillMaxWidth())
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        SidebarPosition.Bottom ->
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
                Spacer(modifier = Modifier.height(railSize).fillMaxWidth())
            }
    }
}

@Composable
fun SidebarRail(
    position: SidebarPosition,
    pinned: Boolean,
    showLabels: Boolean,
    nightModeActive: Boolean,
    shortcuts: List<SidebarShortcut>,
    onPinnedChange: (Boolean) -> Unit,
    onShortcutClick: (SidebarShortcut) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVertical = position == SidebarPosition.Left || position == SidebarPosition.Right
    val scrollState = rememberScrollState()
    val railSize =
        if (showLabels) {
            labeledRailSize
        } else {
            compactRailSize
        }
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier =
            modifier.then(
                if (isVertical) {
                    Modifier.width(railSize).fillMaxHeight()
                } else {
                    Modifier.height(railSize).fillMaxWidth()
                },
            ),
    ) {
        if (isVertical) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .verticalScroll(scrollState)
                        .padding(vertical = 8.dp),
            ) {
                PinButton(pinned = pinned, onPinnedChange = onPinnedChange)
                shortcuts.forEach { shortcut ->
                    SidebarIconButton(
                        shortcut = shortcut,
                        showLabels = showLabels,
                        selected = shortcut.selectedInSidebar(nightModeActive),
                        onClick = { onShortcutClick(shortcut) },
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 8.dp),
            ) {
                PinButton(pinned = pinned, onPinnedChange = onPinnedChange)
                shortcuts.forEach { shortcut ->
                    SidebarIconButton(
                        shortcut = shortcut,
                        showLabels = showLabels,
                        selected = shortcut.selectedInSidebar(nightModeActive),
                        onClick = { onShortcutClick(shortcut) },
                    )
                }
            }
        }
    }
}

@Composable
fun SidebarIconButton(
    shortcut: SidebarShortcut,
    showLabels: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (showLabels) {
        TextButton(
            onClick = onClick,
            colors =
                ButtonDefaults.textButtonColors(
                    containerColor =
                        if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        },
                    contentColor =
                        if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                ),
            modifier =
                Modifier
                    .size(width = labeledItemWidth, height = labeledItemHeight)
                    .semantics { contentDescription = sidebarContentDescription(shortcut, selected) },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = sidebarIcon(shortcut.icon),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = sidebarLabel(shortcut),
                    modifier = Modifier.clearAndSetSemantics {},
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    } else {
        if (selected) {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(compactButtonSize),
            ) {
                Icon(
                    imageVector = sidebarIcon(shortcut.icon),
                    contentDescription = sidebarContentDescription(shortcut, selected),
                )
            }
        } else {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(compactButtonSize),
            ) {
                Icon(
                    imageVector = sidebarIcon(shortcut.icon),
                    contentDescription = sidebarContentDescription(shortcut, selected),
                )
            }
        }
    }
}

@Composable
private fun PinButton(
    pinned: Boolean,
    onPinnedChange: (Boolean) -> Unit,
) {
    IconButton(
        onClick = { onPinnedChange(!pinned) },
        modifier = Modifier.size(compactButtonSize),
    ) {
        Icon(
            imageVector =
                if (pinned) {
                    Icons.Filled.Close
                } else {
                    Icons.Filled.PushPin
                },
            contentDescription =
                if (pinned) {
                    "Unpin sidebar"
                } else {
                    "Pin sidebar"
                },
        )
    }
}

private val SidebarPosition.alignment: Alignment
    get() =
        when (this) {
            SidebarPosition.Left -> Alignment.CenterStart
            SidebarPosition.Right -> Alignment.CenterEnd
            SidebarPosition.Top -> Alignment.TopCenter
            SidebarPosition.Bottom -> Alignment.BottomCenter
        }

internal fun sidebarLabel(shortcut: SidebarShortcut): String {
    val label = shortcut.shortLabel?.trim().orEmpty()
    return if (shortcut.shortLabelCustomized) {
        label
    } else {
        label.ifBlank { shortcut.action.defaultShortLabel }
    }
}

private fun sidebarContentDescription(shortcut: SidebarShortcut, selected: Boolean): String =
    if (selected) {
        "${shortcut.title}, active"
    } else {
        shortcut.title
    }

private fun sidebarIcon(value: String): ImageVector =
    when (value) {
        "settings" -> Icons.Filled.Settings
        "screensaver" -> Icons.Filled.Wallpaper
        "moon" -> Icons.Filled.DarkMode
        "sun" -> Icons.Filled.LightMode
        "refresh" -> Icons.Filled.Refresh
        "dashboard" -> Icons.Filled.DashboardCustomize
        else -> Icons.Filled.DashboardCustomize
    }
