package com.superdash.voice.models

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

enum class VoiceModelInstallStatus {
    Available,
    NotInstalled,
    Downloading,
    Failed,
}

data class VoiceModelRow(
    val id: String,
    val kind: VoiceModelKind,
    val label: String,
    val summary: String,
    val languages: List<VoiceModelLanguage>,
    val status: VoiceModelInstallStatus,
    val selected: Boolean,
    val canDownload: Boolean,
    val canDelete: Boolean,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null,
)

data class VoiceModelState(
    val models: List<VoiceModelRow>,
)

interface VoiceModelSettingsCommands {
    suspend fun setSelectedSttModelId(value: String)

    suspend fun setSelectedIntentEmbeddingModelId(value: String)
}

class VoiceModelRepository(
    private val catalog: List<VoiceModelCatalogEntry>,
    private val store: VoiceModelStore,
    private val downloader: VoiceModelDownloader?,
    selectedSttModelIds: Flow<String>,
    selectedIntentEmbeddingModelIds: Flow<String>,
    private val commands: VoiceModelSettingsCommands,
    private val assetOpener: (String) -> InputStream,
    scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val repositoryScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val installed = MutableStateFlow<List<InstalledVoiceModelMetadata>>(emptyList())
    private val progress = MutableStateFlow<Map<String, VoiceModelDownloadProgress>>(emptyMap())
    private val failures = MutableStateFlow<Map<String, String>>(emptyMap())
    private val activeDownloadsLock = Any()
    private val activeDownloads = mutableSetOf<String>()
    private val initialSelectedSttModelId = selectedInitial(selectedSttModelIds, VoiceModelIds.DEFAULT_STT_MODEL_ID)
    private val initialSelectedIntentEmbeddingModelId =
        selectedInitial(selectedIntentEmbeddingModelIds, VoiceModelIds.DEFAULT_INTENT_EMBEDDING_MODEL_ID)

    val state: StateFlow<VoiceModelState> =
        combine(
            installed,
            progress,
            failures,
            selectedSttModelIds.onStart { emit(initialSelectedSttModelId) },
            selectedIntentEmbeddingModelIds.onStart { emit(initialSelectedIntentEmbeddingModelId) },
        ) {
            installedModels,
            progressByModel,
            failuresByModel,
            selectedSttModelId,
            selectedIntentEmbeddingModelId,
            ->
            buildState(
                installedModels = installedModels,
                progressByModel = progressByModel,
                failuresByModel = failuresByModel,
                selectedSttModelId = selectedSttModelId,
                selectedIntentEmbeddingModelId = selectedIntentEmbeddingModelId,
            )
        }.stateIn(
            repositoryScope,
            SharingStarted.Eagerly,
            buildState(
                installedModels = emptyList(),
                progressByModel = emptyMap(),
                failuresByModel = emptyMap(),
                selectedSttModelId = initialSelectedSttModelId,
                selectedIntentEmbeddingModelId = initialSelectedIntentEmbeddingModelId,
            ),
        )

    init {
        repositoryScope.launch {
            try {
                refreshInstalled()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                // Initial install metadata refresh should not kill later downloads.
            }
        }
    }

    suspend fun select(modelId: String) {
        val model = catalog.first { model -> model.id == modelId }
        when (model.kind) {
            VoiceModelKind.Stt -> commands.setSelectedSttModelId(model.id)
            VoiceModelKind.IntentEmbedding -> commands.setSelectedIntentEmbeddingModelId(model.id)
        }
    }

    fun downloadAndInstall(modelId: String) {
        val model = catalog.first { model -> model.id == modelId }
        val accepted =
            synchronized(activeDownloadsLock) {
                if (model.id in activeDownloads) {
                    false
                } else {
                    activeDownloads += model.id
                    true
                }
            }
        if (!accepted) {
            return
        }
        failures.update { values -> values - model.id }
        progress.update { values -> values + (model.id to initialProgress(model)) }

        repositoryScope.launch {
            try {
                try {
                    val stagingDir =
                        withContext(ioDispatcher) {
                            requireNotNull(downloader) { "Downloader is not configured" }
                                .download(model) { value ->
                                    progress.update { values -> values + (model.id to value) }
                                }
                        }
                    withContext(ioDispatcher) {
                        store.installFromDirectory(model, stagingDir)
                    }
                    progress.update { values -> values - model.id }
                    refreshInstalled()
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) {
                        throw throwable
                    }
                    progress.update { values -> values - model.id }
                    failures.update { values ->
                        values + (model.id to (throwable.message ?: "Download failed"))
                    }
                }
            } finally {
                synchronized(activeDownloadsLock) {
                    activeDownloads -= model.id
                }
            }
        }
    }

    private fun initialProgress(model: VoiceModelCatalogEntry): VoiceModelDownloadProgress =
        VoiceModelDownloadProgress(
            modelId = model.id,
            downloadedBytes = 0L,
            totalBytes = model.files.sumOf { file -> file.bytes },
            currentFile = model.files.firstOrNull()?.relativePath ?: "",
        )

    private suspend fun refreshInstalled() {
        installed.value =
            withContext(ioDispatcher) {
                store.installedModels()
            }
    }

    suspend fun installBundled(modelId: String) {
        val model = catalog.first { model -> model.id == modelId }
        withContext(ioDispatcher) {
            store.installFromAssets(model, assetOpener)
        }
        refreshInstalled()
    }

    suspend fun delete(modelId: String) {
        val model = catalog.first { model -> model.id == modelId }
        val protectedModel =
            model.source is VoiceModelSource.BundledAsset ||
                model.source is VoiceModelSource.BuiltInNone
        if (protectedModel) {
            return
        }
        val wasSelected =
            state.value.models
                .firstOrNull { row -> row.id == model.id }
                ?.selected == true
        withContext(ioDispatcher) {
            store.delete(model.id)
        }
        refreshInstalled()
        if (wasSelected && model.kind == VoiceModelKind.Stt) {
            commands.setSelectedSttModelId(VoiceModelIds.DEFAULT_STT_MODEL_ID)
        }
        if (wasSelected && model.kind == VoiceModelKind.IntentEmbedding) {
            commands.setSelectedIntentEmbeddingModelId(VoiceModelIds.DEFAULT_INTENT_EMBEDDING_MODEL_ID)
        }
    }

    private fun selectedInitial(
        flow: Flow<String>,
        defaultValue: String,
    ): String = (flow as? StateFlow<String>)?.value ?: defaultValue

    private fun buildState(
        installedModels: List<InstalledVoiceModelMetadata>,
        progressByModel: Map<String, VoiceModelDownloadProgress>,
        failuresByModel: Map<String, String>,
        selectedSttModelId: String,
        selectedIntentEmbeddingModelId: String,
    ): VoiceModelState =
        VoiceModelState(
            models =
                catalog.map { model ->
                    val isInstalled = installedModels.any { metadata -> metadata.modelId == model.id }
                    val isBuiltIn = model.source is VoiceModelSource.BuiltInNone
                    val isBundled = model.source is VoiceModelSource.BundledAsset
                    val modelProgress = progressByModel[model.id]
                    val selected =
                        when (model.kind) {
                            VoiceModelKind.Stt -> model.id == selectedSttModelId
                            VoiceModelKind.IntentEmbedding -> model.id == selectedIntentEmbeddingModelId
                        }
                    VoiceModelRow(
                        id = model.id,
                        kind = model.kind,
                        label = model.label,
                        summary = model.summary,
                        languages = model.languages,
                        status =
                            when {
                                modelProgress != null -> VoiceModelInstallStatus.Downloading
                                failuresByModel.containsKey(model.id) -> VoiceModelInstallStatus.Failed
                                isInstalled || isBundled || isBuiltIn -> VoiceModelInstallStatus.Available
                                else -> VoiceModelInstallStatus.NotInstalled
                            },
                        selected = selected,
                        canDownload = model.canDownload && !isInstalled && modelProgress == null,
                        canDelete = isInstalled,
                        downloadedBytes = modelProgress?.downloadedBytes ?: 0L,
                        totalBytes = modelProgress?.totalBytes ?: model.files.sumOf { file -> file.bytes },
                        error = failuresByModel[model.id],
                    )
                },
        )
}
