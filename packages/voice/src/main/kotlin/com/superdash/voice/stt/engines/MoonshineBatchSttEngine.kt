package com.superdash.voice.stt.engines

import ai.moonshine.voice.JNI
import ai.moonshine.voice.Transcriber
import android.content.Context
import com.superdash.core.log.Log
import com.superdash.core.persistence.AssetInstaller
import com.superdash.voice.models.VoiceModelCatalog
import com.superdash.voice.models.VoiceModelCatalogEntry
import com.superdash.voice.models.VoiceModelIds
import com.superdash.voice.models.VoiceModelStore
import com.superdash.voice.models.sha256
import com.superdash.voice.stt.BatchLocalSttEngine
import com.superdash.voice.stt.LocalSttEngine
import com.superdash.voice.stt.UnavailableLocalSttEngine
import java.io.File
import java.io.InputStream

private const val MOONSHINE_SAMPLE_RATE = 16000
private const val DEFAULT_MAX_MOONSHINE_SAMPLES = MOONSHINE_SAMPLE_RATE * 5
private const val DEFAULT_MOONSHINE_MODEL_ASSET_DIR = "models/moonshine/tiny-en"
private const val DEFAULT_MOONSHINE_MODEL_FILES_DIR = "models/moonshine/tiny-en"

private val moonshineLog = Log("MoonshineBatchStt")
private val moonshineModelFiles =
    listOf(
        "encoder_model.ort",
        "decoder_model_merged.ort",
        "tokenizer.bin",
    )

interface MoonshineBatchTranscriber {
    fun transcribe(samples: FloatArray): String

    fun close()
}

class MoonshineBatchSttEngine(
    transcriberFactory: () -> MoonshineBatchTranscriber,
    maxSamples: Int = DEFAULT_MAX_MOONSHINE_SAMPLES,
    keepTranscriberLoaded: Boolean = false,
) : LocalSttEngine by BatchLocalSttEngine(
        log = moonshineLog,
        label = "Moonshine",
        sampleRate = MOONSHINE_SAMPLE_RATE,
        maxSamples = maxSamples,
        resourceFactory = transcriberFactory,
        closeResource = { transcriber -> transcriber.close() },
        convertSamples = ::copyMoonshineFloatFrames,
        transcribe = { transcriber, samples -> transcriber.transcribe(samples) },
        keepResourceLoaded = keepTranscriberLoaded,
    ) {
    companion object {
        fun createOrUnavailable(
            context: Context,
            selectedModelId: String = VoiceModelIds.DEFAULT_STT_MODEL_ID,
            modelAssetDir: String = DEFAULT_MOONSHINE_MODEL_ASSET_DIR,
            modelFilesDir: String = DEFAULT_MOONSHINE_MODEL_FILES_DIR,
            modelArch: Int = JNI.MOONSHINE_MODEL_ARCH_TINY,
        ): LocalSttEngine {
            val appContext = context.applicationContext
            val requestedModel =
                runCatching { VoiceModelCatalog.requireModel(selectedModelId) }
                    .getOrDefault(VoiceModelCatalog.requireModel(VoiceModelIds.DEFAULT_STT_MODEL_ID))
            val store = VoiceModelStore(appContext.filesDir)
            val bundledModelDir = File(appContext.filesDir, modelFilesDir)
            val bundledReady =
                copyMoonshineModelFilesIfPresent(
                    modelDir = bundledModelDir,
                    assetOpener = { name -> appContext.assets.open("$modelAssetDir/$name") },
                )
            if (!bundledReady) {
                val reason = "Moonshine bundled STT model is missing at $modelAssetDir"
                moonshineLog.w("local STT unavailable", null, "reason" to reason)
                return UnavailableLocalSttEngine(reason)
            }
            val resolved =
                resolveMoonshineModel(
                    requestedModel = requestedModel,
                    installedModelDir = { modelId -> store.modelDir(modelId) },
                    bundledModelDir = { bundledModelDir },
                    fallbackModelArch = modelArch,
                )
            moonshineLog.i(
                "checked local Moonshine STT availability",
                "requestedModel" to selectedModelId,
                "modelPath" to resolved.modelDir.absolutePath,
            )
            return createForReadyModelOrUnavailable(
                modelDescription = resolved.modelDir.absolutePath,
                transcriberFactory = {
                    MoonshineJavaBatchTranscriber(
                        modelDir = resolved.modelDir,
                        modelArch = resolved.modelArch,
                    )
                },
            )
        }

        internal fun createForReadyModelOrUnavailable(
            modelDescription: String,
            transcriberFactory: () -> MoonshineBatchTranscriber,
        ): LocalSttEngine =
            runCatching {
                MoonshineBatchSttEngine(
                    transcriberFactory = transcriberFactory,
                    keepTranscriberLoaded = true,
                )
            }.getOrElse { throwable ->
                moonshineLog.w(
                    "local Moonshine STT failed to initialize",
                    throwable,
                    "model" to modelDescription,
                )
                UnavailableLocalSttEngine("Moonshine STT failed to initialize: ${throwable.message}")
            }
    }
}

data class MoonshineResolvedModel(
    val modelDir: File,
    val modelArch: Int,
)

internal fun resolveMoonshineModel(
    requestedModel: VoiceModelCatalogEntry,
    installedModelDir: (String) -> File,
    bundledModelDir: () -> File,
    fallbackModelArch: Int = JNI.MOONSHINE_MODEL_ARCH_TINY,
): MoonshineResolvedModel {
    val selectedDir = installedModelDir(requestedModel.id)
    if (requestedModel.files.all { expectedFile ->
            val file = selectedDir.resolve(expectedFile.relativePath)
            file.exists() && file.length() == expectedFile.bytes && file.sha256() == expectedFile.sha256
        }
    ) {
        return MoonshineResolvedModel(selectedDir, requestedModel.moonshineModelArch ?: fallbackModelArch)
    }
    return MoonshineResolvedModel(bundledModelDir(), JNI.MOONSHINE_MODEL_ARCH_TINY)
}

private class MoonshineJavaBatchTranscriber(
    modelDir: File,
    modelArch: Int,
) : MoonshineBatchTranscriber {
    private val transcriber =
        Transcriber().also { value ->
            value.loadFromFiles(modelDir.absolutePath + File.separator, modelArch)
        }

    override fun transcribe(samples: FloatArray): String =
        transcriber
            .transcribeWithoutStreaming(samples, MOONSHINE_SAMPLE_RATE)
            .text()

    override fun close() {
        // Moonshine's Java Transcriber only exposes native cleanup through finalize.
    }
}

private fun copyMoonshineFloatFrames(
    frames: List<ShortArray>,
    totalSamples: Int,
): FloatArray {
    val samples = FloatArray(totalSamples)
    var offset = 0
    for (frame in frames) {
        for (sample in frame) {
            samples[offset] = sample / 32768f
            offset += 1
        }
    }
    return samples
}

internal fun copyMoonshineModelFilesIfPresent(
    modelDir: File,
    assetOpener: (String) -> InputStream,
): Boolean {
    val installer = AssetInstaller(openAsset = assetOpener)
    return runCatching {
        modelDir.mkdirs()
        moonshineModelFiles.all { name ->
            installer.installIfMissing(name, File(modelDir, name)) != null
        }
    }.getOrDefault(false)
}
