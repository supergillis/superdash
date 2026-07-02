package com.superdash.voice

import com.superdash.kiosk.bus.KioskEventBus
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.recognizedWordsFromText
import com.superdash.voice.pipeline.VoicePipelineCoordinator
import com.superdash.voice.pipeline.VoiceProviderIdentity
import com.superdash.voice.pipeline.VoiceProviderRunEvent
import com.superdash.voice.pipeline.VoiceProviderRunner
import com.superdash.voice.pipeline.VoiceProviderSelection
import com.superdash.voice.pipeline.VoiceRunContext
import com.superdash.voice.pipeline.VoiceRunId
import com.superdash.voice.pipeline.VoiceState
import com.superdash.voice.stt.RecognitionUpdate
import com.superdash.voice.testing.NoopTts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCommandAudioFixtureTest {
    @Test fun `generated fixtures keep wake word and command audio separate`() {
        val fixtures = loadFixtures()
        assumeTrue(
            "Generate fixtures with scripts/voice-fixtures/generate-command-wavs.main.kts",
            fixtures.isNotEmpty(),
        )

        for (fixture in fixtures) {
            val wakeWordSamples = fixture.readWakeWordSamples()
            val commandSamples = fixture.readCommandSamples()

            assumeTrue("Wake-word fixture ${fixture.wakeFileName} must contain audio", wakeWordSamples.isNotEmpty())
            assumeTrue("Command fixture ${fixture.commandFileName} must contain audio", commandSamples.isNotEmpty())
            assumeTrue(
                "Fixture ${fixture.commandFileName} must declare non-negative silence",
                fixture.wakeCommandSilenceMs >= 0,
            )
        }
    }

    @Test fun `generated command wav fixtures hand post-wake audio to HA action flow`() =
        runTest {
            val fixtures = loadFixtures()
            assumeTrue(
                "Generate fixtures with scripts/voice-fixtures/generate-command-wavs.main.kts",
                fixtures.isNotEmpty(),
            )

            for (fixture in fixtures) {
                val commandSamples = fixture.readCommandSamples()
                val consumedFrames = mutableListOf<ShortArray>()
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
                                    audio.collect { consumedFrames += it }
                                    emit(
                                        VoiceActionEvent.Recognition(
                                            RecognitionUpdate.Final(words = recognizedWordsFromText(fixture.text)),
                                        ),
                                    )
                                    emit(VoiceActionEvent.ActionComplete(response = response))
                                    kotlinx.coroutines.awaitCancellation()
                                }.map { event -> VoiceProviderRunEvent.Action(event) }
                            },
                        ttsPlayer = NoopTts,
                        bus = KioskEventBus(),
                        dispatcher = StandardTestDispatcher(testScheduler),
                        assistRunTimeoutMs = Long.MAX_VALUE,
                    )

                coordinator.onWake(testRunContext(fixture.wakeWordId), commandSamples.asFrames())
                runCurrent()

                assertEquals(
                    "Fixture ${fixture.commandFileName} should stream command-only audio",
                    commandSamples.size / FRAME_SAMPLES,
                    consumedFrames.size,
                )
                assertEquals(
                    "Fixture ${fixture.commandFileName} should record action completion",
                    VoiceState.ActionComplete(fixture.text, response),
                    coordinator.state.value,
                )
                coordinator.stopAll()
            }
        }

    private fun loadFixtures(): List<Fixture> {
        val manifest = resourceText("voice/commands/manifest.tsv") ?: return emptyList()
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

        fun readCommandSamples(): ShortArray =
            resourceStream("voice/commands/$commandFileName")
                ?.use(::readWav16kMonoPcm16)
                ?: error("Missing command fixture WAV: $commandFileName")

        fun readWakeWordSamples(): ShortArray =
            resourceStream("voice/commands/$wakeFileName")
                ?.use(::readWav16kMonoPcm16)
                ?: error("Missing wake-word fixture WAV: $wakeFileName")
    }

    private fun testRunContext(wakeWord: String): VoiceRunContext =
        VoiceRunContext(
            id = VoiceRunId.new(),
            wakeWord = wakeWord,
            startedAtEpochMs = 1_000L,
            providerSelection =
                VoiceProviderSelection(
                    primary = VoiceProviderIdentity("test", null),
                    secondary = null,
                ),
        )

    private companion object {
        private const val FRAME_SAMPLES = 160
        private const val MANIFEST_COLUMNS = 7
        private val fixtureDirectory =
            listOf(
                Path.of("packages/app/src/androidTest/assets/voice/commands"),
                Path.of("../app/src/androidTest/assets/voice/commands"),
                Path.of("src/androidTest/assets/voice/commands"),
            ).first(Files::exists)

        private fun resourceStream(path: String): InputStream? =
            requireNotNull(Thread.currentThread().contextClassLoader)
                .getResourceAsStream(path)
                ?: fileFixtureStream(path)

        private fun resourceText(path: String): String? =
            resourceStream(path)?.bufferedReader()?.use { it.readText() }

        private fun fileFixtureStream(path: String): InputStream? {
            val fileName = path.substringAfterLast('/')
            val file = fixtureDirectory.resolve(fileName)
            if (!Files.exists(file)) {
                return null
            }
            return FileInputStream(file.toFile())
        }

        private fun ShortArray.asFrames() =
            flow {
                var offset = 0
                while (offset + FRAME_SAMPLES <= size) {
                    emit(copyOfRange(offset, offset + FRAME_SAMPLES))
                    offset += FRAME_SAMPLES
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
            for (i in 0 until sampleCount) {
                out[i] = buffer.short
            }
            return out
        }
    }
}
