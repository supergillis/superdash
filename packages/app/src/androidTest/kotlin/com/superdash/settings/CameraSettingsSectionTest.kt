package com.superdash.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.superdash.settings.ui.CameraSettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraSettingsSectionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun dragGestureOnMaxFpsSliderCommitsExactlyOnceOnRelease() {
        val recordedMaxFps = mutableListOf<Int>()
        val state =
            CameraSettingsState.empty().copy(
                enabled = true,
                cameraPermissionGranted = true,
                maxFps = 10,
            )
        val actions =
            testSettingsActions().camera.copy(
                onMaxFpsChange = { value -> recordedMaxFps.add(value) },
            )

        composeRule.setContent {
            MaterialTheme {
                CameraSettingsSection(state = state, actions = actions)
            }
        }

        // The section renders two sliders: motion sensitivity (0f..100f) and
        // max fps (1f..30f). Select the max-fps one by its distinct
        // ProgressBarRangeInfo range rather than by index, so the test keeps
        // working if the rows are reordered.
        val maxFpsSliderMatcher =
            SemanticsMatcher("has ProgressBarRangeInfo range 1f..30f") { node ->
                node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 1f..30f
            }
        val maxFpsSlider = composeRule.onAllNodes(maxFpsSliderMatcher).get(0)

        maxFpsSlider.performTouchInput { swipeRight() }

        composeRule.runOnIdle {
            assertEquals(1, recordedMaxFps.size)
            assertTrue(recordedMaxFps.single() in 1..30)
        }
    }
}
