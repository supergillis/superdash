package com.superdash.settings

import com.superdash.core.locale.SupportedLanguage
import com.superdash.core.resources.StringProvider
import com.superdash.doorbell.DoorbellConfig
import com.superdash.doorbell.DoorbellSettings
import com.superdash.doorbell.DoorbellState
import com.superdash.ha.EntityState
import com.superdash.ha.HaConnectionState
import com.superdash.kiosk.KioskSettings
import com.superdash.kiosk.SidebarPosition
import com.superdash.kiosk.SidebarSettings
import com.superdash.kiosk.SidebarSettingsDefaults
import com.superdash.kiosk.SidebarShortcut
import com.superdash.screensaver.ScreensaverMode
import com.superdash.screensaver.ScreensaverSettings
import com.superdash.voice.VoiceSettings
import com.superdash.voice.models.VoiceModelState
import com.superdash.voice.pipeline.VoiceResponseMode
import com.superdash.voice.pipeline.VoiceSttProvider
import com.superdash.voice.wake.WakeWordModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private val emptyEntity =
        EntityState(
            entityId = "weather.home",
            state = "sunny",
            attributes = JsonObject(emptyMap()),
        )

    private val fakePskStore =
        object : PskStore {
            override val psk: Flow<ByteArray?> = MutableStateFlow(null)

            override suspend fun set(psk: ByteArray) = Unit

            override suspend fun clear() = Unit
        }

    private val fakeLocaleController =
        object : LocaleSettingsController {
            override fun isPerAppLanguageSupported(): Boolean = true

            override fun currentLanguage(): SupportedLanguage? = null

            override fun setLanguage(language: SupportedLanguage?) = Unit
        }

    private val fakeStrings =
        object : StringProvider {
            override fun get(id: Int): String = "string:$id"

            override fun get(id: Int, vararg args: Any): String =
                "string:$id(${args.joinToString(",")})"

            override fun getQuantity(id: Int, quantity: Int, vararg args: Any): String =
                "plural:$id:$quantity(${args.joinToString(",")})"
        }

    private class FakeLocaleController(
        private val language: SupportedLanguage? = null,
        private val perAppLanguageSupported: Boolean = true,
    ) : LocaleSettingsController {
        var lastSetLanguage: SupportedLanguage? = null
            private set
        var setLanguageCallCount = 0
            private set

        override fun isPerAppLanguageSupported(): Boolean = perAppLanguageSupported

        override fun currentLanguage(): SupportedLanguage? = language

        override fun setLanguage(language: SupportedLanguage?) {
            lastSetLanguage = language
            setLanguageCallCount += 1
        }
    }

    private fun buildViewModel(
        kiosk: FakeKioskSettings = FakeKioskSettings(),
        sidebar: FakeSidebarSettings = FakeSidebarSettings(),
        voice: FakeVoiceSettings = FakeVoiceSettings(),
        doorbell: FakeDoorbellSettings = FakeDoorbellSettings(),
        screensaver: FakeScreensaverSettings = FakeScreensaverSettings(),
        pskStore: PskStore = fakePskStore,
        localeController: LocaleSettingsController = fakeLocaleController,
        haUrl: MutableStateFlow<String?> = MutableStateFlow(null),
        haState: MutableStateFlow<HaConnectionState> = MutableStateFlow(HaConnectionState.Disconnected),
        entities: MutableStateFlow<Map<String, EntityState>> = MutableStateFlow(emptyMap()),
        doorbellState: MutableStateFlow<DoorbellState> = MutableStateFlow(DoorbellState.Idle),
        isIdle: MutableStateFlow<Boolean> = MutableStateFlow(false),
        voiceModelState: MutableStateFlow<VoiceModelState> = MutableStateFlow(VoiceModelState(emptyList())),
        actions: SettingsExternalActions = RecordingSettingsExternalActions(),
        strings: StringProvider = fakeStrings,
    ): SettingsViewModel =
        SettingsViewModel(
            kioskSettings = kiosk,
            sidebarSettings = sidebar,
            voiceSettings = voice,
            doorbellSettings = doorbell,
            screensaverSettings = screensaver,
            esphomePskStore = pskStore,
            localeController = localeController,
            haUrlFlow = haUrl,
            immichUrlFlow = MutableStateFlow(""),
            immichApiKeyFlow = MutableStateFlow(""),
            immichAlbumFlow = MutableStateFlow(""),
            voiceModelStateFlow = voiceModelState,
            haStateFlow = haState,
            entitiesFlow = entities,
            doorbellStateFlow = doorbellState,
            isIdleFlow = isIdle,
            actions = actions,
            strings = strings,
        )

    @Test
    fun `empty() matches initial uiState before flows emit`() =
        runTest {
            val viewModel = buildViewModel()
            assertEquals(SettingsUiState.empty(), viewModel.uiState.value)
        }

    @Test
    fun `empty voice settings use default wake word`() {
        assertEquals(WakeWordModel.DEFAULT_ID, SettingsUiState.empty().activeWakeWord)
    }

    @Test
    fun `entityCount reflects connected entities map size`() =
        runTest {
            val entities = MutableStateFlow<Map<String, EntityState>>(emptyMap())
            val viewModel = buildViewModel(entities = entities)
            backgroundScope.launch { viewModel.uiState.collect {} }

            entities.value = mapOf("weather.home" to emptyEntity)
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.entityCount)
        }

    @Test
    fun `haEntities reflects connected entities sorted by id`() =
        runTest {
            val entities = MutableStateFlow<Map<String, EntityState>>(emptyMap())
            val viewModel = buildViewModel(entities = entities)
            backgroundScope.launch { viewModel.uiState.collect {} }

            entities.value =
                mapOf(
                    "weather.home" to emptyEntity,
                    "camera.front" to emptyEntity.copy(entityId = "camera.front", state = "idle"),
                )
            advanceUntilIdle()

            assertEquals(
                listOf("camera.front", "weather.home"),
                viewModel.uiState.value.haEntities
                    .map { entity -> entity.entityId },
            )
        }

    @Test
    fun `haState propagates to uiState`() =
        runTest {
            val haState = MutableStateFlow<HaConnectionState>(HaConnectionState.Disconnected)
            val viewModel = buildViewModel(haState = haState)
            backgroundScope.launch { viewModel.uiState.collect {} }

            haState.value = HaConnectionState.Connected(haVersion = "2025.1.0")
            advanceUntilIdle()
            assertEquals(
                HaConnectionState.Connected(haVersion = "2025.1.0"),
                viewModel.uiState.value.haState,
            )
        }

    @Test
    fun `doorbellState propagates to uiState`() =
        runTest {
            val doorbellState = MutableStateFlow<DoorbellState>(DoorbellState.Idle)
            val viewModel = buildViewModel(doorbellState = doorbellState)
            backgroundScope.launch { viewModel.uiState.collect {} }

            val showing =
                DoorbellState.Showing(
                    config =
                        DoorbellConfig(
                            id = "test",
                            name = "Front",
                            triggerEntity = "binary_sensor.front",
                            cameraEntity = "camera.front",
                        ),
                    openedAtEpochMs = 0L,
                )
            doorbellState.value = showing
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.doorbellState is DoorbellState.Showing)
        }

    @Test
    fun `isIdle propagates to uiState`() =
        runTest {
            val isIdle = MutableStateFlow(false)
            val viewModel = buildViewModel(isIdle = isIdle)
            backgroundScope.launch { viewModel.uiState.collect {} }

            isIdle.value = true
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isIdle)
        }

    @Test
    fun `haUrl propagates to uiState`() =
        runTest {
            val haUrl = MutableStateFlow<String?>(null)
            val viewModel = buildViewModel(haUrl = haUrl)
            backgroundScope.launch { viewModel.uiState.collect {} }

            haUrl.value = "http://homeassistant.local:8123"
            advanceUntilIdle()
            assertEquals("http://homeassistant.local:8123", viewModel.uiState.value.haUrl)
        }

    @Test
    fun `currentLanguage propagates to uiState`() =
        runTest {
            val localeController = FakeLocaleController(language = SupportedLanguage.FRENCH)
            val viewModel = buildViewModel(localeController = localeController)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(SupportedLanguage.FRENCH, viewModel.uiState.value.general.currentLanguage)
        }

    @Test
    fun `languagePickerAvailable follows localeController support`() =
        runTest {
            val localeController = FakeLocaleController(perAppLanguageSupported = false)
            val viewModel = buildViewModel(localeController = localeController)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.general.languagePickerAvailable)
        }

    @Test
    fun `setLanguage delegates to localeController`() =
        runTest {
            val localeController = FakeLocaleController()
            val viewModel = buildViewModel(localeController = localeController)

            viewModel.setLanguage(SupportedLanguage.DUTCH)
            advanceUntilIdle()

            assertEquals(SupportedLanguage.DUTCH, localeController.lastSetLanguage)
            assertEquals(1, localeController.setLanguageCallCount)
        }

    @Test
    fun `pictureSpacingDp propagates to uiState`() =
        runTest {
            val screensaver = FakeScreensaverSettings(pictureSpacingDpFlow = MutableStateFlow(24))
            val viewModel = buildViewModel(screensaver = screensaver)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(24, viewModel.uiState.value.pictureSpacingDp)
        }

    @Test
    fun `day off and night immich propagates to uiState`() =
        runTest {
            val screensaver =
                FakeScreensaverSettings(
                    dayModeFlow = MutableStateFlow("off"),
                    nightModeFlow = MutableStateFlow("immich"),
                )
            val viewModel = buildViewModel(screensaver = screensaver)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(ScreensaverMode.Off, viewModel.uiState.value.screensaver.dayMode)
            assertEquals(ScreensaverMode.Immich, viewModel.uiState.value.screensaver.nightMode)
        }

    @Test
    fun `setImmichUrl delegates to actions`() =
        runTest {
            val actions = RecordingSettingsExternalActions()
            val viewModel = buildViewModel(actions = actions)

            viewModel.setImmichUrl("http://immich.local:2283")
            advanceUntilIdle()

            assertEquals("http://immich.local:2283", actions.immichUrl)
        }

    @Test
    fun `refreshImmichCatalog formats success via StringProvider`() =
        runTest {
            val actions =
                object : SettingsExternalActions by RecordingSettingsExternalActions() {
                    override suspend fun refreshImmichCatalog(): RefreshImmichCatalogResult =
                        RefreshImmichCatalogResult.Success(itemCount = 42)
                }
            val viewModel = buildViewModel(actions = actions, strings = fakeStrings)

            val message = viewModel.refreshImmichCatalog()

            assertEquals(
                fakeStrings.getQuantity(
                    com.superdash.R.plurals.settings_immich_refresh_success,
                    42,
                    42,
                ),
                message,
            )
        }

    @Test
    fun `refreshImmichCatalog formats failure via StringProvider`() =
        runTest {
            val actions =
                object : SettingsExternalActions by RecordingSettingsExternalActions() {
                    override suspend fun refreshImmichCatalog(): RefreshImmichCatalogResult =
                        RefreshImmichCatalogResult.Failure(reason = "boom")
                }
            val viewModel = buildViewModel(actions = actions, strings = fakeStrings)

            val message = viewModel.refreshImmichCatalog()

            assertEquals(
                fakeStrings.get(com.superdash.R.string.settings_immich_refresh_failed, "boom"),
                message,
            )
        }

    @Test
    fun `setVoiceEnabled delegates to voice settings`() =
        runTest {
            val voice = FakeVoiceSettings()
            val viewModel = buildViewModel(voice = voice)

            viewModel.setVoiceEnabled(true)
            advanceUntilIdle()

            assertEquals(true, voice.lastEnabled)
        }

    @Test
    fun `voiceAssistProvider propagates to uiState`() =
        runTest {
            val voice = FakeVoiceSettings(assistProviderFlow = MutableStateFlow(VoiceSttProvider.Moonshine.key))
            val viewModel = buildViewModel(voice = voice)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(VoiceSttProvider.Moonshine, viewModel.uiState.value.voice.assistProvider)
        }

    @Test
    fun `stt provider defaults propagate to uiState`() =
        runTest {
            val viewModel = buildViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(VoiceSttProvider.HaAssist, viewModel.uiState.value.voice.primarySttProvider)
            assertEquals(VoiceSttProvider.None, viewModel.uiState.value.voice.secondarySttProvider)
            assertFalse(viewModel.uiState.value.voice.commandRecordingEnabled)
            assertEquals(100, viewModel.uiState.value.voice.commandRecordingRetention)
        }

    @Test
    fun `voice response mode propagates to uiState`() =
        runTest {
            val voice = FakeVoiceSettings(responseModeFlow = MutableStateFlow("visual"))
            val viewModel = buildViewModel(voice = voice)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(VoiceResponseMode.Visual, viewModel.uiState.value.voice.responseMode)
        }

    @Test
    fun `setVoiceAssistProvider delegates to voice settings`() =
        runTest {
            val voice = FakeVoiceSettings()
            val viewModel = buildViewModel(voice = voice)

            viewModel.setVoiceAssistProvider(VoiceSttProvider.Moonshine)
            advanceUntilIdle()

            assertEquals(VoiceSttProvider.Moonshine.key, voice.lastAssistProvider)
        }

    @Test
    fun `set stt providers delegates to voice settings`() =
        runTest {
            val voice = FakeVoiceSettings()
            val viewModel = buildViewModel(voice = voice)

            viewModel.setPrimarySttProvider(VoiceSttProvider.Whisper)
            viewModel.setSecondarySttProvider(VoiceSttProvider.HaAssist)
            advanceUntilIdle()

            assertEquals("whisper", voice.lastPrimarySttProvider)
            assertEquals("ha_assist", voice.lastSecondarySttProvider)
        }

    @Test
    fun `selected voice model ids propagate to uiState`() =
        runTest {
            val voice =
                FakeVoiceSettings(
                    selectedSttModelIdFlow = MutableStateFlow("moonshine-tiny-en"),
                    selectedIntentEmbeddingModelIdFlow = MutableStateFlow("intent-embedding-none"),
                )
            val viewModel = buildViewModel(voice = voice)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals("moonshine-tiny-en", viewModel.uiState.value.voice.selectedSttModelId)
            assertEquals("intent-embedding-none", viewModel.uiState.value.voice.selectedIntentEmbeddingModelId)
        }

    @Test
    fun `local intent recognizer setting propagates to uiState`() =
        runTest {
            val voice = FakeVoiceSettings(localIntentRecognizerEnabledFlow = MutableStateFlow(true))
            val viewModel = buildViewModel(voice = voice)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.voice.localIntentRecognizerEnabled)
        }

    @Test
    fun `set selected voice models delegates to voice settings`() =
        runTest {
            val voice = FakeVoiceSettings()
            val viewModel = buildViewModel(voice = voice)

            viewModel.setSelectedSttModelId("moonshine-tiny-en")
            viewModel.setSelectedIntentEmbeddingModelId("intent-embedding-none")
            viewModel.setLocalIntentRecognizerEnabled(true)
            advanceUntilIdle()

            assertEquals("moonshine-tiny-en", voice.lastSelectedSttModelId)
            assertEquals("intent-embedding-none", voice.lastSelectedIntentEmbeddingModelId)
            assertEquals(true, voice.lastLocalIntentRecognizerEnabled)
        }

    @Test
    fun `download voice model delegates through actions`() =
        runTest {
            val actions = RecordingSettingsExternalActions()
            val viewModel = buildViewModel(actions = actions)

            viewModel.downloadVoiceModel("moonshine-base-en")
            advanceUntilIdle()

            assertEquals(listOf("moonshine-base-en"), actions.downloadedModelIds)
        }

    @Test
    fun `delete voice model delegates through actions`() =
        runTest {
            val actions = RecordingSettingsExternalActions()
            val viewModel = buildViewModel(actions = actions)

            viewModel.deleteVoiceModel("moonshine-base-en")
            advanceUntilIdle()

            assertEquals(listOf("moonshine-base-en"), actions.deletedModelIds)
        }

    @Test
    fun `setVoiceResponseMode delegates to voice settings`() =
        runTest {
            val voice = FakeVoiceSettings()
            val viewModel = buildViewModel(voice = voice)

            viewModel.setVoiceResponseMode(VoiceResponseMode.Silent)
            advanceUntilIdle()

            assertEquals("silent", voice.lastResponseMode)
        }

    @Test
    fun `sidebar settings propagate to uiState`() =
        runTest {
            val sidebar =
                FakeSidebarSettings(
                    positionFlow = MutableStateFlow(SidebarPosition.Top),
                    pinnedFlow = MutableStateFlow(true),
                    shortcutsFlow = MutableStateFlow(SidebarSettingsDefaults.shortcuts.take(2)),
                )
            sidebar.showLabelsValue.value = true
            val viewModel = buildViewModel(sidebar = sidebar)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(SidebarPosition.Top, viewModel.uiState.value.sidebar.position)
            assertEquals(true, viewModel.uiState.value.sidebar.pinned)
            assertEquals(true, viewModel.uiState.value.sidebar.showLabels)
            assertEquals(SidebarSettingsDefaults.shortcuts.take(2), viewModel.uiState.value.sidebar.shortcuts)
        }

    @Test
    fun `sidebar setters delegate to sidebar settings`() =
        runTest {
            val sidebar = FakeSidebarSettings()
            val viewModel = buildViewModel(sidebar = sidebar)

            viewModel.setSidebarPosition(SidebarPosition.Right)
            viewModel.setSidebarPinned(true)
            viewModel.setSidebarShortcuts(SidebarSettingsDefaults.shortcuts.take(1))
            advanceUntilIdle()

            assertEquals(SidebarPosition.Right, sidebar.lastPosition)
            assertEquals(true, sidebar.lastPinned)
            assertEquals(SidebarSettingsDefaults.shortcuts.take(1), sidebar.lastShortcuts)
        }

    @Test
    fun `setSidebarShowLabels delegates to sidebar settings`() =
        runTest {
            val sidebar = FakeSidebarSettings()
            val viewModel = buildViewModel(sidebar = sidebar)

            viewModel.setSidebarShowLabels(true)
            advanceUntilIdle()

            assertEquals(true, sidebar.lastShowLabels)
        }

    private class FakeKioskSettings(
        keepScreenOnFlow: MutableStateFlow<Boolean> = MutableStateFlow(true),
        startOnBootFlow: MutableStateFlow<Boolean> = MutableStateFlow(true),
        launchOnWakeFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        batteryOptPromptShownFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        esphomeEnabledFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        dashboardPathFlow: MutableStateFlow<String> = MutableStateFlow(""),
    ) : KioskSettings {
        override val keepScreenOn: Flow<Boolean> = keepScreenOnFlow.asStateFlow()
        override val startOnBoot: Flow<Boolean> = startOnBootFlow.asStateFlow()
        override val launchOnWake: Flow<Boolean> = launchOnWakeFlow.asStateFlow()
        override val batteryOptPromptShown: Flow<Boolean> = batteryOptPromptShownFlow.asStateFlow()
        override val esphomeEnabled: Flow<Boolean> = esphomeEnabledFlow.asStateFlow()
        override val dashboardPath: Flow<String> = dashboardPathFlow.asStateFlow()

        override suspend fun setKeepScreenOn(value: Boolean) = Unit

        override suspend fun setStartOnBoot(value: Boolean) = Unit

        override suspend fun setLaunchOnWake(value: Boolean) = Unit

        override suspend fun setBatteryOptPromptShown(value: Boolean) = Unit

        override suspend fun setEsphomeEnabled(value: Boolean) = Unit

        override suspend fun setDashboardPath(value: String) = Unit
    }

    private class FakeSidebarSettings(
        positionFlow: MutableStateFlow<SidebarPosition> = MutableStateFlow(SidebarPosition.Left),
        pinnedFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        shortcutsFlow: MutableStateFlow<List<SidebarShortcut>> = MutableStateFlow(SidebarSettingsDefaults.shortcuts),
    ) : SidebarSettings {
        val showLabelsValue = MutableStateFlow(false)

        override val position: Flow<SidebarPosition> = positionFlow.asStateFlow()
        override val pinned: Flow<Boolean> = pinnedFlow.asStateFlow()
        override val showLabels: Flow<Boolean> = showLabelsValue
        override val shortcuts: Flow<List<SidebarShortcut>> = shortcutsFlow.asStateFlow()

        var lastPosition: SidebarPosition? = null
        var lastPinned: Boolean? = null
        var lastShowLabels: Boolean? = null
        var lastShortcuts: List<SidebarShortcut>? = null

        override suspend fun setPosition(value: SidebarPosition) {
            lastPosition = value
        }

        override suspend fun setPinned(value: Boolean) {
            lastPinned = value
        }

        override suspend fun setShowLabels(value: Boolean) {
            lastShowLabels = value
            showLabelsValue.value = value
        }

        override suspend fun setShortcuts(value: List<SidebarShortcut>) {
            lastShortcuts = value
        }
    }

    private class FakeVoiceSettings(
        enabledFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        activeWakeWordFlow: MutableStateFlow<String> = MutableStateFlow(WakeWordModel.DEFAULT_ID),
        assistProviderFlow: MutableStateFlow<String> = MutableStateFlow("ha_assist"),
        primarySttProviderFlow: MutableStateFlow<String> = MutableStateFlow("ha_assist"),
        secondarySttProviderFlow: MutableStateFlow<String> = MutableStateFlow("none"),
        selectedSttModelIdFlow: MutableStateFlow<String> = MutableStateFlow("moonshine-tiny-en"),
        selectedIntentEmbeddingModelIdFlow: MutableStateFlow<String> = MutableStateFlow("intent-embedding-none"),
        localIntentRecognizerEnabledFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        responseModeFlow: MutableStateFlow<String> = MutableStateFlow("speak"),
        commandRecordingEnabledFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        commandRecordingRetentionFlow: MutableStateFlow<Int> = MutableStateFlow(100),
        vadSilenceMsFlow: MutableStateFlow<Int> = MutableStateFlow(500),
    ) : VoiceSettings {
        override val enabled: Flow<Boolean> = enabledFlow.asStateFlow()
        override val activeWakeWord: Flow<String> = activeWakeWordFlow.asStateFlow()
        override val assistProvider: Flow<String> = assistProviderFlow.asStateFlow()
        override val primarySttProvider: Flow<String> = primarySttProviderFlow.asStateFlow()
        override val secondarySttProvider: Flow<String> = secondarySttProviderFlow.asStateFlow()
        override val selectedSttModelId: Flow<String> = selectedSttModelIdFlow.asStateFlow()
        override val selectedIntentEmbeddingModelId: Flow<String> =
            selectedIntentEmbeddingModelIdFlow.asStateFlow()
        override val localIntentRecognizerEnabled: Flow<Boolean> = localIntentRecognizerEnabledFlow.asStateFlow()
        override val responseMode: Flow<String> = responseModeFlow.asStateFlow()
        override val commandRecordingEnabled: Flow<Boolean> = commandRecordingEnabledFlow.asStateFlow()
        override val commandRecordingRetention: Flow<Int> = commandRecordingRetentionFlow.asStateFlow()
        override val vadSilenceMs: Flow<Int> = vadSilenceMsFlow.asStateFlow()

        var lastEnabled: Boolean? = null
        var lastAssistProvider: String? = null
        var lastPrimarySttProvider: String? = null
        var lastSecondarySttProvider: String? = null
        var lastSelectedSttModelId: String? = null
        var lastSelectedIntentEmbeddingModelId: String? = null
        var lastLocalIntentRecognizerEnabled: Boolean? = null
        var lastResponseMode: String? = null

        override suspend fun setEnabled(value: Boolean) {
            lastEnabled = value
        }

        override suspend fun setActiveWakeWord(value: String) = Unit

        override suspend fun setAssistProvider(value: String) {
            lastAssistProvider = value
        }

        override suspend fun setPrimarySttProvider(value: String) {
            lastPrimarySttProvider = value
        }

        override suspend fun setSecondarySttProvider(value: String) {
            lastSecondarySttProvider = value
        }

        override suspend fun setSelectedSttModelId(value: String) {
            lastSelectedSttModelId = value
        }

        override suspend fun setSelectedIntentEmbeddingModelId(value: String) {
            lastSelectedIntentEmbeddingModelId = value
        }

        override suspend fun setLocalIntentRecognizerEnabled(value: Boolean) {
            lastLocalIntentRecognizerEnabled = value
        }

        override suspend fun setResponseMode(value: String) {
            lastResponseMode = value
        }

        override suspend fun setCommandRecordingEnabled(value: Boolean) = Unit

        override suspend fun setCommandRecordingRetention(value: Int) = Unit

        override suspend fun setVadSilenceMs(value: Int) = Unit
    }

    private class FakeDoorbellSettings(
        enabledFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        doorbellsFlow: MutableStateFlow<List<DoorbellConfig>> = MutableStateFlow(emptyList()),
        autoCloseSecFlow: MutableStateFlow<Int> = MutableStateFlow(60),
    ) : DoorbellSettings {
        override val enabled: Flow<Boolean> = enabledFlow.asStateFlow()
        override val doorbells: Flow<List<DoorbellConfig>> = doorbellsFlow.asStateFlow()
        override val autoCloseSec: Flow<Int> = autoCloseSecFlow.asStateFlow()

        override suspend fun setEnabled(value: Boolean) = Unit

        override suspend fun setAutoCloseSec(value: Int) = Unit

        override suspend fun upsertDoorbell(config: DoorbellConfig) = Unit

        override suspend fun removeDoorbell(id: String) = Unit
    }

    private class FakeScreensaverSettings(
        dayModeFlow: MutableStateFlow<String> = MutableStateFlow("photos"),
        nightModeFlow: MutableStateFlow<String> = MutableStateFlow("black"),
        nightModeActiveFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        idleTimeoutSecFlow: MutableStateFlow<Int> = MutableStateFlow(300),
        overlayPositionFlow: MutableStateFlow<String> = MutableStateFlow("bottom_left"),
        pictureSpacingDpFlow: MutableStateFlow<Int> = MutableStateFlow(8),
        mediaLibraryOrderFlow: MutableStateFlow<String> = MutableStateFlow("shuffle"),
        mediaLibrarySourceIdFlow: MutableStateFlow<String?> = MutableStateFlow(null),
        mediaLibrarySourceTitleFlow: MutableStateFlow<String?> = MutableStateFlow(null),
        weatherEntityIdFlow: MutableStateFlow<String> = MutableStateFlow("weather.home"),
        calendarEntityIdFlow: MutableStateFlow<String> = MutableStateFlow(""),
        powerUsageEntityIdFlow: MutableStateFlow<String> = MutableStateFlow(""),
        solarPowerEntityIdFlow: MutableStateFlow<String> = MutableStateFlow(""),
        gridPowerEntityIdFlow: MutableStateFlow<String> = MutableStateFlow(""),
    ) : ScreensaverSettings {
        override val dayMode: Flow<String> = dayModeFlow.asStateFlow()
        override val nightMode: Flow<String> = nightModeFlow.asStateFlow()
        override val nightModeActive: Flow<Boolean> = nightModeActiveFlow.asStateFlow()
        override val idleTimeoutSec: Flow<Int> = idleTimeoutSecFlow.asStateFlow()
        override val overlayPosition: Flow<String> = overlayPositionFlow.asStateFlow()
        override val pictureSpacingDp: Flow<Int> = pictureSpacingDpFlow.asStateFlow()
        override val mediaLibraryOrder: Flow<String> = mediaLibraryOrderFlow.asStateFlow()
        override val mediaLibrarySourceId: Flow<String?> = mediaLibrarySourceIdFlow.asStateFlow()
        override val mediaLibrarySourceTitle: Flow<String?> = mediaLibrarySourceTitleFlow.asStateFlow()
        override val weatherEntityId: Flow<String> = weatherEntityIdFlow.asStateFlow()
        override val calendarEntityId: Flow<String> = calendarEntityIdFlow.asStateFlow()
        override val powerUsageEntityId: Flow<String> = powerUsageEntityIdFlow.asStateFlow()
        override val solarPowerEntityId: Flow<String> = solarPowerEntityIdFlow.asStateFlow()
        override val gridPowerEntityId: Flow<String> = gridPowerEntityIdFlow.asStateFlow()

        override suspend fun setDayMode(value: String) = Unit

        override suspend fun setNightMode(value: String) = Unit

        override suspend fun setNightModeActive(value: Boolean) = Unit

        override suspend fun setIdleTimeoutSec(value: Int) = Unit

        override suspend fun setOverlayPosition(value: String) = Unit

        override suspend fun setPictureSpacingDp(value: Int) = Unit

        override suspend fun setMediaLibraryOrder(value: String) = Unit

        override suspend fun setMediaLibrarySource(
            id: String?,
            title: String?,
        ) = Unit

        override suspend fun setWeatherEntityId(value: String) = Unit

        override suspend fun setCalendarEntityId(value: String) = Unit

        override suspend fun setPowerUsageEntityId(value: String) = Unit

        override suspend fun setSolarPowerEntityId(value: String) = Unit

        override suspend fun setGridPowerEntityId(value: String) = Unit
    }

    private class RecordingSettingsExternalActions : SettingsExternalActions {
        var haUrl: String? = null
        var immichUrl: String? = null
        var immichApiKey: String? = null
        var immichAlbum: String? = null
        val downloadedModelIds = mutableListOf<String>()
        val deletedModelIds = mutableListOf<String>()
        var clearCount = 0

        override suspend fun setHaUrl(value: String) {
            haUrl = value
        }

        override suspend fun setImmichUrl(value: String) {
            immichUrl = value
        }

        override suspend fun setImmichApiKey(value: String) {
            immichApiKey = value
        }

        override suspend fun setImmichAlbum(value: String) {
            immichAlbum = value
        }

        override suspend fun setImmichCatalogTtlHours(value: Int) = Unit

        override suspend fun refreshImmichCatalog(): RefreshImmichCatalogResult =
            RefreshImmichCatalogResult.Failure("not wired yet")

        override suspend fun downloadVoiceModel(modelId: String) {
            downloadedModelIds += modelId
        }

        override suspend fun deleteVoiceModel(modelId: String) {
            deletedModelIds += modelId
        }

        override suspend fun clearCommandRecordings() {
            clearCount += 1
        }
    }
}
