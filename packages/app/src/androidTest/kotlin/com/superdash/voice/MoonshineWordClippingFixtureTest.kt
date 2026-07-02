package com.superdash.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.executors.TtsPlay
import com.superdash.voice.action.recognizedWordsFromText
import com.superdash.voice.pipeline.VoiceCaptureLoop
import com.superdash.voice.pipeline.VoicePipelineCoordinator
import com.superdash.voice.pipeline.VoiceProviderIdentity
import com.superdash.voice.pipeline.VoiceProviderRunEvent
import com.superdash.voice.pipeline.VoiceProviderRunner
import com.superdash.voice.pipeline.VoiceProviderSelection
import com.superdash.voice.pipeline.VoiceRunContext
import com.superdash.voice.pipeline.VoiceRunId
import com.superdash.voice.pipeline.VoiceState
import com.superdash.voice.stt.RecognitionUpdate
import com.superdash.voice.stt.UnavailableLocalSttEngine
import com.superdash.voice.stt.engines.MoonshineBatchSttEngine
import com.superdash.voice.wake.MicroWakeWordRunner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class MoonshineWordClippingFixtureTest {
    @Test
    fun moonshineTranscribesFullCommandAfterWake() =
        runBlocking {
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            val testContext = InstrumentationRegistry.getInstrumentation().context

            val engine = MoonshineBatchSttEngine.createOrUnavailable(targetContext)
            assumeTrue(
                "Moonshine model not available — install with just fetch-moonshine",
                engine !is UnavailableLocalSttEngine,
            )

            val wakeSamples =
                testContext.assets
                    .open("voice/commands/$WAKE_FIXTURE")
                    .use(::readWav16kMonoPcm16)
            val commandSamples =
                testContext.assets
                    .open("voice/commands/$COMMAND_FIXTURE")
                    .use(::readWav16kMonoPcm16)
            val samples =
                wakeSamples +
                    silenceSamples(WAKE_COMMAND_SILENCE_MS) +
                    commandSamples +
                    silenceSamples(TRAILING_SILENCE_MS)

            val transcriptDeferred = CompletableDeferred<String>()
            val emittedFrames = AtomicInteger(0)
            val wakeEventDeferred = CompletableDeferred<KioskEvent.WakeWordDetected>()
            val bus = KioskEventBus()

            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner =
                        VoiceProviderRunner { _, audio ->
                            flow {
                                val recognitionUpdates = engine.recognize(audio).toList()
                                val finalUpdate =
                                    recognitionUpdates.filterIsInstance<RecognitionUpdate.Final>().lastOrNull()
                                val transcript = finalUpdate?.text.orEmpty()
                                transcriptDeferred.complete(transcript)
                                if (finalUpdate != null) {
                                    emit(VoiceProviderRunEvent.Action(VoiceActionEvent.Recognition(finalUpdate)))
                                } else {
                                    emit(
                                        VoiceProviderRunEvent.Action(
                                            VoiceActionEvent.Recognition(
                                                RecognitionUpdate.Final(words = recognizedWordsFromText("")),
                                            ),
                                        ),
                                    )
                                }
                                emit(
                                    VoiceProviderRunEvent.Action(
                                        VoiceActionEvent.ActionComplete(response = buildJsonObject {}),
                                    ),
                                )
                                kotlinx.coroutines.awaitCancellation()
                            }
                        },
                    ttsPlayer = NoopTts,
                    bus = bus,
                    dispatcher = Dispatchers.Default,
                    assistRunTimeoutMs = 30_000L,
                )
            val loop =
                VoiceCaptureLoop(
                    source = { samples.asTimedFrames(emittedFrames) },
                    activeWakeWord = flow { emit(WAKE_WORD_ID) },
                    vadSilenceMs = flow { emit(VAD_SILENCE_MS) },
                    coordinator = coordinator,
                    runnerFactory = { word -> MicroWakeWordRunner(targetContext, word) },
                    createRunContext = { event -> testRunContext(event.word) },
                )
            val scope = CoroutineScope(Dispatchers.Default)
            val wakeFrameJob =
                scope.launch {
                    wakeEventDeferred.complete(
                        bus.events.filterIsInstance<KioskEvent.WakeWordDetected>().first(),
                    )
                }
            val loopJob = scope.launch { loop.run() }
            val transcript: String
            try {
                withTimeout(30_000L) {
                    wakeEventDeferred.await()
                }
                withTimeout(30_000L) {
                    coordinator.state.first { it is VoiceState.ActionComplete || it is VoiceState.Failed }
                }
                transcript =
                    withTimeout(5_000L) {
                        transcriptDeferred.await()
                    }
            } finally {
                coordinator.stopAll()
                wakeFrameJob.cancelAndJoin()
                loopJob.cancelAndJoin()
            }

            val normalized = transcript.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
            val expectedWords = listOf("turn", "off", "desk", "lights")
            val containsAllInOrder = containsWordsInOrder(normalized, expectedWords)
            assertTrue(
                "Expected Moonshine transcript to contain \"Turn off desk lights\" (all four words, in order)" +
                    " but got \"$transcript\" (normalized=\"$normalized\")." +
                    " This documents the word-clipping bug where the first word(s) after wake are lost.",
                containsAllInOrder,
            )
        }

    private fun containsWordsInOrder(
        normalized: String,
        expected: List<String>,
    ): Boolean {
        val tokens = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        var index = 0
        for (token in tokens) {
            if (index < expected.size && token == expected[index]) {
                index += 1
            }
        }
        return index == expected.size
    }

    private fun testRunContext(wakeWord: String): VoiceRunContext =
        VoiceRunContext(
            id = VoiceRunId.new(),
            wakeWord = wakeWord,
            startedAtEpochMs = 1_000L,
            providerSelection =
                VoiceProviderSelection(
                    primary = VoiceProviderIdentity("android-test-moonshine", null),
                    secondary = null,
                ),
        )

    private object NoopTts : TtsPlay {
        override suspend fun play(url: String) {}

        override fun stop() {}
    }

    private companion object {
        private const val FRAME_SAMPLES = 160
        private const val TRAILING_SILENCE_FRAMES = 80
        private const val WAKE_FIXTURE = "wakeword_hey_jarvis.wav"
        private const val COMMAND_FIXTURE = "turn_off_desk_lights.wav"
        private const val WAKE_WORD_ID = "hey_jarvis"
        private const val WAKE_COMMAND_SILENCE_MS = 250
        private const val TRAILING_SILENCE_MS = 1_500
        private const val VAD_SILENCE_MS = 250

        private fun silenceSamples(durationMs: Int): ShortArray = ShortArray(durationMs * 16)

        private fun ShortArray.asTimedFrames(emittedFrames: AtomicInteger): Flow<ShortArray> =
            flow {
                var offset = 0
                while (offset + FRAME_SAMPLES <= size) {
                    emit(copyOfRange(offset, offset + FRAME_SAMPLES))
                    emittedFrames.incrementAndGet()
                    kotlinx.coroutines.delay(10L)
                    offset += FRAME_SAMPLES
                }
                repeat(TRAILING_SILENCE_FRAMES) {
                    emit(ShortArray(FRAME_SAMPLES))
                    emittedFrames.incrementAndGet()
                    kotlinx.coroutines.delay(10L)
                }
            }

        private fun readWav16kMonoPcm16(input: InputStream): ShortArray {
            val bytes = input.readBytes()
            require(bytes.size >= 44) { "WAV too short" }
            require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Not a WAV" }
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
            require(sampleRate == 16000 && channels == 1 && bitsPerSample == 16 && dataOffset >= 0) {
                "WAV must be 16kHz/mono/PCM-16, got rate=$sampleRate ch=$channels bits=$bitsPerSample"
            }
            val sampleCount = dataSize / 2
            val out = ShortArray(sampleCount)
            val buffer = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (sampleIdx in 0 until sampleCount) {
                out[sampleIdx] = buffer.short
            }
            return out
        }
    }
}
