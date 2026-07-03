package com.superdash

import com.superdash.doorbell.DoorbellState
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
class MainViewModelTest {
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

    private class Inputs(
        val haUrl: MutableStateFlow<String?>,
        val tokens: MutableStateFlow<HaTokens?>,
        val haState: MutableStateFlow<HaConnectionState>,
        val voiceEnabled: MutableStateFlow<Boolean>,
        val doorbellState: MutableStateFlow<DoorbellState>,
    )

    private fun buildViewModel(): Pair<MainViewModel, Inputs> {
        val haUrl = MutableStateFlow<String?>(null)
        val dashboardPath = MutableStateFlow("")
        val tokens = MutableStateFlow<HaTokens?>(null)
        val haState = MutableStateFlow<HaConnectionState>(HaConnectionState.Disconnected)
        val voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
        val isIdle = MutableStateFlow(false)
        val dayKey = MutableStateFlow("photos")
        val nightKey = MutableStateFlow("black")
        val nightActive = MutableStateFlow(false)
        val doorbellState = MutableStateFlow<DoorbellState>(DoorbellState.Idle)
        val autoClose = MutableStateFlow(60)
        val voiceEnabled = MutableStateFlow(false)
        val sidebarPosition = MutableStateFlow(SidebarPosition.Left)
        val sidebarPinned = MutableStateFlow(false)
        val sidebarShowLabels = MutableStateFlow(false)
        val sidebarShortcuts = MutableStateFlow(SidebarSettingsDefaults.shortcuts)

        val viewModel =
            MainViewModel(
                haUrlFlow = haUrl,
                dashboardPathFlow = dashboardPath,
                tokensFlow = tokens,
                haStateFlow = haState,
                voiceStateFlow = voiceState,
                isIdleFlow = isIdle,
                dayScreensaverModeKeyFlow = dayKey,
                nightScreensaverModeKeyFlow = nightKey,
                nightModeActiveFlow = nightActive,
                doorbellStateFlow = doorbellState,
                doorbellAutoCloseSecFlow = autoClose,
                voiceEnabledFlow = voiceEnabled,
                sidebarPositionFlow = sidebarPosition,
                sidebarPinnedFlow = sidebarPinned,
                sidebarShowLabelsFlow = sidebarShowLabels,
                sidebarShortcutsFlow = sidebarShortcuts,
            )

        return viewModel to Inputs(haUrl, tokens, haState, voiceEnabled, doorbellState)
    }

    @Test
    fun `appState is NeedsSetup when haUrl is null`() =
        runTest {
            val (viewModel, _) = buildViewModel()
            // Subscribe so WhileSubscribed activates the source combine.
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(AppState.NeedsSetup, viewModel.uiState.value.appState)
        }

    @Test
    fun `appState seed is Loading before settings emit`() =
        runTest {
            val (viewModel, _) = buildViewModel()
            // No subscriber yet, so uiState holds the stateIn seed. It must be Loading,
            // not NeedsSetup: otherwise an already-configured user briefly sees the
            // first-run setup form on launch before persisted settings load.
            assertEquals(AppState.Loading, viewModel.uiState.value.appState)
        }

    @Test
    fun `appState is NeedsSetup when haUrl is blank`() =
        runTest {
            val (viewModel, inputs) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            inputs.haUrl.value = "   "
            advanceUntilIdle()
            assertEquals(AppState.NeedsSetup, viewModel.uiState.value.appState)
        }

    @Test
    fun `appState transitions to Configured when haUrl is set`() =
        runTest {
            val (viewModel, inputs) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            inputs.haUrl.value = "http://homeassistant.local:8123"
            advanceUntilIdle()
            assertEquals(
                AppState.Configured("http://homeassistant.local:8123"),
                viewModel.uiState.value.appState,
            )
        }

    @Test
    fun `appState is NeedsReauth when haClient emits NeedsReauth`() =
        runTest {
            val (viewModel, inputs) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            inputs.haUrl.value = "http://homeassistant.local:8123"
            inputs.tokens.value = fakeTokens
            inputs.haState.value = HaConnectionState.NeedsReauth(reason = "token_invalid")
            advanceUntilIdle()
            val appState = viewModel.uiState.value.appState
            assertTrue(appState is AppState.NeedsReauth)
            assertEquals("token_invalid", (appState as AppState.NeedsReauth).reason)
        }

    @Test
    fun `appState returns to Configured after reauth resolves`() =
        runTest {
            val (viewModel, inputs) = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            inputs.haUrl.value = "http://homeassistant.local:8123"
            inputs.tokens.value = fakeTokens
            inputs.haState.value = HaConnectionState.NeedsReauth(reason = "token_invalid")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.appState is AppState.NeedsReauth)

            inputs.haState.value = HaConnectionState.Connected(haVersion = "2025.1.0")
            advanceUntilIdle()
            assertEquals(
                AppState.Configured("http://homeassistant.local:8123"),
                viewModel.uiState.value.appState,
            )
        }

    @Test
    fun `voiceServiceShouldRun is false when voiceEnabled is false`() =
        runTest {
            val (viewModel, inputs) = buildViewModel()
            backgroundScope.launch { viewModel.voiceServiceShouldRun.collect {} }
            inputs.tokens.value = fakeTokens
            inputs.voiceEnabled.value = false
            advanceUntilIdle()
            assertFalse(viewModel.voiceServiceShouldRun.value)
        }

    @Test
    fun `voiceServiceShouldRun is false when tokens are absent`() =
        runTest {
            val (viewModel, inputs) = buildViewModel()
            backgroundScope.launch { viewModel.voiceServiceShouldRun.collect {} }
            inputs.tokens.value = null
            inputs.haUrl.value = "http://homeassistant.local:8123"
            inputs.voiceEnabled.value = true
            advanceUntilIdle()
            assertFalse(viewModel.voiceServiceShouldRun.value)
        }

    @Test
    fun `voiceServiceShouldRun is true when voiceEnabled is true`() =
        runTest {
            val (viewModel, inputs) = buildViewModel()
            backgroundScope.launch { viewModel.voiceServiceShouldRun.collect {} }
            inputs.tokens.value = fakeTokens
            inputs.haUrl.value = "http://homeassistant.local:8123"
            inputs.voiceEnabled.value = true
            advanceUntilIdle()
            assertTrue(viewModel.voiceServiceShouldRun.value)
        }

    @Test
    fun `voiceServiceShouldRun is false when HA needs reauth`() =
        runTest {
            val (viewModel, inputs) = buildViewModel()
            backgroundScope.launch { viewModel.voiceServiceShouldRun.collect {} }
            inputs.tokens.value = fakeTokens
            inputs.haUrl.value = "http://homeassistant.local:8123"
            inputs.haState.value = HaConnectionState.NeedsReauth(reason = "token_invalid")
            inputs.voiceEnabled.value = true
            advanceUntilIdle()
            assertFalse(viewModel.voiceServiceShouldRun.value)
        }

    @Test
    fun `sidebar settings propagate to uiState`() =
        runTest {
            val haUrl = MutableStateFlow<String?>(null)
            val dashboardPath = MutableStateFlow("")
            val tokens = MutableStateFlow<HaTokens?>(null)
            val haState = MutableStateFlow<HaConnectionState>(HaConnectionState.Disconnected)
            val voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
            val isIdle = MutableStateFlow(false)
            val dayKey = MutableStateFlow("photos")
            val nightKey = MutableStateFlow("black")
            val nightActive = MutableStateFlow(false)
            val doorbellState = MutableStateFlow<DoorbellState>(DoorbellState.Idle)
            val autoClose = MutableStateFlow(60)
            val voiceEnabled = MutableStateFlow(false)
            val sidebarPosition = MutableStateFlow(SidebarPosition.Bottom)
            val sidebarPinned = MutableStateFlow(true)
            val sidebarShowLabels = MutableStateFlow(true)
            val sidebarShortcuts = MutableStateFlow(SidebarSettingsDefaults.shortcuts.take(1))
            val viewModel =
                MainViewModel(
                    haUrlFlow = haUrl,
                    dashboardPathFlow = dashboardPath,
                    tokensFlow = tokens,
                    haStateFlow = haState,
                    voiceStateFlow = voiceState,
                    isIdleFlow = isIdle,
                    dayScreensaverModeKeyFlow = dayKey,
                    nightScreensaverModeKeyFlow = nightKey,
                    nightModeActiveFlow = nightActive,
                    doorbellStateFlow = doorbellState,
                    doorbellAutoCloseSecFlow = autoClose,
                    voiceEnabledFlow = voiceEnabled,
                    sidebarPositionFlow = sidebarPosition,
                    sidebarPinnedFlow = sidebarPinned,
                    sidebarShowLabelsFlow = sidebarShowLabels,
                    sidebarShortcutsFlow = sidebarShortcuts,
                )

            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(SidebarPosition.Bottom, viewModel.uiState.value.sidebar.position)
            assertEquals(true, viewModel.uiState.value.sidebar.pinned)
            assertEquals(true, viewModel.uiState.value.sidebar.showLabels)
            assertEquals(SidebarSettingsDefaults.shortcuts.take(1), viewModel.uiState.value.sidebar.shortcuts)
        }
}
