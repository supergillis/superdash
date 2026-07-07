package com.superdash.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.superdash.screensaver.ScreensaverMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsContentTest {
    @get:Rule val composeRule = createComposeRule()

    // LocaleManager is API 33+; below that the device default locale is used,
    // so these tests rely on the test device being set to English.
    @Before
    fun forceEnglish() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getSystemService(android.app.LocaleManager::class.java)
                .applicationLocales = android.os.LocaleList.forLanguageTags("en")
        }
    }

    @After
    fun resetLocale() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getSystemService(android.app.LocaleManager::class.java)
                .applicationLocales = android.os.LocaleList.getEmptyLocaleList()
        }
    }

    @Test
    fun compactLayoutDrillsIntoVoice() {
        composeRule.setContent {
            MaterialTheme {
                SettingsContent(
                    state = settingsUiStateWithVoiceEnabled(),
                    actions = testSettingsActions(),
                    mediaSourcePicker = { _, _ -> },
                    forceWideForTest = false,
                )
            }
        }

        composeRule.onNodeWithTag("settings_nav_voice").performClick()
        composeRule.onNodeWithText("Wake word").assertIsDisplayed()
        composeRule.onAllNodesWithText("Settings").fetchSemanticsNodes().also { nodes ->
            assertTrue(nodes.isEmpty())
        }
    }

    @Test
    fun compactLayoutBackButtonNavigatesUpFromDetail() {
        var backCount = 0
        composeRule.setContent {
            MaterialTheme {
                SettingsContent(
                    state = settingsUiStateWithVoiceEnabled(),
                    actions = testSettingsActions().copy(onBack = { backCount += 1 }),
                    mediaSourcePicker = { _, _ -> },
                    forceWideForTest = false,
                )
            }
        }

        composeRule.onNodeWithTag("settings_nav_voice").performClick()
        composeRule.onNodeWithTag("settings_back").performClick()

        composeRule.onNodeWithTag("settings_nav_voice").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, backCount)
        }
    }

    @Test
    fun compactLayoutBackButtonExitsFromRoot() {
        var backCount = 0
        composeRule.setContent {
            MaterialTheme {
                SettingsContent(
                    state = SettingsUiState.empty(),
                    actions = testSettingsActions().copy(onBack = { backCount += 1 }),
                    mediaSourcePicker = { _, _ -> },
                    forceWideForTest = false,
                )
            }
        }

        composeRule.onNodeWithTag("settings_back").performClick()

        composeRule.runOnIdle {
            assertEquals(1, backCount)
        }
    }

    @Test
    fun wideLayoutShowsNavigationAndHomeAssistantByDefault() {
        composeRule.setContent {
            MaterialTheme {
                SettingsContent(
                    state = SettingsUiState.empty(),
                    actions = testSettingsActions(),
                    mediaSourcePicker = { _, _ -> },
                    forceWideForTest = true,
                )
            }
        }

        composeRule.onNodeWithTag("settings_navigation_pane").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_detail_pane").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_nav_home_assistant").assertIsSelected()
        composeRule.onNodeWithTag("settings_exit").assertIsDisplayed()
        composeRule.onAllNodesWithText("Settings").fetchSemanticsNodes().also { nodes ->
            assertTrue(nodes.isEmpty())
        }
    }

    @Test
    fun wideLayoutKeepsNavigationWhileChangingDetail() {
        composeRule.setContent {
            MaterialTheme {
                SettingsContent(
                    state = settingsUiStateWithVoiceEnabled(),
                    actions = testSettingsActions(),
                    mediaSourcePicker = { _, _ -> },
                    forceWideForTest = true,
                )
            }
        }

        composeRule.onNodeWithTag("settings_nav_voice").performClick()
        composeRule.onNodeWithTag("settings_navigation_pane").assertIsDisplayed()
        composeRule.onNodeWithText("Wake word").assertIsDisplayed()
    }

    @Test
    fun compactLayoutOpensVoiceChildPages() {
        composeRule.setContent {
            MaterialTheme {
                SettingsContent(
                    state = settingsUiStateWithVoiceEnabled(),
                    actions = testSettingsActions(),
                    mediaSourcePicker = { _, _ -> },
                    forceWideForTest = false,
                )
            }
        }

        composeRule.onNodeWithTag("settings_nav_voice").performClick()
        composeRule.onNodeWithText("Speech pipeline").performClick()
        composeRule.onNodeWithText("Assist provider").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_back").performClick()

        composeRule.onNodeWithText("Local models").performClick()
        composeRule.onNodeWithText("STT model").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_back").performClick()

        composeRule.onNodeWithText("Command recording").performClick()
        composeRule.onNodeWithText("Save command recordings").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_back").performClick()

        composeRule.onNodeWithText("Advanced tuning").performClick()
        composeRule.onNodeWithText("Silence timeout: 500 ms").assertIsDisplayed()
    }

    @Test
    fun wideLayoutOpensImmichUnderScreensaver() {
        composeRule.setContent {
            MaterialTheme {
                SettingsContent(
                    state = settingsUiStateWithImmichScreensaver(),
                    actions = testSettingsActions(),
                    mediaSourcePicker = { _, _ -> },
                    forceWideForTest = true,
                )
            }
        }

        composeRule.onNodeWithTag("settings_nav_screensaver").performClick()
        composeRule.onNodeWithText("Immich photos").performClick()

        composeRule.onNodeWithText("Immich URL").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_nav_screensaver").assertIsSelected()
        composeRule.onNodeWithTag("settings_back").assertIsDisplayed()
    }

    private fun settingsUiStateWithVoiceEnabled(): SettingsUiState {
        val state = SettingsUiState.empty()
        return state.copy(voice = state.voice.copy(voiceEnabled = true))
    }

    private fun settingsUiStateWithImmichScreensaver(): SettingsUiState {
        val state = SettingsUiState.empty()
        return state.copy(screensaver = state.screensaver.copy(dayMode = ScreensaverMode.Immich))
    }
}
