package com.superdash.kiosk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.superdash.kiosk.SidebarAction
import com.superdash.kiosk.SidebarPosition
import com.superdash.kiosk.SidebarShortcut
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SidebarRailTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun labeledShortcutExposesOneAccessibleTitleWhenShortLabelMatchesTitle() {
        val shortcut =
            SidebarShortcut(
                id = "settings",
                title = "Settings",
                icon = "settings",
                shortLabel = "Settings",
                action = SidebarAction.OpenSettings,
            )

        composeRule.setContent {
            MaterialTheme {
                SidebarIconButton(
                    shortcut = shortcut,
                    showLabels = true,
                    selected = false,
                    onClick = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Settings").assertCountEquals(1)
        composeRule.onAllNodesWithText("Settings").assertCountEquals(0)
    }

    @Test
    fun labeledShortcutExposesFullTitleWithoutSeparateShortLabelSemantics() {
        val shortcut =
            SidebarShortcut(
                id = "screensaver",
                title = "Show screensaver",
                icon = "screensaver",
                shortLabel = "Sleep",
                action = SidebarAction.ShowScreensaver,
            )

        composeRule.setContent {
            MaterialTheme {
                SidebarIconButton(
                    shortcut = shortcut,
                    showLabels = true,
                    selected = false,
                    onClick = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Show screensaver").assertCountEquals(1)
        composeRule.onAllNodesWithText("Sleep").assertCountEquals(0)
    }

    @Test
    fun openUnpinnedSidebarExposesDismissAction() {
        composeRule.setContent {
            MaterialTheme {
                SidebarRailLayout(
                    position = SidebarPosition.Left,
                    pinned = false,
                    edgeHandle = false,
                    open = true,
                    idle = false,
                    showLabels = false,
                    nightModeActive = false,
                    shortcuts = emptyList(),
                    onOpen = {},
                    onDismiss = {},
                    onPinnedChange = {},
                    onShortcutClick = {},
                    content = { Box {} },
                    overlays = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Dismiss sidebar").assertHasClickAction()
        composeRule.onAllNodesWithContentDescription("Dismiss sidebar").assertCountEquals(1)
    }

    @Test
    fun openUnpinnedSidebarHidesBackgroundSemantics() {
        val shortcut =
            SidebarShortcut(
                id = "settings",
                title = "Settings",
                icon = "settings",
                action = SidebarAction.OpenSettings,
            )

        composeRule.setContent {
            MaterialTheme {
                SidebarRailLayout(
                    position = SidebarPosition.Left,
                    pinned = false,
                    edgeHandle = false,
                    open = true,
                    idle = false,
                    showLabels = false,
                    nightModeActive = false,
                    shortcuts = listOf(shortcut),
                    onOpen = {},
                    onDismiss = {},
                    onPinnedChange = {},
                    onShortcutClick = {},
                    content = { Text("Background action", Modifier.clickable {}) },
                    overlays = { Text("Overlay action", Modifier.clickable {}) },
                )
            }
        }

        composeRule.onAllNodesWithText("Background action").assertCountEquals(0)
        composeRule.onAllNodesWithText("Overlay action").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Dismiss sidebar").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Settings").assertHasClickAction()
    }

    @Test
    fun closedUnpinnedSidebarKeepsBackgroundSemantics() {
        composeRule.setContent {
            MaterialTheme {
                SidebarRailLayout(
                    position = SidebarPosition.Left,
                    pinned = false,
                    edgeHandle = false,
                    open = false,
                    idle = false,
                    showLabels = false,
                    nightModeActive = false,
                    shortcuts = emptyList(),
                    onOpen = {},
                    onDismiss = {},
                    onPinnedChange = {},
                    onShortcutClick = {},
                    content = { Text("Background action", Modifier.clickable {}) },
                    overlays = { Text("Overlay action", Modifier.clickable {}) },
                )
            }
        }

        composeRule.onAllNodesWithText("Background action").assertCountEquals(1)
        composeRule.onAllNodesWithText("Overlay action").assertCountEquals(1)
    }

    @Test
    fun pinnedSidebarKeepsBackgroundSemantics() {
        composeRule.setContent {
            MaterialTheme {
                SidebarRailLayout(
                    position = SidebarPosition.Left,
                    pinned = true,
                    edgeHandle = false,
                    open = true,
                    idle = false,
                    showLabels = false,
                    nightModeActive = false,
                    shortcuts = emptyList(),
                    onOpen = {},
                    onDismiss = {},
                    onPinnedChange = {},
                    onShortcutClick = {},
                    content = { Text("Background action", Modifier.clickable {}) },
                    overlays = { Text("Overlay action", Modifier.clickable {}) },
                )
            }
        }

        composeRule.onAllNodesWithText("Background action").assertCountEquals(1)
        composeRule.onAllNodesWithText("Overlay action").assertCountEquals(1)
    }

    @Test
    fun unpinnedClosedSidebarWithHandleShowsHandle() {
        composeRule.setContent {
            MaterialTheme {
                SidebarRailLayout(
                    position = SidebarPosition.Left,
                    pinned = false,
                    edgeHandle = true,
                    open = false,
                    idle = false,
                    showLabels = false,
                    nightModeActive = false,
                    shortcuts = emptyList(),
                    onOpen = {},
                    onDismiss = {},
                    onPinnedChange = {},
                    onShortcutClick = {},
                    content = { Box {} },
                    overlays = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open sidebar").assertHasClickAction()
    }

    @Test
    fun unpinnedClosedSidebarWithoutHandleHidesHandle() {
        composeRule.setContent {
            MaterialTheme {
                SidebarRailLayout(
                    position = SidebarPosition.Left,
                    pinned = false,
                    edgeHandle = false,
                    open = false,
                    idle = false,
                    showLabels = false,
                    nightModeActive = false,
                    shortcuts = emptyList(),
                    onOpen = {},
                    onDismiss = {},
                    onPinnedChange = {},
                    onShortcutClick = {},
                    content = { Box {} },
                    overlays = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Open sidebar").assertCountEquals(0)
    }

    @Test
    fun pinnedSidebarHidesHandle() {
        composeRule.setContent {
            MaterialTheme {
                SidebarRailLayout(
                    position = SidebarPosition.Left,
                    pinned = true,
                    edgeHandle = true,
                    open = false,
                    idle = false,
                    showLabels = false,
                    nightModeActive = false,
                    shortcuts = emptyList(),
                    onOpen = {},
                    onDismiss = {},
                    onPinnedChange = {},
                    onShortcutClick = {},
                    content = { Box {} },
                    overlays = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Open sidebar").assertCountEquals(0)
    }

    @Test
    fun tappingHandleOpensSidebar() {
        var opened = false
        composeRule.setContent {
            MaterialTheme {
                SidebarRailLayout(
                    position = SidebarPosition.Left,
                    pinned = false,
                    edgeHandle = true,
                    open = false,
                    idle = false,
                    showLabels = false,
                    nightModeActive = false,
                    shortcuts = emptyList(),
                    onOpen = { opened = true },
                    onDismiss = {},
                    onPinnedChange = {},
                    onShortcutClick = {},
                    content = { Box {} },
                    overlays = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open sidebar").performClick()
        composeRule.runOnIdle { assert(opened) }
    }

    @Test
    fun idleHidesPinnedRail() {
        val shortcut =
            SidebarShortcut(
                id = "settings",
                title = "Settings",
                icon = "settings",
                action = SidebarAction.OpenSettings,
            )

        composeRule.setContent {
            MaterialTheme {
                SidebarRailLayout(
                    position = SidebarPosition.Left,
                    pinned = true,
                    edgeHandle = false,
                    open = false,
                    idle = true,
                    showLabels = false,
                    nightModeActive = false,
                    shortcuts = listOf(shortcut),
                    onOpen = {},
                    onDismiss = {},
                    onPinnedChange = {},
                    onShortcutClick = {},
                    content = { Box {} },
                    overlays = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Settings").assertCountEquals(0)
    }

    @Test
    fun idleHidesEdgeHandle() {
        composeRule.setContent {
            MaterialTheme {
                SidebarRailLayout(
                    position = SidebarPosition.Left,
                    pinned = false,
                    edgeHandle = true,
                    open = false,
                    idle = true,
                    showLabels = false,
                    nightModeActive = false,
                    shortcuts = emptyList(),
                    onOpen = {},
                    onDismiss = {},
                    onPinnedChange = {},
                    onShortcutClick = {},
                    content = { Box {} },
                    overlays = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Open sidebar").assertCountEquals(0)
    }
}
