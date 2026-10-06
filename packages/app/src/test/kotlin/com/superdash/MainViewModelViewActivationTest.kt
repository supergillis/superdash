package com.superdash

import com.superdash.feed.FeedConfig
import com.superdash.feed.FeedState
import com.superdash.ha.HaConnectionState
import com.superdash.ha.HaTokens
import com.superdash.kiosk.SidebarPosition
import com.superdash.kiosk.SidebarSettingsDefaults
import com.superdash.kiosk.ui.AppState
import com.superdash.voice.pipeline.VoiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelViewActivationTest {
    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private val fakeTokens =
        HaTokens(
            accessToken = "access",
            refreshToken = "refresh",
            expiresAtEpochMs = Long.MAX_VALUE,
        )

    private val fakeFeedConfig =
        FeedConfig(
            id = "test",
            name = "Front",
            triggerEntity = "binary_sensor.front",
            cameraEntity = "camera.front",
        )

    private data class Flows(
        val haUrl: MutableStateFlow<String?>,
        val dashboardPath: MutableStateFlow<String>,
        val tokens: MutableStateFlow<HaTokens?>,
        val haState: MutableStateFlow<HaConnectionState>,
        val voiceState: MutableStateFlow<VoiceState>,
        val isIdle: MutableStateFlow<Boolean>,
        val dayKey: MutableStateFlow<String>,
        val nightKey: MutableStateFlow<String>,
        val nightActive: MutableStateFlow<Boolean>,
        val feedState: MutableStateFlow<FeedState>,
        val voiceEnabled: MutableStateFlow<Boolean>,
    )

    private fun buildViewModel(): Pair<MainViewModel, Flows> {
        val flows =
            Flows(
                haUrl = MutableStateFlow(null),
                dashboardPath = MutableStateFlow(""),
                tokens = MutableStateFlow(null),
                haState = MutableStateFlow(HaConnectionState.Disconnected),
                voiceState = MutableStateFlow(VoiceState.Idle),
                isIdle = MutableStateFlow(false),
                dayKey = MutableStateFlow("photos"),
                nightKey = MutableStateFlow("black"),
                nightActive = MutableStateFlow(false),
                feedState = MutableStateFlow(FeedState.Idle),
                voiceEnabled = MutableStateFlow(false),
            )
        val viewModel =
            MainViewModel(
                haUrlFlow = flows.haUrl,
                dashboardPathFlow = flows.dashboardPath,
                tokensFlow = flows.tokens,
                haStateFlow = flows.haState,
                voiceStateFlow = flows.voiceState,
                isIdleFlow = flows.isIdle,
                dayScreensaverModeKeyFlow = flows.dayKey,
                nightScreensaverModeKeyFlow = flows.nightKey,
                nightModeActiveFlow = flows.nightActive,
                feedStateFlow = flows.feedState,
                voiceEnabledFlow = flows.voiceEnabled,
                sidebarPositionFlow = MutableStateFlow(SidebarPosition.Left),
                sidebarPinnedFlow = MutableStateFlow(false),
                sidebarShowLabelsFlow = MutableStateFlow(false),
                sidebarShortcutsFlow = MutableStateFlow(SidebarSettingsDefaults.shortcuts),
                sidebarEdgeHandleFlow = MutableStateFlow(SidebarSettingsDefaults.edgeHandle),
            )
        return viewModel to flows
    }

    @Test
    fun `feed uiState reflects Idle when feedState is Idle`() =
        runTest {
            val (viewModel, _) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(FeedState.Idle, viewModel.uiState.value.feedState)
        }

    @Test
    fun `feed uiState reflects Showing when feedState becomes Showing`() =
        runTest {
            val (viewModel, flows) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val showing = FeedState.Showing(config = fakeFeedConfig, openedAtEpochMs = 1000L)
            flows.feedState.value = showing
            advanceUntilIdle()

            assertEquals(showing, viewModel.uiState.value.feedState)
        }

    @Test
    fun `isIdle is false when not idle regardless of screensaver mode`() =
        runTest {
            val (viewModel, flows) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            flows.isIdle.value = false
            flows.dayKey.value = "photos"
            flows.nightActive.value = false
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isIdle)
        }

    @Test
    fun `isIdle is true when idle and day screensaver mode is photos`() =
        runTest {
            val (viewModel, flows) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            flows.isIdle.value = true
            flows.dayKey.value = "photos"
            flows.nightActive.value = false
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isIdle)
        }

    @Test
    fun `isIdle is false when idle but day screensaver mode is off`() =
        runTest {
            val (viewModel, flows) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            flows.isIdle.value = true
            flows.dayKey.value = "off"
            flows.nightActive.value = false
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isIdle)
        }

    @Test
    fun `isIdle is false when idle and night active but night screensaver mode is off`() =
        runTest {
            val (viewModel, flows) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            flows.isIdle.value = true
            flows.dayKey.value = "photos"
            flows.nightKey.value = "off"
            flows.nightActive.value = true
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isIdle)
        }

    @Test
    fun `isIdle is true when idle and night active with non-off night screensaver mode`() =
        runTest {
            val (viewModel, flows) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            flows.isIdle.value = true
            flows.dayKey.value = "off"
            flows.nightKey.value = "black"
            flows.nightActive.value = true
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isIdle)
        }

    @Test
    fun `haBaseUrl is empty string when appState is NeedsSetup`() =
        runTest {
            val (viewModel, flows) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            flows.haUrl.value = null
            advanceUntilIdle()
            assertEquals(AppState.NeedsSetup, viewModel.uiState.value.appState)
            assertEquals("", viewModel.uiState.value.haBaseUrl)
        }

    @Test
    fun `haBaseUrl equals the configured URL when appState is Configured`() =
        runTest {
            val (viewModel, flows) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            flows.haUrl.value = "http://homeassistant.local:8123"
            flows.tokens.value = fakeTokens
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.appState is AppState.Configured)
            assertEquals("http://homeassistant.local:8123", viewModel.uiState.value.haBaseUrl)
        }

    @Test
    fun `dashboardPath propagates from source flow`() =
        runTest {
            val (viewModel, flows) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            flows.dashboardPath.value = "/lovelace/main"
            advanceUntilIdle()
            assertEquals("/lovelace/main", viewModel.uiState.value.dashboardPath)
        }

    @Test
    fun `voiceState propagates Idle from source flow`() =
        runTest {
            val (viewModel, flows) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            flows.voiceState.value = VoiceState.Idle
            advanceUntilIdle()
            assertEquals(VoiceState.Idle, viewModel.uiState.value.voiceState)
        }

    @Test
    fun `voiceState propagates WakeFired from source flow`() =
        runTest {
            val (viewModel, flows) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            flows.voiceState.value = VoiceState.WakeFired("hey_jarvis")
            advanceUntilIdle()
            assertEquals(VoiceState.WakeFired("hey_jarvis"), viewModel.uiState.value.voiceState)
        }
}
