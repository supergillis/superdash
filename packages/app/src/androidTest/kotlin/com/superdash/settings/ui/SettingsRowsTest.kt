package com.superdash.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRowsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun navigationRowShowsIconAndSelectedState() {
        composeRule.setContent {
            MaterialTheme {
                SettingsNavigationRow(
                    label = "Voice",
                    supportingText = "Wake word and voice assistant",
                    icon = Icons.Filled.Mic,
                    selected = true,
                    exposeSelection = true,
                    showChevron = false,
                    testTag = "voice_row",
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("voice_row").assertIsSelected()
        composeRule.onAllNodesWithContentDescription("Voice settings").fetchSemanticsNodes().also { nodes ->
            assertTrue(nodes.isEmpty())
        }
    }
}
