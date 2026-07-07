package com.superdash.voice.models

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceModelDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `downloads every remote model file into staging directory`() =
        runTest {
            val filesDir = temporaryFolder.newFolder("files")
            val model = remoteModel("https://models.example.test")
            val client =
                HttpClient(
                    MockEngine { request ->
                        respond(
                            content = request.url.encodedPath.substringAfterLast('/'),
                            status = HttpStatusCode.OK,
                        )
                    },
                )
            val downloader = VoiceModelDownloader(client = client, filesDir = filesDir)

            val stagingDir = downloader.download(model) {}

            assertEquals("encoder_model.ort", stagingDir.resolve("encoder_model.ort").readText())
            assertEquals("tokenizer.bin", stagingDir.resolve("tokenizer.bin").readText())
        }

    @Test
    fun `download fails on HTTP error and removes partial files`() =
        runTest {
            val filesDir = temporaryFolder.newFolder("files")
            val model = remoteModel("https://models.example.test")
            val client = HttpClient(MockEngine { respond("", status = HttpStatusCode.NotFound) })
            val downloader = VoiceModelDownloader(client = client, filesDir = filesDir)

            val result = runCatching { downloader.download(model) {} }

            assertTrue(result.isFailure)
            assertTrue(filesDir.resolve("voice-model-downloads/test-remote").listFiles().isNullOrEmpty())
        }

    @Test
    fun `download reports bytes written`() =
        runTest {
            val filesDir = temporaryFolder.newFolder("files")
            val model = remoteModel("https://models.example.test")
            val client =
                HttpClient(
                    MockEngine { request ->
                        respond(request.url.encodedPath.substringAfterLast('/'), status = HttpStatusCode.OK)
                    },
                )
            val progress = mutableListOf<VoiceModelDownloadProgress>()

            VoiceModelDownloader(client, filesDir).download(model) { value -> progress += value }

            assertEquals(model.files.sumOf { file -> file.bytes }, progress.last().downloadedBytes)
            assertEquals(model.files.sumOf { file -> file.bytes }, progress.last().totalBytes)
        }

    private fun remoteModel(baseUrl: String): VoiceModelCatalogEntry =
        VoiceModelCatalogEntry(
            id = "test-remote",
            kind = VoiceModelKind.Stt,
            label = "Test remote",
            providerKey = "moonshine",
            languages = listOf(VoiceModelLanguage("en", "English")),
            source = VoiceModelSource.Remote(baseUrl),
            summary = "Remote test model.",
            files =
                listOf(
                    VoiceModelFile("encoder_model.ort", 17L, sha256("encoder_model.ort")),
                    VoiceModelFile("tokenizer.bin", 13L, sha256("tokenizer.bin")),
                ),
        )
}
