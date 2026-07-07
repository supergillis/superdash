package com.superdash.voice.models

import com.superdash.core.persistence.SerializedFileMutationRunner
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceModelRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `bundled model is available even before metadata exists`() =
        runTest {
            val repository = repository(scope = backgroundScope)
            advanceUntilIdle()

            val row =
                repository.state.value.models
                    .single { model -> model.id == VoiceModelIds.MOONSHINE_TINY_EN }

            assertEquals(VoiceModelInstallStatus.Available, row.status)
            assertTrue(row.selected)
        }

    @Test
    fun `delete selected STT model resets selection to bundled fallback`() =
        runTest {
            val selectedStt = MutableStateFlow("downloaded-model")
            val commands = RecordingModelSettingsCommands(selectedStt)
            val repository =
                repository(
                    scope = backgroundScope,
                    selectedSttModelId = selectedStt,
                    commands = commands,
                    catalog =
                        listOf(
                            VoiceModelCatalog.requireModel(VoiceModelIds.MOONSHINE_TINY_EN),
                            VoiceModelCatalogEntry(
                                id = "downloaded-model",
                                kind = VoiceModelKind.Stt,
                                label = "Downloaded model",
                                providerKey = "moonshine",
                                languages = listOf(VoiceModelLanguage("en", "English")),
                                files = emptyList(),
                                source = VoiceModelSource.Remote("https://models.example.test"),
                                summary = "Downloaded.",
                            ),
                        ),
                )
            advanceUntilIdle()

            repository.delete("downloaded-model")

            assertEquals(VoiceModelIds.MOONSHINE_TINY_EN, commands.lastSelectedSttModelId)
        }

    @Test
    fun `built in intent embedding row is selected by default`() =
        runTest {
            val repository = repository(scope = backgroundScope)
            advanceUntilIdle()

            val row =
                repository.state.value.models
                    .single { model -> model.id == VoiceModelIds.INTENT_EMBEDDING_NONE }

            assertEquals(VoiceModelInstallStatus.Available, row.status)
            assertTrue(row.selected)
            assertFalse(row.canDelete)
        }

    @Test
    fun `delete ignores bundled model and keeps selected fallback`() =
        runTest {
            val filesDir = temporaryFolder.newFolder("files")
            val selectedStt = MutableStateFlow(VoiceModelIds.MOONSHINE_TINY_EN)
            val commands = RecordingModelSettingsCommands(selectedStt)
            val repository =
                VoiceModelRepository(
                    catalog = VoiceModelCatalog.models,
                    store = VoiceModelStore(filesDir),
                    downloader = null,
                    selectedSttModelIds = selectedStt,
                    selectedIntentEmbeddingModelIds = MutableStateFlow(VoiceModelIds.DEFAULT_INTENT_EMBEDDING_MODEL_ID),
                    commands = commands,
                    assetOpener = { error("asset opener not used") },
                    scope = backgroundScope,
                )
            advanceUntilIdle()

            repository.delete(VoiceModelIds.MOONSHINE_TINY_EN)

            assertEquals(VoiceModelIds.MOONSHINE_TINY_EN, selectedStt.value)
            assertEquals(null, commands.lastSelectedSttModelId)
        }

    @Test
    fun `double download for same model starts one downloader run`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repositoryJob = SupervisorJob()
            val model = remoteModel("downloadable-model")
            val finishDownload = CompletableDeferred<Unit>()
            val downloader =
                RecordingVoiceModelDownloader(
                    stagingRoot = temporaryFolder.newFolder("downloads"),
                    finishSignal = finishDownload,
                )
            val repository =
                repository(
                    scope = CoroutineScope(repositoryJob + dispatcher),
                    catalog = listOf(model),
                    store =
                        VoiceModelStore(
                            filesDir = temporaryFolder.newFolder("files"),
                            mutationRunner = SerializedFileMutationRunner(dispatcher),
                        ),
                    downloader = downloader,
                    ioDispatcher = dispatcher,
                )

            try {
                repository.downloadAndInstall(model.id)
                repository.downloadAndInstall(model.id)
                runCurrent()

                val downloadingRow = repository.row(model.id)
                assertEquals(VoiceModelInstallStatus.Downloading, downloadingRow.status)
                assertFalse(downloadingRow.canDownload)
                assertEquals(1, downloader.downloadCount)

                finishDownload.complete(Unit)
                advanceUntilIdle()

                assertEquals(1, downloader.downloadCount)
                assertEquals(VoiceModelInstallStatus.Available, repository.row(model.id).status)
            } finally {
                repositoryJob.cancel()
            }
        }

    @Test
    fun `failed download keeps state alive and accepts retry`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repositoryJob = SupervisorJob()
            val model = remoteModel("retry-model")
            val finishRetry = CompletableDeferred<Unit>()
            val downloader =
                RecordingVoiceModelDownloader(
                    stagingRoot = temporaryFolder.newFolder("downloads"),
                    failures = ArrayDeque(listOf(IllegalStateException("first download failed"))),
                    finishSignal = finishRetry,
                )
            val repository =
                repository(
                    scope = CoroutineScope(repositoryJob + dispatcher),
                    catalog = listOf(model),
                    store =
                        VoiceModelStore(
                            filesDir = temporaryFolder.newFolder("files"),
                            mutationRunner = SerializedFileMutationRunner(dispatcher),
                        ),
                    downloader = downloader,
                    ioDispatcher = dispatcher,
                )

            try {
                repository.downloadAndInstall(model.id)
                advanceUntilIdle()

                val failedRow = repository.row(model.id)
                assertEquals(VoiceModelInstallStatus.Failed, failedRow.status)
                assertEquals("first download failed", failedRow.error)

                repository.downloadAndInstall(model.id)
                runCurrent()

                val retryRow = repository.row(model.id)
                assertEquals(VoiceModelInstallStatus.Downloading, retryRow.status)
                assertNull(retryRow.error)

                finishRetry.complete(Unit)
                advanceUntilIdle()

                assertEquals(2, downloader.downloadCount)
                assertEquals(VoiceModelInstallStatus.Available, repository.row(model.id).status)
            } finally {
                repositoryJob.cancel()
            }
        }

    private fun repository(
        scope: CoroutineScope,
        selectedSttModelId: MutableStateFlow<String> = MutableStateFlow(VoiceModelIds.DEFAULT_STT_MODEL_ID),
        selectedIntentEmbeddingModelId: MutableStateFlow<String> =
            MutableStateFlow(VoiceModelIds.DEFAULT_INTENT_EMBEDDING_MODEL_ID),
        commands: VoiceModelSettingsCommands = RecordingModelSettingsCommands(selectedSttModelId),
        catalog: List<VoiceModelCatalogEntry> = VoiceModelCatalog.models,
        store: VoiceModelStore = VoiceModelStore(temporaryFolder.newFolder("files")),
        downloader: VoiceModelDownloader? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): VoiceModelRepository =
        VoiceModelRepository(
            catalog = catalog,
            store = store,
            downloader = downloader,
            selectedSttModelIds = selectedSttModelId,
            selectedIntentEmbeddingModelIds = selectedIntentEmbeddingModelId,
            commands = commands,
            assetOpener = { error("asset opener not used") },
            scope = scope,
            ioDispatcher = ioDispatcher,
        )

    private fun VoiceModelRepository.row(modelId: String): VoiceModelRow =
        state.value.models.single { model -> model.id == modelId }

    private fun remoteModel(id: String): VoiceModelCatalogEntry =
        VoiceModelCatalogEntry(
            id = id,
            kind = VoiceModelKind.Stt,
            label = "Remote model",
            providerKey = "moonshine",
            languages = listOf(VoiceModelLanguage("en", "English")),
            files =
                listOf(
                    VoiceModelFile(
                        relativePath = "model.bin",
                        bytes = "model.bin".length.toLong(),
                        sha256 = sha256("model.bin"),
                    ),
                ),
            source = VoiceModelSource.Remote("https://models.example.test"),
            summary = "Remote test model.",
        )

    private class RecordingModelSettingsCommands(
        private val selectedSttModelId: MutableStateFlow<String>,
    ) : VoiceModelSettingsCommands {
        var lastSelectedSttModelId: String? = null

        override suspend fun setSelectedSttModelId(value: String) {
            lastSelectedSttModelId = value
            selectedSttModelId.value = value
        }

        override suspend fun setSelectedIntentEmbeddingModelId(value: String) {}
    }

    private class RecordingVoiceModelDownloader(
        private val stagingRoot: File,
        private val failures: ArrayDeque<Throwable> = ArrayDeque(),
        private val finishSignal: CompletableDeferred<Unit>? = null,
    ) : VoiceModelDownloader(
            client = HttpClient(MockEngine { respond("") }),
            filesDir = stagingRoot,
        ) {
        var downloadCount = 0

        override suspend fun download(
            model: VoiceModelCatalogEntry,
            onProgress: (VoiceModelDownloadProgress) -> Unit,
        ): File {
            downloadCount += 1
            failures.removeFirstOrNull()?.let { throwable -> throw throwable }
            val stagingDir = stagingRoot.resolve("${model.id}-$downloadCount")
            stagingDir.mkdirs()
            for (file in model.files) {
                val target = stagingDir.resolve(file.relativePath)
                target.parentFile?.mkdirs()
                target.writeText(file.relativePath)
                onProgress(
                    VoiceModelDownloadProgress(
                        modelId = model.id,
                        downloadedBytes = file.bytes,
                        totalBytes = model.files.sumOf { expectedFile -> expectedFile.bytes },
                        currentFile = file.relativePath,
                    ),
                )
            }
            finishSignal?.await()
            return stagingDir
        }
    }
}
