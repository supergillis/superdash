package com.superdash.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.superdash.SuperdashApp
import com.superdash.core.log.Log
import com.superdash.ha.AssistAudioOptions
import com.superdash.voice.action.toVoiceActionEvent
import com.superdash.voice.audio.vadGated
import com.superdash.voice.pipeline.VoiceProviderSelection
import com.superdash.voice.pipeline.VoiceRunContext
import com.superdash.voice.pipeline.VoiceRunId
import com.superdash.voice.pipeline.VoiceSttProvider
import com.superdash.voice.voiceProviderIdentityFor
import com.superdash.voice.wake.WakeWordModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

private val log = Log("AssistTest")

private const val FRAME_SAMPLES = 160 // 10ms @ 16kHz
private const val DEFAULT_VAD_SILENCE_MS = 500
private const val PIPELINE_DRAIN_MS = 15_000L

// Append silence after the WAV exhausts so the test exercises the infinite-stream
// case (real mic never ends). vadGated should terminate the flow on its own once
// the VAD detects trailing silence; if it doesn't, the test runs the full tail.
private const val SILENCE_TAIL_MS = 5_000

/** Debug-only: drive the full assist pipeline (coordinator + HaAssistClient + HA WS)
 *  with a WAV instead of the mic. Trigger:
 *
 *      adb push cmd.wav /data/local/tmp/cmd.wav
 *      adb shell run-as com.superdash cp /data/local/tmp/cmd.wav files/cmd.wav
 *      adb shell am broadcast \
 *        -a com.superdash.DEBUG_ASSIST_TEST \
 *        --es name cmd.wav \
 *        -p com.superdash
 *
 *  The `name` extra is resolved relative to the app's internal filesDir, which
 *  sidesteps Android 11+ scoped-storage restrictions on /sdcard.
 *  WAV must be 16kHz mono PCM-16. Skips wake-word detection and fakes a wake event
 *  and feeds the WAV samples through the same vadGated → onWake path VoiceService
 *  uses, including the same `vad.close()`-after-`onWake-returns` lifecycle. */
class AssistPipelineTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name =
            intent.getStringExtra("name") ?: run {
                log.w("missing 'name' extra")
                return
            }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runTest(
                    context = context,
                    name = name,
                    providerKey = intent.getStringExtra("provider")?.takeIf { it.isNotBlank() },
                    secondaryProviderKey = intent.getStringExtra("secondary_provider"),
                    useVad = intent.getBooleanExtra("use_vad", true),
                    frameSamples = intent.getIntExtra("frame_samples", FRAME_SAMPLES).coerceIn(80, 3200),
                )
            } catch (t: Throwable) {
                log.w("assist pipeline test failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun runTest(
        context: Context,
        name: String,
        providerKey: String?,
        secondaryProviderKey: String?,
        useVad: Boolean,
        frameSamples: Int,
    ) {
        val file = File(context.filesDir, name)
        if (!file.exists()) {
            log.w("file not found", null, "path" to file.absolutePath)
            return
        }
        val samples = readWav16kMonoPcm16(file) ?: return
        log.i(
            "loaded WAV",
            "path" to file.absolutePath,
            "samples" to samples.size,
            "ms" to samples.size * 1000 / 16000,
        )

        val app = context.applicationContext as SuperdashApp
        if (providerKey == "raw_ha_assist") {
            runRawHaAssist(app, samples, frameSamples)
            return
        }
        val coordinator = app.graph.voiceCoordinator
        val originalPrimaryProvider =
            app
                .graph
                .voiceSettings
                .primarySttProvider
                .first()
        val originalSecondaryProvider =
            app
                .graph
                .voiceSettings
                .secondarySttProvider
                .first()
        val shouldForceProviders = providerKey != null || secondaryProviderKey != null
        if (shouldForceProviders) {
            val provider =
                if (providerKey != null) {
                    parseDebugSttProvider(providerKey).getOrElse { throwable ->
                        log.w("rejected debug STT provider", throwable, "key" to providerKey)
                        return
                    }
                } else {
                    VoiceSttProvider.fromKey(originalPrimaryProvider)
                }
            val secondaryProvider =
                parseDebugSecondarySttProvider(secondaryProviderKey).getOrElse { throwable ->
                    log.w("rejected debug secondary STT provider", throwable, "key" to secondaryProviderKey)
                    return
                }
            app
                .graph
                .voiceSettings
                .setPrimarySttProvider(provider.key)
            app
                .graph
                .voiceSettings
                .setSecondarySttProvider(secondaryProvider.key)
            log.i(
                "forced STT providers",
                "primary" to provider.key,
                "secondary" to secondaryProvider.key,
                "debugProvider" to providerKey,
            )
        }

        val audioFlow =
            flow {
                var offset = 0
                while (offset + frameSamples <= samples.size) {
                    emit(samples.copyOfRange(offset, offset + frameSamples))
                    offset += frameSamples
                    // Pace at real-time so VAD sample-based hysteresis behaves
                    // identically to mic input.
                    delay(frameSamples * 1000L / 16000L)
                }
                log.i("test audioFlow WAV exhausted", "frames" to samples.size / frameSamples)
                val silenceFrame = ShortArray(frameSamples)
                repeat(SILENCE_TAIL_MS * 16000 / 1000 / frameSamples) {
                    emit(silenceFrame)
                    delay(frameSamples * 1000L / 16000L)
                }
                log.i("test audioFlow silence-tail exhausted", "frameSamples" to frameSamples)
            }

        try {
            val startedAt = SystemClock.elapsedRealtime()
            coroutineScope {
                val stateJob =
                    launch {
                        coordinator.state.collect { state ->
                            log.i(
                                "voice e2e state",
                                "state" to state.javaClass.simpleName,
                                "elapsedMs" to elapsedSince(startedAt),
                            )
                        }
                    }

                log.i(
                    "voice e2e wake",
                    "elapsedMs" to elapsedSince(startedAt),
                    "vadSilenceMs" to DEFAULT_VAD_SILENCE_MS,
                    "useVad" to useVad,
                    "frameSamples" to frameSamples,
                )
                val selectedPrimaryProvider =
                    app
                        .graph
                        .voiceSettings
                        .primarySttProvider
                        .first()
                val selectedSecondaryProvider =
                    app
                        .graph
                        .voiceSettings
                        .secondarySttProvider
                        .first()
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
                coordinator.onWake(
                    VoiceRunContext(
                        id = VoiceRunId.new(),
                        wakeWord = WakeWordModel.DEFAULT_ID,
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
                    ),
                    if (useVad) {
                        vadGated(audioFlow, DEFAULT_VAD_SILENCE_MS)
                    } else {
                        audioFlow
                    },
                )

                // Coordinator's job runs on its own scope; keep this receiver alive long
                // enough for the assist run to play out so logs all land in one capture.
                delay(PIPELINE_DRAIN_MS)
                stateJob.cancelAndJoin()
                log.i("voice e2e drain complete", "elapsedMs" to elapsedSince(startedAt))
            }
        } finally {
            if (shouldForceProviders) {
                app
                    .graph
                    .voiceSettings
                    .setPrimarySttProvider(originalPrimaryProvider)
                app
                    .graph
                    .voiceSettings
                    .setSecondarySttProvider(originalSecondaryProvider)
                log.i(
                    "restored STT providers",
                    "primary" to originalPrimaryProvider,
                    "secondary" to originalSecondaryProvider,
                )
            }
        }
    }

    private fun elapsedSince(startedAt: Long): Long = SystemClock.elapsedRealtime() - startedAt

    private suspend fun runRawHaAssist(
        app: SuperdashApp,
        samples: ShortArray,
        frameSamples: Int,
    ) {
        val baseUrl =
            app
                .graph
                .haUrlFlow
                .value
                ?.trimEnd('/')
        if (baseUrl.isNullOrBlank()) {
            log.w("raw HA assist missing HA URL")
            return
        }
        val startedAt = SystemClock.elapsedRealtime()
        val audioFlow =
            flow {
                var offset = 0
                var frames = 0
                while (offset + frameSamples <= samples.size) {
                    emit(samples.copyOfRange(offset, offset + frameSamples))
                    offset += frameSamples
                    frames += 1
                    delay(frameSamples * 1000L / 16000L)
                }
                val silenceFrame = ShortArray(frameSamples)
                repeat(SILENCE_TAIL_MS * 16000 / 1000 / frameSamples) {
                    emit(silenceFrame)
                    frames += 1
                    delay(frameSamples * 1000L / 16000L)
                }
                log.i("raw HA shared-client audio complete", "elapsedMs" to elapsedSince(startedAt), "frames" to frames)
            }
        app
            .graph
            .assistClient
            .runPipeline(
                audio = audioFlow,
                options = AssistAudioOptions(noVad = true, finalSilenceMs = 0),
            ).map { it.toVoiceActionEvent() }
            .collect { event ->
                log.i(
                    "raw HA shared-client event",
                    "elapsedMs" to elapsedSince(startedAt),
                    "event" to event.javaClass.simpleName,
                )
            }
    }

    private fun readWav16kMonoPcm16(file: File): ShortArray? {
        val bytes = file.readBytes()
        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") {
            log.w("not a WAV", null, "path" to file.path)
            return null
        }
        var pos = 12
        var dataOffset = -1
        var dataSize = 0
        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = ByteBuffer.wrap(bytes, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            when (id) {
                "fmt " -> {
                    channels =
                        ByteBuffer
                            .wrap(bytes, pos + 10, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short
                            .toInt()
                    sampleRate = ByteBuffer.wrap(bytes, pos + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    bitsPerSample =
                        ByteBuffer
                            .wrap(bytes, pos + 22, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short
                            .toInt()
                }
                "data" -> {
                    dataOffset = pos + 8
                    dataSize = size
                }
            }
            pos += 8 + size
        }
        if (sampleRate != 16000 || channels != 1 || bitsPerSample != 16 || dataOffset < 0) {
            log.w(
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
        val bb = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            out[i] = bb.short
        }
        return out
    }
}
