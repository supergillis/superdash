package com.superdash.voice.models

import com.superdash.core.persistence.FileMutationRunner
import com.superdash.core.persistence.SerializedFileMutationRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class VoiceModelStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `install bundled asset copies files and writes metadata`() =
        runTest {
            val filesDir = temporaryFolder.newFolder("files")
            val model =
                testModel(
                    files =
                        listOf(
                            VoiceModelFile("encoder_model.ort", 7L, sha256("encoder")),
                            VoiceModelFile("decoder_model_merged.ort", 7L, sha256("decoder")),
                            VoiceModelFile("tokenizer.bin", 9L, sha256("tokenizer")),
                        ),
                )
            val store = VoiceModelStore(filesDir = filesDir)

            val installed =
                store.installFromAssets(
                    model = model,
                    assetOpener = { path ->
                        ByteArrayInputStream(
                            when (path.substringAfterLast('/')) {
                                "encoder_model.ort" -> "encoder"
                                "decoder_model_merged.ort" -> "decoder"
                                "tokenizer.bin" -> "tokenizer"
                                else -> error("unexpected asset path $path")
                            }.toByteArray(),
                        )
                    },
                )

            assertEquals(model.id, installed.modelId)
            assertTrue(filesDir.resolve("voice-models/test-model/encoder_model.ort").exists())
            assertTrue(store.installedModels().any { metadata -> metadata.modelId == "test-model" })
        }

    @Test
    fun `install rejects checksum mismatch and removes partial directory`() =
        runTest {
            val filesDir = temporaryFolder.newFolder("files")
            val model = testModel(files = listOf(VoiceModelFile("tokenizer.bin", 3L, sha256("good"))))
            val store = VoiceModelStore(filesDir = filesDir)

            val result =
                runCatching {
                    store.installFromAssets(
                        model = model,
                        assetOpener = { ByteArrayInputStream("bad".toByteArray()) },
                    )
                }

            assertTrue(result.isFailure)
            assertFalse(filesDir.resolve("voice-models/test-model").exists())
        }

    @Test
    fun `delete removes installed files and metadata`() =
        runTest {
            val filesDir = temporaryFolder.newFolder("files")
            val model = testModel(files = listOf(VoiceModelFile("tokenizer.bin", 4L, sha256("data"))))
            val store = VoiceModelStore(filesDir = filesDir)
            store.installFromAssets(model, assetOpener = { ByteArrayInputStream("data".toByteArray()) })

            store.delete(model.id)

            assertFalse(filesDir.resolve("voice-models/test-model").exists())
            assertTrue(store.installedModels().none { metadata -> metadata.modelId == "test-model" })
        }

    @Test
    fun `install and delete mutations do not overlap`() =
        runTest {
            val filesDir = temporaryFolder.newFolder("serialized-files")
            val mutationRunner = TrackingMutationRunner()
            val store = VoiceModelStore(filesDir = filesDir, mutationRunner = mutationRunner)
            val model = testModel(files = listOf(VoiceModelFile("tokenizer.bin", 4L, sha256("data"))))

            joinAll(
                launch { store.installFromAssets(model, assetOpener = { ByteArrayInputStream("data".toByteArray()) }) },
                launch { store.delete(model.id) },
            )

            assertEquals(1, mutationRunner.maxActive)
        }

    private fun testModel(files: List<VoiceModelFile>): VoiceModelCatalogEntry =
        VoiceModelCatalogEntry(
            id = "test-model",
            kind = VoiceModelKind.Stt,
            label = "Test model",
            providerKey = "moonshine",
            languages = listOf(VoiceModelLanguage("en", "English")),
            files = files,
            source = VoiceModelSource.BundledAsset("models/test"),
            summary = "Test model.",
        )

    private fun testFileMutationRunner(): FileMutationRunner =
        SerializedFileMutationRunner(dispatcher = Dispatchers.Unconfined)

    private class TrackingMutationRunner : FileMutationRunner {
        private val delegate = SerializedFileMutationRunner(dispatcher = Dispatchers.Unconfined)
        private var active = 0
        var maxActive = 0
            private set

        override suspend fun <T> mutate(block: suspend () -> T): T =
            delegate.mutate {
                active += 1
                maxActive = maxOf(maxActive, active)
                try {
                    delay(1)
                    block()
                } finally {
                    active -= 1
                }
            }
    }
}
