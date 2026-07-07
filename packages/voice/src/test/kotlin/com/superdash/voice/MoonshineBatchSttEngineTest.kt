package com.superdash.voice

import ai.moonshine.voice.JNI
import com.superdash.voice.action.LocalTranscriptActionFlow
import com.superdash.voice.action.TranscriptActionExecutor
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.models.VoiceModelCatalog
import com.superdash.voice.models.VoiceModelCatalogEntry
import com.superdash.voice.models.VoiceModelFile
import com.superdash.voice.models.VoiceModelIds
import com.superdash.voice.models.VoiceModelKind
import com.superdash.voice.models.VoiceModelLanguage
import com.superdash.voice.models.VoiceModelSource
import com.superdash.voice.models.sha256
import com.superdash.voice.stt.RecognitionUpdate
import com.superdash.voice.stt.engines.MoonshineBatchSttEngine
import com.superdash.voice.stt.engines.MoonshineBatchTranscriber
import com.superdash.voice.stt.engines.copyMoonshineModelFilesIfPresent
import com.superdash.voice.stt.engines.resolveMoonshineModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class MoonshineBatchSttEngineTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `recognize buffers audio and emits final transcript`() =
        runTest {
            val transcriber = FakeMoonshineTranscriber("turn on the kitchen lights")
            val engine = MoonshineBatchSttEngine(transcriberFactory = { transcriber })

            val updates = engine.recognize(flowOf(shortArrayOf(Short.MAX_VALUE, 0), shortArrayOf(Short.MIN_VALUE))).toList()

            assertEquals("turn on the kitchen lights", (updates.single() as RecognitionUpdate.Final).text)
            assertEquals(listOf(Short.MAX_VALUE / 32768f, 0f, Short.MIN_VALUE / 32768f), transcriber.receivedSamples.toList())
            assertTrue(transcriber.closed)
        }

    @Test
    fun `recognize emits no final transcript for blank result`() =
        runTest {
            val transcriber = FakeMoonshineTranscriber(" ")
            val engine = MoonshineBatchSttEngine(transcriberFactory = { transcriber })

            val updates = engine.recognize(flowOf(shortArrayOf(1))).toList()

            assertTrue(updates.isEmpty())
            assertTrue(transcriber.closed)
        }

    @Test
    fun `recognize emits no final transcript for empty audio`() =
        runTest {
            val transcriber = FakeMoonshineTranscriber("turn on the kitchen lights")
            val engine = MoonshineBatchSttEngine(transcriberFactory = { transcriber })

            val updates = engine.recognize(flowOf()).toList()

            assertTrue(updates.isEmpty())
            assertEquals(0, transcriber.receivedSamples.size)
        }

    @Test
    fun `recognize caps default audio passed to transcriber at five seconds`() =
        runTest {
            val fiveSecondsOfSamples = 16000 * 5
            val transcriber = FakeMoonshineTranscriber("turn on the kitchen lights")
            val engine = MoonshineBatchSttEngine(transcriberFactory = { transcriber })

            engine.recognize(flowOf(ShortArray(fiveSecondsOfSamples), shortArrayOf(1))).toList()

            assertEquals(fiveSecondsOfSamples, transcriber.receivedSamples.size)
        }

    @Test
    fun `recognize can keep transcriber loaded across requests`() =
        runTest {
            val transcriber = FakeMoonshineTranscriber("turn on the kitchen lights")
            var transcriberCreates = 0
            val engine =
                MoonshineBatchSttEngine(
                    transcriberFactory = {
                        transcriberCreates += 1
                        transcriber
                    },
                    keepTranscriberLoaded = true,
                )

            engine.recognize(flowOf(shortArrayOf(1, 2))).toList()
            engine.recognize(flowOf(shortArrayOf(3, 4))).toList()

            assertEquals(1, transcriberCreates)
            assertEquals(2, transcriber.receivedSamples.size)
            assertTrue(!transcriber.closed)
        }

    @Test
    fun `recognize serializes access to retained transcriber`() =
        runTest {
            val transcriber = SerialCheckingMoonshineTranscriber("turn on the kitchen lights")
            val engine =
                MoonshineBatchSttEngine(
                    transcriberFactory = { transcriber },
                    keepTranscriberLoaded = true,
                )

            awaitAll(
                async { engine.recognize(flowOf(shortArrayOf(1, 2))).toList() },
                async { engine.recognize(flowOf(shortArrayOf(3, 4))).toList() },
            )

            assertEquals(0, transcriber.concurrentCalls)
            assertFalse(transcriber.closed)
        }

    @Test
    fun `create for ready model returns unavailable engine when native init fails`() =
        runTest {
            val engine =
                MoonshineBatchSttEngine.createForReadyModelOrUnavailable(
                    modelDescription = "test model",
                    transcriberFactory = { error("load failed") },
                )

            val updates =
                LocalTranscriptActionFlow(
                    localStt = engine,
                    transcriptActionExecutor =
                        TranscriptActionExecutor { text ->
                            flowOf(VoiceActionEvent.ActionComplete(transcript = text, response = buildJsonObject { }))
                        },
                    audioActionProvider = {
                        flowOf(
                            VoiceActionEvent.ActionComplete(
                                transcript = "ha audio fallback",
                                response = buildJsonObject { },
                            ),
                        )
                    },
                ).invoke(flowOf(shortArrayOf(1, 2))).toList()

            assertEquals("ha audio fallback", updates.filterIsInstance<VoiceActionEvent.ActionComplete>().single().transcript)
        }

    @Test
    fun `copy model files replaces stale partial files atomically`() {
        val targetDir = temporaryFolder.newFolder("moonshine-model")
        val staleDecoder = targetDir.resolve("decoder_model_merged.ort")
        staleDecoder.writeBytes(byteArrayOf(1))

        val copied =
            copyMoonshineModelFilesIfPresent(
                modelDir = targetDir,
                assetOpener = { name -> ByteArrayInputStream("asset-$name".toByteArray()) },
            )

        assertTrue(copied)
        assertEquals("asset-decoder_model_merged.ort", staleDecoder.readText())
        assertFalse(targetDir.listFiles().orEmpty().any { file -> file.name.endsWith(".tmp") })
    }

    @Test
    fun `resolve model uses installed selected model when files are valid`() {
        val modelDir = temporaryFolder.newFolder("selected-model")
        modelDir.resolve("encoder_model.ort").writeText("encoder")
        modelDir.resolve("decoder_model_merged.ort").writeText("decoder")
        modelDir.resolve("tokenizer.bin").writeText("tokenizer")
        val selectedModel =
            VoiceModelCatalogEntry(
                id = "selected",
                kind = VoiceModelKind.Stt,
                label = "Selected",
                providerKey = "moonshine",
                languages = listOf(VoiceModelLanguage("en", "English")),
                files =
                    listOf(
                        VoiceModelFile("encoder_model.ort", 7L, sha256("encoder")),
                        VoiceModelFile("decoder_model_merged.ort", 7L, sha256("decoder")),
                        VoiceModelFile("tokenizer.bin", 9L, sha256("tokenizer")),
                    ),
                source = VoiceModelSource.Remote("https://models.example.test"),
                moonshineModelArch = JNI.MOONSHINE_MODEL_ARCH_BASE,
                summary = "Selected.",
            )

        val resolved =
            resolveMoonshineModel(
                requestedModel = selectedModel,
                installedModelDir = { modelDir },
                bundledModelDir = { error("bundled fallback should not be used") },
            )

        assertEquals(modelDir.absolutePath, resolved.modelDir.absolutePath)
        assertEquals(JNI.MOONSHINE_MODEL_ARCH_BASE, resolved.modelArch)
    }

    @Test
    fun `resolve model falls back to bundled model when selected files are missing`() {
        val bundledDir = temporaryFolder.newFolder("bundled-model")
        bundledDir.resolve("encoder_model.ort").writeText("encoder")
        bundledDir.resolve("decoder_model_merged.ort").writeText("decoder")
        bundledDir.resolve("tokenizer.bin").writeText("tokenizer")

        val resolved =
            resolveMoonshineModel(
                requestedModel = VoiceModelCatalog.requireModel(VoiceModelIds.MOONSHINE_TINY_EN),
                installedModelDir = { temporaryFolder.newFolder("missing-selected") },
                bundledModelDir = { bundledDir },
            )

        assertEquals(bundledDir.absolutePath, resolved.modelDir.absolutePath)
    }

    private class FakeMoonshineTranscriber(
        private val result: String,
    ) : MoonshineBatchTranscriber {
        var receivedSamples = FloatArray(0)
        var closed = false

        override fun transcribe(samples: FloatArray): String {
            receivedSamples = samples
            return result
        }

        override fun close() {
            closed = true
        }
    }

    private class SerialCheckingMoonshineTranscriber(
        private val result: String,
    ) : MoonshineBatchTranscriber {
        var concurrentCalls = 0
        private var active = false
        var closed = false

        override fun transcribe(samples: FloatArray): String {
            if (active) {
                concurrentCalls += 1
            }
            active = true
            Thread.sleep(10)
            active = false
            return result
        }

        override fun close() {
            closed = true
        }
    }
}
