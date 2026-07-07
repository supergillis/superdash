package com.superdash.voice

import android.app.Application
import com.superdash.core.log.Log
import com.superdash.ha.AssistAudioOptions
import com.superdash.kiosk.bus.KioskEventBus
import com.superdash.voice.action.LocalTranscriptActionFlow
import com.superdash.voice.action.TranscriptActionExecutor
import com.superdash.voice.action.VoiceActionProvider
import com.superdash.voice.action.executors.HaServiceCallExecutor
import com.superdash.voice.action.executors.HaTextActionExecutor
import com.superdash.voice.action.executors.HaTtsPlayer
import com.superdash.voice.action.retryingVoiceActionProvider
import com.superdash.voice.action.toVoiceActionEvent
import com.superdash.voice.audio.AudioRecordSource
import com.superdash.voice.intent.LocalIntentActionDispatcher
import com.superdash.voice.intent.LocalIntentRegistryRecognizer
import com.superdash.voice.intent.LocalIntentTranscriptActionExecutor
import com.superdash.voice.intent.UnsupportedSkillExecutor
import com.superdash.voice.intent.registry.LocalIntentRegistryCache
import com.superdash.voice.intent.registry.LocalIntentRegistryMetadata
import com.superdash.voice.models.VoiceModelIds
import com.superdash.voice.models.VoiceModelInstallStatus
import com.superdash.voice.models.VoiceModelState
import com.superdash.voice.pipeline.ResolvedVoiceProvider
import com.superdash.voice.pipeline.VoiceCaptureLoop
import com.superdash.voice.pipeline.VoicePipelineCoordinator
import com.superdash.voice.pipeline.VoiceProviderIdentity
import com.superdash.voice.pipeline.VoiceProviderRegistry
import com.superdash.voice.pipeline.VoiceProviderRunner
import com.superdash.voice.pipeline.VoiceProviderSelection
import com.superdash.voice.pipeline.VoiceProviderWithFallback
import com.superdash.voice.pipeline.VoiceResponseMode
import com.superdash.voice.pipeline.VoiceRunContext
import com.superdash.voice.pipeline.VoiceRunId
import com.superdash.voice.pipeline.VoiceSttProvider
import com.superdash.voice.recording.VoiceCommandRecordingRepository
import com.superdash.voice.recording.VoiceCommandRecordingService
import com.superdash.voice.recording.VoiceRecordingComponent
import com.superdash.voice.stt.LocalSttEngine
import com.superdash.voice.stt.engines.MoonshineBatchSttEngine
import com.superdash.voice.stt.engines.WhisperBatchSttEngine
import com.superdash.voice.wake.MicroWakeWordRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.io.File

private val voiceLog = Log("VoiceModule")
private const val HA_ASSIST_TRAILING_SILENCE_FRAME_SAMPLES = 160
private const val HA_ASSIST_TRAILING_SILENCE_FRAME_MS = 10L
private const val HA_ASSIST_TRAILING_SILENCE_MS = 700L
private const val HA_ASSIST_TRAILING_SILENCE_FRAMES =
    HA_ASSIST_TRAILING_SILENCE_MS / HA_ASSIST_TRAILING_SILENCE_FRAME_MS

/**
 * Self-contained voice pipeline: STT provider factory, HA-Assist trailing
 * silence shim, model-eviction wiring, local-intent dispatcher, capture
 * loop, recording component, and the coordinator that ties them together.
 *
 * The app composes a [HaVoiceCollaborator] and hands it in here; nothing
 * else inside this module needs to know how the rest of the app is wired.
 */
class VoiceModule(
    private val application: Application,
    private val voiceSettings: VoiceSettings,
    private val ha: HaVoiceCollaborator,
    private val voiceModelState: Flow<VoiceModelState>,
    eventBus: KioskEventBus,
    private val scope: CoroutineScope,
) {
    val ttsPlayer: HaTtsPlayer =
        HaTtsPlayer(
            context = application,
            tokenProvider = { ha.tokenProvider.get() },
            haBaseUrlProvider = { ha.haUrl.value },
        )

    private val dispatcher: LocalIntentActionDispatcher =
        LocalIntentActionDispatcher(
            haServiceCallExecutor = HaServiceCallExecutor { request -> ha.serviceCalls.callService(request) },
            skillExecutor = UnsupportedSkillExecutor(),
        )

    private val rawHaAudioProvider: VoiceActionProvider =
        { audio: Flow<ShortArray> ->
            flow {
                val responseMode = VoiceResponseMode.fromKey(voiceSettings.responseMode.first())
                ha.assistClient
                    .runPipeline(
                        audio = audio.withHaAssistTrailingSilence(),
                        options =
                            AssistAudioOptions(
                                noVad = true,
                                finalSilenceMs = 0,
                                endStage = responseMode.assistEndStage,
                            ),
                    ).map { it.toVoiceActionEvent() }
                    .collect { event -> emit(event) }
            }
        }

    private val haAudioProvider: VoiceActionProvider = retryingVoiceActionProvider(rawHaAudioProvider)

    private val haTextActionExecutor: TranscriptActionExecutor =
        HaTextActionExecutor(
            runTextPipeline = { text, endStage ->
                ha.assistClient.runTextPipeline(
                    text = text,
                    endStage = endStage,
                )
            },
            responseMode = {
                VoiceResponseMode.fromKey(voiceSettings.responseMode.first())
            },
        )

    private val localIntentRegistryCache: LocalIntentRegistryCache =
        LocalIntentRegistryCache(
            entitiesProvider = { ha.webSocketClient.entities.value },
            areasProvider = { ha.webSocketClient.areas.value },
            metadataProvider = {
                val voiceExposure = ha.webSocketClient.voiceExposure.value
                LocalIntentRegistryMetadata(
                    entityRegistry = ha.webSocketClient.entityRegistry.value,
                    deviceRegistry = ha.webSocketClient.deviceRegistry.value,
                    exposedEntityIds = voiceExposure.exposedEntityIds,
                    loaded = voiceExposure.loaded,
                )
            },
        )

    private val localIntentActionExecutor: TranscriptActionExecutor =
        LocalIntentTranscriptActionExecutor(
            enabled = { voiceSettings.localIntentRecognizerEnabled.first() },
            recognizer =
                LocalIntentRegistryRecognizer(
                    registryProvider = { localIntentRegistryCache.current() },
                ),
            dispatcher = dispatcher,
            fallbackExecutor = haTextActionExecutor,
        )

    private fun localTranscriptActionProvider(localStt: LocalSttEngine): VoiceActionProvider =
        LocalTranscriptActionFlow(
            localStt = localStt,
            transcriptActionExecutor = localIntentActionExecutor,
            audioActionProvider = haAudioProvider,
        )

    private val providerSelections =
        combine(
            voiceSettings.primarySttProvider,
            voiceSettings.secondarySttProvider,
            voiceSettings.selectedSttModelId,
            voiceModelState,
        ) { primaryKey, secondaryKey, selectedModelId, modelState ->
            val secondaryProvider = VoiceSttProvider.fromKey(secondaryKey)
            VoiceProviderSelection(
                primary = voiceProviderIdentityFor(primaryKey, selectedModelId, modelState),
                secondary =
                    secondaryProvider
                        .takeIf { provider -> provider != VoiceSttProvider.None }
                        ?.let { provider -> voiceProviderIdentityFor(provider.key, selectedModelId, modelState) },
            )
        }

    val providerRegistry: VoiceProviderRegistry =
        VoiceProviderRegistry(providerFactory = ::createProvider)

    init {
        // Evict cached Whisper and Moonshine resolved providers when the
        // selected STT model id changes. The cached engines hold ~50 MB of
        // native handles (loadedResource) and would otherwise leak per switch.
        // HA Assist has no per-model state.
        voiceSettings.selectedSttModelId
            .drop(1)
            .onEach { _ ->
                providerRegistry.evict(VoiceSttProvider.Moonshine.key)
                providerRegistry.evict(VoiceSttProvider.Whisper.key)
            }.launchIn(scope)
    }

    private fun createProvider(identity: VoiceProviderIdentity): ResolvedVoiceProvider? =
        when (identity.providerKey) {
            VoiceSttProvider.HaAssist.key -> {
                ResolvedVoiceProvider(identity = identity, provider = haAudioProvider)
            }
            VoiceSttProvider.Whisper.key -> {
                val engine = WhisperBatchSttEngine.createOrUnavailable(application)
                ResolvedVoiceProvider(
                    identity = identity,
                    provider = localTranscriptActionProvider(engine),
                    closeable = { engine.close() },
                )
            }
            VoiceSttProvider.Moonshine.key -> {
                val engine =
                    MoonshineBatchSttEngine.createOrUnavailable(
                        context = application,
                        selectedModelId = identity.modelId ?: VoiceModelIds.DEFAULT_STT_MODEL_ID,
                    )
                ResolvedVoiceProvider(
                    identity = identity,
                    provider = localTranscriptActionProvider(engine),
                    closeable = { engine.close() },
                )
            }
            else -> {
                null
            }
        }

    private val voiceProviderRunner: VoiceProviderRunner =
        VoiceProviderWithFallback(
            providerRegistry = providerRegistry,
        )

    val coordinator: VoicePipelineCoordinator =
        VoicePipelineCoordinator(
            voiceProviderRunner = voiceProviderRunner,
            ttsPlayer = ttsPlayer,
            bus = eventBus,
            responseModes = voiceSettings.responseMode.map { key -> VoiceResponseMode.fromKey(key) },
        )

    private val recordingRootDir = File(application.filesDir, "voice-recordings")

    val recordingRepository: VoiceCommandRecordingRepository =
        VoiceCommandRecordingRepository(
            rootDir = recordingRootDir,
            retentionCount = { voiceSettings.commandRecordingRetention.first() },
        )

    val recordingService: VoiceCommandRecordingService =
        VoiceCommandRecordingService(
            enabled = { voiceSettings.commandRecordingEnabled.first() },
            runResults = coordinator.runResults,
            currentClearGeneration = { recordingRepository.currentClearGeneration() },
            saveRecording = { recording, generation -> recordingRepository.save(recording, generation) },
            scope = scope,
        )

    val recordingComponent: VoiceRecordingComponent =
        VoiceRecordingComponent(
            service = recordingService,
            clearRecordings = { recordingRepository.clear() },
        )

    val captureLoop: VoiceCaptureLoop =
        VoiceCaptureLoop(
            source = { AudioRecordSource().frames() },
            activeWakeWord = voiceSettings.activeWakeWord,
            vadSilenceMs = voiceSettings.vadSilenceMs,
            coordinator = coordinator,
            runnerFactory = { wakeWord ->
                try {
                    MicroWakeWordRunner(
                        context = application,
                        wakeWord = wakeWord,
                    )
                } catch (throwable: Throwable) {
                    voiceLog.w("failed to load wake-word model", throwable, "word" to wakeWord)
                    null
                }
            },
            createRunContext = { event ->
                VoiceRunContext(
                    id = VoiceRunId.new(),
                    wakeWord = event.word,
                    startedAtEpochMs = System.currentTimeMillis(),
                    providerSelection = providerSelections.first(),
                    fixture = null,
                )
            },
            commandAudioTransform = { context, audio ->
                recordingComponent.transformCommandAudio(context, audio)
            },
        )
}

fun voiceProviderIdentityFor(
    providerKey: String,
    selectedModelId: String,
    modelState: VoiceModelState,
): VoiceProviderIdentity =
    if (providerKey == VoiceSttProvider.Moonshine.key) {
        val availableSelectedModel =
            modelState.models.any { model ->
                model.id == selectedModelId && model.status == VoiceModelInstallStatus.Available
            }
        val runnableModelId =
            if (availableSelectedModel) {
                selectedModelId
            } else {
                VoiceModelIds.DEFAULT_STT_MODEL_ID
            }
        VoiceProviderIdentity(providerKey = providerKey, modelId = runnableModelId)
    } else {
        VoiceProviderIdentity(providerKey = providerKey, modelId = null)
    }

private fun Flow<ShortArray>.withHaAssistTrailingSilence(): Flow<ShortArray> =
    flow {
        collect { samples ->
            emit(samples)
        }
        val silence = ShortArray(HA_ASSIST_TRAILING_SILENCE_FRAME_SAMPLES)
        repeat(HA_ASSIST_TRAILING_SILENCE_FRAMES.toInt()) {
            emit(silence)
            delay(HA_ASSIST_TRAILING_SILENCE_FRAME_MS)
        }
    }
