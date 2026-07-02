package com.superdash.kiosk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstRunFormTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun submitDisabledUntilValidUrlEntered() {
        composeRule.setContent { MaterialTheme { FirstRunForm(onSubmit = {}) } }
        composeRule.onNodeWithTag("save_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("ha_url_field").performTextInput("homeassistant.local:8123")
        composeRule.onNodeWithTag("save_button").assertIsEnabled()
    }

    @Test
    fun submitEmitsNormalizedUrl() {
        var emitted: String? = null
        composeRule.setContent { MaterialTheme { FirstRunForm(onSubmit = { emitted = it }) } }
        composeRule.onNodeWithTag("ha_url_field").performTextInput("homeassistant.local:8123/")
        composeRule.onNodeWithTag("save_button").performClick()
        assertEquals("http://homeassistant.local:8123", emitted)
    }
}
