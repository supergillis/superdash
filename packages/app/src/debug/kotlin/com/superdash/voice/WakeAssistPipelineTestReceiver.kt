package com.superdash.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.superdash.SuperdashApp
import com.superdash.core.log.Log
import com.superdash.voice.pipeline.LocalSttRoute
import com.superdash.voice.pipeline.VoiceCaptureLoop
import com.superdash.voice.pipeline.VoiceFixtureMetadata
import com.superdash.voice.pipeline.VoiceProviderAttempt
import com.superdash.voice.pipeline.VoiceProviderAttemptResult
import com.superdash.voice.pipeline.VoiceProviderProvenance
import com.superdash.voice.pipeline.VoiceProviderSelection
import com.superdash.voice.pipeline.VoiceRunContext
import com.superdash.voice.pipeline.VoiceRunId
import com.superdash.voice.pipeline.VoiceRunResult
import com.superdash.voice.pipeline.VoiceState
import com.superdash.voice.pipeline.VoiceSttProvider
import com.superdash.voice.voiceProviderIdentityFor
import com.superdash.voice.wake.MicroWakeWordRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

private val wakeAssistLog = Log("WakeAssistTest")
private val debugVoiceJson = Json { prettyPrint = false }

private const val FRAME_SAMPLES = 160
private const val DEFAULT_WAKE_COMMAND_SILENCE_MS = 250
private const val TRAILING_SILENCE_MS = 1_500
private const val WAKE_ASSIST_TIMEOUT_MS = 60_000L

/** Debug-only: feed wake-word WAV + command WAV through VoiceCaptureLoop.
 *
 * Files are resolved relative to filesDir. Trigger through Just:
 *
 *     just send-wake-audio wake.wav command.wav ha_assist hey_jarvis 250
 */
class WakeAssistPipelineTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wakeName =
            intent.getStringExtra("wake_name") ?: run {
                wakeAssistLog.w("missing 'wake_name' extra")
                return
            }
        val commandName =
            intent.getStringExtra("command_name") ?: run {
                wakeAssistLog.w("missing 'command_name' extra")
                return
            }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runTest(
                    context = context,
                    wakeName = wakeName,
                    commandName = commandName,
                    fixtureName = intent.getStringExtra("fixture_name") ?: commandName,
                    fixtureSource = intent.getStringExtra("fixture_source") ?: "generated",
                    expectedText = intent.getStringExtra("expected_text").orEmpty(),
                    providerKey = intent.getStringExtra("provider")?.takeIf { it.isNotBlank() },
                    secondaryProviderKey = intent.getStringExtra("secondary_provider"),
                    wakeWord = intent.getStringExtra("word")?.takeIf { it.isNotBlank() },
                    wakeCommandSilenceMs =
                        intent
                            .getIntExtra("silence_ms", DEFAULT_WAKE_COMMAND_SILENCE_MS)
                            .coerceAtLeast(0),
                )
            } catch (t: Throwable) {
                wakeAssistLog.w("wake assist test failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun runTest(
        context: Context,
        wakeName: String,
        commandName: String,
        fixtureName: String,
        fixtureSource: String,
        expectedText: String,
        providerKey: String?,
        secondaryProviderKey: String?,
        wakeWord: String?,
        wakeCommandSilenceMs: Int,
    ) {
        val app = context.applicationContext as SuperdashApp
        val voiceSettings = app.graph.voiceSettings
        val originalPrimaryProvider = voiceSettings.primarySttProvider.first()
        val originalSecondaryProvider = voiceSettings.secondarySttProvider.first()
        val originalWakeWord = voiceSettings.activeWakeWord.first()
        val shouldForceProviders = providerKey != null || secondaryProviderKey != null
        var selectedPrimaryProvider = originalPrimaryProvider
        var selectedSecondaryProvider = originalSecondaryProvider
        if (shouldForceProviders) {
            val provider =
                if (providerKey != null) {
                    parseDebugSttProvider(providerKey).getOrElse { throwable ->
                        wakeAssistLog.w("rejected debug STT provider", throwable, "key" to providerKey)
                        return
                    }
                } else {
                    VoiceSttProvider.fromKey(originalPrimaryProvider)
                }
            val secondaryProvider =
                parseDebugSecondarySttProvider(secondaryProviderKey).getOrElse { throwable ->
                    wakeAssistLog.w("rejected debug secondary STT provider", throwable, "key" to secondaryProviderKey)
                    return
                }
            selectedPrimaryProvider = provider.key
            selectedSecondaryProvider = secondaryProvider.key
            voiceSettings.setPrimarySttProvider(provider.key)
            voiceSettings.setSecondarySttProvider(secondaryProvider.key)
            wakeAssistLog.i(
                "forced STT providers",
                "primary" to provider.key,
                "secondary" to secondaryProvider.key,
                "debugProvider" to providerKey,
            )
        }
        val selectedWakeWord = wakeWord ?: originalWakeWord
        voiceSettings.setActiveWakeWord(selectedWakeWord)
        wakeAssistLog.i("forced wake word", "wakeWord" to selectedWakeWord)
        try {
            runInjectedAudio(
                context = context,
                app = app,
                wakeName = wakeName,
                commandName = commandName,
                fixtureName = fixtureName,
                fixtureSource = fixtureSource,
                expectedText = expectedText,
                providerKey = providerKey,
                selectedPrimaryProvider = selectedPrimaryProvider,
                selectedSecondaryProvider = selectedSecondaryProvider,
                selectedWakeWord = selectedWakeWord,
                wakeCommandSilenceMs = wakeCommandSilenceMs,
            )
        } finally {
            voiceSettings.setPrimarySttProvider(originalPrimaryProvider)
            voiceSettings.setSecondarySttProvider(originalSecondaryProvider)
            voiceSettings.setActiveWakeWord(originalWakeWord)
            wakeAssistLog.i(
                "restored voice settings",
                "primary" to originalPrimaryProvider,
                "secondary" to originalSecondaryProvider,
                "wakeWord" to originalWakeWord,
            )
        }
    }

    private suspend fun runInjectedAudio(
        context: Context,
        app: SuperdashApp,
        wakeName: String,
        commandName: String,
        fixtureName: String,
        fixtureSource: String,
        expectedText: String,
        providerKey: String?,
        selectedPrimaryProvider: String,
        selectedSecondaryProvider: String,
        selectedWakeWord: String,
        wakeCommandSilenceMs: Int,
    ) {
        val wakeSamples = readWav16kMonoPcm16(File(context.filesDir, wakeName)) ?: return
        val commandSamples = readWav16kMonoPcm16(File(context.filesDir, commandName)) ?: return
        val samples =
            wakeSamples +
                silenceSamples(wakeCommandSilenceMs) +
                commandSamples +
                silenceSamples(TRAILING_SILENCE_MS)
        val emittedFrames = AtomicInteger(0)
        val startedAt = SystemClock.elapsedRealtime()
        val coordinator = app.graph.voiceCoordinator
        val scope = CoroutineScope(Dispatchers.Default)
        var activeRunContext: VoiceRunContext? = null
        val loop =
            VoiceCaptureLoop(
                source = { samples.asTimedFrames(emittedFrames) },
                activeWakeWord = flow { emit(selectedWakeWord) },
                vadSilenceMs = app.graph.voiceSettings.vadSilenceMs,
                coordinator = coordinator,
                runnerFactory = { word ->
                    try {
                        MicroWakeWordRunner(context = app, wakeWord = word)
                    } catch (throwable: Throwable) {
                        wakeAssistLog.w("failed to load wake-word model", throwable, "word" to word)
                        null
                    }
                },
                createRunContext = { event ->
                    val selectedModelId =
                        app
                            .graph
                            .voiceSettings
                            .selectedSttModelId
                            .first()
                    val modelState =
                        app
                            .graph
                            .voiceModels
                            .state
                            .first()
                    VoiceRunContext(
                        id = VoiceRunId.new(),
                        wakeWord = event.word,
                        startedAtEpochMs = System.currentTimeMillis(),
                        providerSelection =
                            VoiceProviderSelection(
                                primary =
                                    voiceProviderIdentityFor(
                                        selectedPrimaryProvider,
                                        selectedModelId,
                                        modelState,
                                    ),
                                secondary =
                                    VoiceSttProvider
                                        .fromKey(selectedSecondaryProvider)
                                        .takeIf { provider -> provider != VoiceSttProvider.None }
                                        ?.let { provider ->
                                            voiceProviderIdentityFor(
                                                provider.key,
                                                selectedModelId,
                                                modelState,
                                            )
                                        },
                            ),
                        fixture =
                            VoiceFixtureMetadata(
                                source = fixtureSource,
                                name = fixtureName,
                                expectedText = expectedText.ifBlank { null },
                            ),
                    ).also { runContext ->
                        activeRunContext = runContext
                    }
                },
                commandAudioTransform = { runContext, audio ->
                    app
                        .graph
                        .voice
                        .recordingComponent
                        .transformCommandAudio(runContext, audio)
                },
            )
        wakeAssistLog.i(
            "starting wake assist audio test",
            "wakeSamples" to wakeSamples.size,
            "commandSamples" to commandSamples.size,
            "silenceMs" to wakeCommandSilenceMs,
            "provider" to (providerKey ?: "<current>"),
            "secondary" to selectedSecondaryProvider,
            "fixture" to fixtureName,
            "source" to fixtureSource,
            "wakeWord" to selectedWakeWord,
        )
        var finalTranscript: String? = null
        val stateJob =
            scope.launch {
                coordinator.state.collect { state ->
                    when (state) {
                        is VoiceState.ActionComplete -> {
                            finalTranscript = state.transcript
                        }
                        is VoiceState.Speaking -> {
                            finalTranscript = state.transcript
                        }
                        else -> {
                        }
                    }
                    wakeAssistLog.i(
                        "state",
                        "state" to state.javaClass.simpleName,
                        "elapsedMs" to (SystemClock.elapsedRealtime() - startedAt),
                    )
                }
            }
        val loopJob = scope.launch { loop.run() }
        val completed =
            withTimeoutOrNull(WAKE_ASSIST_TIMEOUT_MS) {
                coordinator.state.first { state ->
                    state is VoiceState.ActionComplete ||
                        state is VoiceState.Speaking ||
                        state is VoiceState.Failed
                }
                true
            } ?: false
        val finalRunResult =
            activeRunContext?.let { runContext ->
                withTimeoutOrNull(2_000L) {
                    coordinator.runResults.first { result -> result.context.id == runContext.id }
                } ?: coordinator
                    .runResults
                    .replayCache
                    .lastOrNull { result -> result.context.id == runContext.id }
            }
        loopJob.cancelAndJoin()
        stateJob.cancelAndJoin()
        val fallbackUsed = finalRunResult?.localProviderFallbackUsed() ?: false
        val benchmarkTranscript = finalRunResult?.transcript ?: finalTranscript
        val benchmarkFinalState =
            finalRunResult?.terminalState?.javaClass?.simpleName
                ?: coordinator.state.value.javaClass.simpleName
        val score =
            if (expectedText.isNotBlank() && benchmarkTranscript != null) {
                scoreVoiceCommand(expectedText, benchmarkTranscript)
            } else {
                null
            }
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        val benchmarkResult =
            DebugVoiceBenchmarkResult(
                runId = finalRunResult?.context?.id?.value ?: activeRunContext?.id?.value.orEmpty(),
                provider = selectedPrimaryProvider,
                primaryModelId =
                    finalRunResult
                        ?.context
                        ?.providerSelection
                        ?.primary
                        ?.modelId
                        ?: activeRunContext?.providerSelection?.primary?.modelId,
                secondaryProvider =
                    selectedSecondaryProvider.takeIf { provider ->
                        provider != VoiceSttProvider.None.key
                    },
                transcript = benchmarkTranscript,
                expected = expectedText.ifBlank { null },
                matched = score?.matches,
                completed = completed,
                elapsedMs = elapsedMs,
                providerTrace = finalRunResult?.providerTrace.orEmpty().map { attempt -> attempt.toDebugAttempt() },
            )
        wakeAssistLog.i(
            "wake assist audio test finished",
            "completed" to completed,
            "frames" to emittedFrames.get(),
            "elapsedMs" to elapsedMs,
            "finalState" to benchmarkFinalState,
        )
        wakeAssistLog.i(
            "benchmark result",
            "provider" to selectedPrimaryProvider,
            "secondary" to selectedSecondaryProvider,
            "source" to fixtureSource,
            "fixture" to fixtureName,
            "expected" to expectedText,
            "transcript" to benchmarkTranscript,
            "expectedNormalized" to score?.expectedNormalized,
            "actualNormalized" to score?.actualNormalized,
            "matched" to score?.matches,
            "completed" to completed,
            "fallbackUsed" to fallbackUsed,
            "elapsedMs" to elapsedMs,
            "finalState" to benchmarkFinalState,
            "json" to debugVoiceJson.encodeToString(benchmarkResult),
        )
    }

    private fun ShortArray.asTimedFrames(emittedFrames: AtomicInteger) =
        flow {
            var offset = 0
            while (offset + FRAME_SAMPLES <= size) {
                emit(copyOfRange(offset, offset + FRAME_SAMPLES))
                emittedFrames.incrementAndGet()
                delay(10L)
                offset += FRAME_SAMPLES
            }
        }

    private fun silenceSamples(durationMs: Int): ShortArray = ShortArray(durationMs * 16)

    private fun readWav16kMonoPcm16(file: File): ShortArray? {
        if (!file.exists()) {
            wakeAssistLog.w("file not found", null, "path" to file.absolutePath)
            return null
        }
        val bytes = file.readBytes()
        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") {
            wakeAssistLog.w("not a WAV", null, "path" to file.path)
            return null
        }
        var position = 12
        var dataOffset = -1
        var dataSize = 0
        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        while (position + 8 <= bytes.size) {
            val id = String(bytes, position, 4)
            val size = ByteBuffer.wrap(bytes, position + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            when (id) {
                "fmt " -> {
                    channels =
                        ByteBuffer
                            .wrap(bytes, position + 10, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short
                            .toInt()
                    sampleRate = ByteBuffer.wrap(bytes, position + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    bitsPerSample =
                        ByteBuffer
                            .wrap(bytes, position + 22, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short
                            .toInt()
                }
                "data" -> {
                    dataOffset = position + 8
                    dataSize = size
                }
            }
            position += 8 + size
        }
        if (sampleRate != 16000 || channels != 1 || bitsPerSample != 16 || dataOffset < 0) {
            wakeAssistLog.w(
                "WAV format not 16kHz/mono/PCM-16",
                null,
                "sampleRate" to sampleRate,
                "channels" to channels,
                "bitsPerSample" to bitsPerSample,
            )
            return null
        }
        val sampleCount = dataSize / 2
        val out = ShortArray(sampleCount)
        val buffer = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            out[i] = buffer.short
        }
        wakeAssistLog.i(
            "loaded WAV",
            "path" to file.absolutePath,
            "samples" to sampleCount,
            "durationMs" to sampleCount * 1000 / 16000,
        )
        return out
    }
}

private fun VoiceRunResult.localProviderFallbackUsed(): Boolean {
    val primary = context.providerSelection.primary
    if (primary.providerKey == VoiceSttProvider.HaAssist.key) {
        return false
    }
    val primaryAttempt = providerTrace.firstOrNull { attempt -> attempt.identity.stableKey == primary.stableKey }
    val localSttProvenance =
        primaryAttempt
            ?.provenance
            ?.filterIsInstance<VoiceProviderProvenance.LocalStt>()
            ?.lastOrNull()
    if (localSttProvenance?.route == LocalSttRoute.HaAudio) {
        return true
    }
    if (providerTrace.size > 1) {
        return true
    }
    return primaryAttempt?.result is VoiceProviderAttemptResult.Failed ||
        primaryAttempt?.result is VoiceProviderAttemptResult.Skipped
}

private fun VoiceProviderAttempt.toDebugAttempt(): DebugVoiceProviderAttempt =
    DebugVoiceProviderAttempt(
        provider = identity.providerKey,
        modelId = identity.modelId,
        elapsedMs = elapsedMs,
        result = result.debugName(),
    )

private fun VoiceProviderAttemptResult.debugName(): String =
    when (this) {
        is VoiceProviderAttemptResult.Completed -> {
            "completed"
        }
        is VoiceProviderAttemptResult.Failed -> {
            "failed"
        }
        is VoiceProviderAttemptResult.Skipped -> {
            "skipped"
        }
    }
