package com.superdash.voice

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.VoiceActionProvider
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
import com.superdash.voice.wake.MicroWakeWordRunner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class VoiceCommandWakeAssistAudioFixtureTest {
    @Test
    fun fullWakeWordCommandAudioTriggersAssistAction() =
        runBlocking {
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            val testContext = InstrumentationRegistry.getInstrumentation().context
            val fixtures = loadFixtures(testContext)
            assumeTrue(
                "Generate fixtures with scripts/voice-fixtures/generate-command-wavs.main.kts",
                fixtures.isNotEmpty(),
            )

            for (fixture in fixtures) {
                runFixture(targetContext, testContext, fixture)
            }
        }

    @Test
    fun longPauseAfterWakeTimesOutBeforeCommandAudio() =
        runBlocking {
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            val testContext = InstrumentationRegistry.getInstrumentation().context
            val fixture = loadFixtures(testContext).firstOrNull()
            assumeTrue(
                "Generate fixtures with scripts/voice-fixtures/generate-command-wavs.main.kts",
                fixture != null,
            )
            requireNotNull(fixture)

            val wakeWordSamples = testContext.assets.open("voice/commands/${fixture.wakeFileName}").use(::readWav16kMonoPcm16)
            val commandSamples = testContext.assets.open("voice/commands/${fixture.commandFileName}").use(::readWav16kMonoPcm16)
            val samples = wakeWordSamples + silenceSamples(6_000) + commandSamples
            val consumedFrames = mutableListOf<ShortArray>()
            val result =
                runUtterance(
                    targetContext = targetContext,
                    samples = samples,
                    wakeWordId = fixture.wakeWordId,
                    voiceActionProvider = { audio ->
                        flow {
                            audio.collect { consumedFrames += it }
                            emit(VoiceActionEvent.Error("stt-no-text-recognized", "no command before timeout"))
                        }
                    },
                )

            assertEquals(fixture.wakeWordId, result.wakeEvent.phrase)
            assertTrue(
                "Command audio should not be streamed after leading-silence timeout; frames=${consumedFrames.size}",
                consumedFrames.size < 300,
            )
            assertTrue("Expected Failed, got ${result.state}", result.state is VoiceState.Failed)
        }

    @Test
    fun normalPauseAfterWakeStillStreamsCommandAudio() =
        runBlocking {
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            val testContext = InstrumentationRegistry.getInstrumentation().context
            val fixture = loadFixtures(testContext).firstOrNull()
            assumeTrue(
                "Generate fixtures with scripts/voice-fixtures/generate-command-wavs.main.kts",
                fixture != null,
            )
            requireNotNull(fixture)

            val wakeWordSamples = testContext.assets.open("voice/commands/${fixture.wakeFileName}").use(::readWav16kMonoPcm16)
            val commandSamples = testContext.assets.open("voice/commands/${fixture.commandFileName}").use(::readWav16kMonoPcm16)
            val samples = wakeWordSamples + silenceSamples(1_500) + commandSamples
            val consumedFrames = mutableListOf<ShortArray>()
            val result =
                runUtterance(
                    targetContext = targetContext,
                    samples = samples,
                    wakeWordId = fixture.wakeWordId,
                    voiceActionProvider = { audio ->
                        flow {
                            audio.collect { consumedFrames += it }
                            emit(
                                VoiceActionEvent.Recognition(
                                    RecognitionUpdate.Final(words = recognizedWordsFromText(fixture.text)),
                                ),
                            )
                            emit(VoiceActionEvent.ActionComplete(response = buildJsonObject {}))
                            kotlinx.coroutines.awaitCancellation()
                        }
                    },
                )

            assertEquals(fixture.wakeWordId, result.wakeEvent.phrase)
            val commandFrameCount = commandSamples.size / FRAME_SAMPLES
            assertTrue(
                "Expected command frames after normal pause; frames=${consumedFrames.size} commandFrames=$commandFrameCount",
                consumedFrames.size > commandFrameCount / 2,
            )
            assertTrue("Expected ActionComplete, got ${result.state}", result.state is VoiceState.ActionComplete)
        }

    @Test
    fun longPauseMidSentenceExcludesAudioAfterPause() =
        runBlocking {
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            val testContext = InstrumentationRegistry.getInstrumentation().context
            val fixture = loadFixtures(testContext).firstOrNull()
            assumeTrue(
                "Generate fixtures with scripts/voice-fixtures/generate-command-wavs.main.kts",
                fixture != null,
            )
            requireNotNull(fixture)

            val wakeWordSamples = testContext.assets.open("voice/commands/${fixture.wakeFileName}").use(::readWav16kMonoPcm16)
            val commandSamples = testContext.assets.open("voice/commands/${fixture.commandFileName}").use(::readWav16kMonoPcm16)
            val samples =
                concatenateUtterance(wakeWordSamples, fixture.wakeCommandSilenceMs, commandSamples) +
                    silenceSamples(1_500) +
                    commandSamples
            val consumedFrames = mutableListOf<ShortArray>()
            val response =
                buildJsonObject {
                    put("action", JsonPrimitive(fixture.action))
                    put("target", JsonPrimitive(fixture.target))
                }
            val result =
                runUtterance(
                    targetContext = targetContext,
                    samples = samples,
                    wakeWordId = fixture.wakeWordId,
                    voiceActionProvider = { audio ->
                        flow {
                            audio.collect { consumedFrames += it }
                            emit(
                                VoiceActionEvent.Recognition(
                                    RecognitionUpdate.Final(words = recognizedWordsFromText(fixture.text)),
                                ),
                            )
                            emit(VoiceActionEvent.ActionComplete(response = response))
                            kotlinx.coroutines.awaitCancellation()
                        }
                    },
                )

            assertEquals(fixture.wakeWordId, result.wakeEvent.phrase)
            assertEquals(VoiceState.ActionComplete(fixture.text, response), result.state)
            assertTrue(
                "Audio after the long pause should be excluded; frames=${consumedFrames.size}",
                consumedFrames.size < commandSamples.size / FRAME_SAMPLES + 140,
            )
        }

    private suspend fun runFixture(
        targetContext: Context,
        testContext: Context,
        fixture: Fixture,
    ) {
        val wakeWordSamples = testContext.assets.open("voice/commands/${fixture.wakeFileName}").use(::readWav16kMonoPcm16)
        val commandSamples = testContext.assets.open("voice/commands/${fixture.commandFileName}").use(::readWav16kMonoPcm16)
        val samples = concatenateUtterance(wakeWordSamples, fixture.wakeCommandSilenceMs, commandSamples)
        val emittedFrames = AtomicInteger(0)
        val wakeEventDeferred = CompletableDeferred<KioskEvent.WakeWordDetected>()
        val wakeDetectedAtFrame = CompletableDeferred<Int>()
        var assistStartedAtFrame: Int? = null
        var firstAssistAudioAtFrame: Int? = null
        val bus = KioskEventBus()
        val response =
            buildJsonObject {
                put("action", JsonPrimitive(fixture.action))
                put("target", JsonPrimitive(fixture.target))
            }
        val coordinator =
            VoicePipelineCoordinator(
                voiceProviderRunner =
                    VoiceProviderRunner { _, audio ->
                        flow {
                            assistStartedAtFrame = emittedFrames.get()
                            audio.collect {
                                if (firstAssistAudioAtFrame == null) {
                                    firstAssistAudioAtFrame = emittedFrames.get()
                                }
                            }
                            emit(
                                VoiceProviderRunEvent.Action(
                                    VoiceActionEvent.Recognition(
                                        RecognitionUpdate.Final(words = recognizedWordsFromText(fixture.text)),
                                    ),
                                ),
                            )
                            emit(VoiceProviderRunEvent.Action(VoiceActionEvent.ActionComplete(response = response)))
                            kotlinx.coroutines.awaitCancellation()
                        }
                    },
                ttsPlayer = NoopTts,
                bus = bus,
                dispatcher = Dispatchers.Default,
                assistRunTimeoutMs = 20_000L,
            )
        val loop =
            VoiceCaptureLoop(
                source = { samples.asTimedFrames(emittedFrames) },
                activeWakeWord = flow { emit(fixture.wakeWordId) },
                vadSilenceMs = flow { emit(250) },
                coordinator = coordinator,
                runnerFactory = { wakeWord -> MicroWakeWordRunner(targetContext, wakeWord) },
                createRunContext = { event -> testRunContext(event.word) },
            )
        val scope = CoroutineScope(Dispatchers.Default)
        val wakeFrameJob =
            scope.launch {
                val wakeEvent = bus.events.filterIsInstance<KioskEvent.WakeWordDetected>().first()
                wakeEventDeferred.complete(wakeEvent)
                wakeDetectedAtFrame.complete(emittedFrames.get())
            }
        val loopJob = scope.launch { loop.run() }
        try {
            val wakeEvent =
                withTimeout(20_000L) {
                    wakeEventDeferred.await()
                }
            val actionComplete =
                withTimeout(20_000L) {
                    coordinator.state.filterIsInstance<VoiceState.ActionComplete>().first()
                }

            assertEquals(fixture.wakeWordId, wakeEvent.phrase)
            assertEquals(VoiceState.ActionComplete(fixture.text, response), actionComplete)
            val wakeFrame = wakeDetectedAtFrame.await()
            val startedFrame = requireNotNull(assistStartedAtFrame)
            val firstAudioFrame = requireNotNull(firstAssistAudioAtFrame)
            check(startedFrame - wakeFrame <= MAX_WAKE_TO_ASSIST_FRAMES) {
                "Fixture ${fixture.commandFileName} started Assist too late: " +
                    "wakeFrame=$wakeFrame assistFrame=$startedFrame"
            }
            check(firstAudioFrame >= startedFrame) {
                "Fixture ${fixture.commandFileName} streamed Assist audio before Assist started"
            }
            assertNotNull(
                "Fixture ${fixture.commandFileName} should start Assist after wake fires",
                assistStartedAtFrame,
            )
            assertNotNull(
                "Fixture ${fixture.commandFileName} should stream post-wake audio into Assist",
                firstAssistAudioAtFrame,
            )
        } finally {
            coordinator.stopAll()
            wakeFrameJob.cancelAndJoin()
            loopJob.cancelAndJoin()
        }
    }

    private suspend fun runUtterance(
        targetContext: Context,
        samples: ShortArray,
        wakeWordId: String,
        voiceActionProvider: VoiceActionProvider,
    ): UtteranceResult {
        val emittedFrames = AtomicInteger(0)
        val wakeEventDeferred = CompletableDeferred<KioskEvent.WakeWordDetected>()
        val bus = KioskEventBus()
        val coordinator =
            VoicePipelineCoordinator(
                voiceProviderRunner = voiceActionProvider.asRunner(),
                ttsPlayer = NoopTts,
                bus = bus,
                dispatcher = Dispatchers.Default,
                assistRunTimeoutMs = 20_000L,
            )
        val loop =
            VoiceCaptureLoop(
                source = { samples.asTimedFrames(emittedFrames) },
                activeWakeWord = flow { emit(wakeWordId) },
                vadSilenceMs = flow { emit(250) },
                coordinator = coordinator,
                runnerFactory = { wakeWord -> MicroWakeWordRunner(targetContext, wakeWord) },
                createRunContext = { event -> testRunContext(event.word) },
            )
        val scope = CoroutineScope(Dispatchers.Default)
        val wakeFrameJob =
            scope.launch {
                wakeEventDeferred.complete(bus.events.filterIsInstance<KioskEvent.WakeWordDetected>().first())
            }
        val loopJob = scope.launch { loop.run() }
        try {
            val wakeEvent =
                withTimeout(20_000L) {
                    wakeEventDeferred.await()
                }
            val terminalState =
                withTimeout(20_000L) {
                    coordinator.state.first { it is VoiceState.ActionComplete || it is VoiceState.Failed }
                }
            return UtteranceResult(wakeEvent, terminalState)
        } finally {
            coordinator.stopAll()
            wakeFrameJob.cancelAndJoin()
            loopJob.cancelAndJoin()
        }
    }

    private data class Fixture(
        val wakeFileName: String,
        val commandFileName: String,
        val wakeWord: String,
        val text: String,
        val action: String,
        val target: String,
        val wakeCommandSilenceMs: Int,
    ) {
        val wakeWordId: String = wakeWord.lowercase().replace(' ', '_')
    }

    private data class UtteranceResult(
        val wakeEvent: KioskEvent.WakeWordDetected,
        val state: VoiceState,
    )

    private fun VoiceActionProvider.asRunner(): VoiceProviderRunner =
        VoiceProviderRunner { _, audio ->
            flow {
                this@asRunner(audio).collect { event ->
                    emit(VoiceProviderRunEvent.Action(event))
                }
            }
        }

    private fun testRunContext(wakeWord: String): VoiceRunContext =
        VoiceRunContext(
            id = VoiceRunId.new(),
            wakeWord = wakeWord,
            startedAtEpochMs = 1_000L,
            providerSelection =
                VoiceProviderSelection(
                    primary = VoiceProviderIdentity("android-test", null),
                    secondary = null,
                ),
        )

    private object NoopTts : TtsPlay {
        override suspend fun play(url: String) {}

        override fun stop() {}
    }

    private companion object {
        private const val FRAME_SAMPLES = 160
        private const val MANIFEST_COLUMNS = 7
        private const val TRAILING_SILENCE_FRAMES = 80
        private const val MAX_WAKE_TO_ASSIST_FRAMES = 10

        private fun loadFixtures(context: Context): List<Fixture> {
            val manifest =
                runCatching {
                    context.assets
                        .open("voice/commands/manifest.tsv")
                        .bufferedReader()
                        .use { it.readText() }
                }.getOrNull()
                    ?: return emptyList()
            return manifest
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val columns = line.split('\t')
                    require(columns.size == MANIFEST_COLUMNS) { "Bad fixture manifest line: $line" }
                    Fixture(
                        wakeFileName = columns[0],
                        commandFileName = columns[1],
                        wakeWord = columns[2],
                        text = columns[3],
                        action = columns[4],
                        target = columns[5],
                        wakeCommandSilenceMs = columns[6].toInt(),
                    )
                }.toList()
        }

        private fun concatenateUtterance(
            wakeWordSamples: ShortArray,
            wakeCommandSilenceMs: Int,
            commandSamples: ShortArray,
        ): ShortArray = wakeWordSamples + silenceSamples(wakeCommandSilenceMs) + commandSamples

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
