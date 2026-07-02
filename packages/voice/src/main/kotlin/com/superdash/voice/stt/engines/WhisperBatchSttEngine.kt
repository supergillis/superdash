package com.superdash.voice.stt.engines

import android.content.Context
import com.superdash.core.log.Log
import com.superdash.core.persistence.AssetInstaller
import com.superdash.voice.WhisperCppNative
import com.superdash.voice.WhisperNative
import com.superdash.voice.stt.BatchLocalSttEngine
import com.superdash.voice.stt.LocalSttEngine
import com.superdash.voice.stt.UnavailableLocalSttEngine
import com.superdash.voice.stt.copyShortFrames
import java.io.File

private const val WHISPER_MODEL_FILE = "ggml-tiny.en-q5_1.bin"
private const val WHISPER_MODEL_ASSET = "models/whisper/$WHISPER_MODEL_FILE"
private const val WHISPER_MODEL_FILES_DIR = "models/whisper"
private const val DEFAULT_MAX_WHISPER_SAMPLES = 16000 * 5

private val whisperLog = Log("WhisperBatchStt")

class WhisperBatchSttEngine(
    nativeFactory: () -> WhisperNative,
    maxSamples: Int = DEFAULT_MAX_WHISPER_SAMPLES,
    keepNativeLoaded: Boolean = false,
) : LocalSttEngine by BatchLocalSttEngine(
        log = whisperLog,
        label = "Whisper",
        sampleRate = 16000,
        maxSamples = maxSamples,
        resourceFactory = nativeFactory,
        closeResource = { native -> native.close() },
        convertSamples = ::copyShortFrames,
        transcribe = { native, samples -> native.transcribe(samples) },
        keepResourceLoaded = keepNativeLoaded,
    ) {
    companion object {
        fun createOrUnavailable(context: Context): LocalSttEngine {
            val appContext = context.applicationContext
            val modelFile = appContext.whisperModelFile()
            val modelReady = appContext.copyWhisperModelAssetIfPresent(modelFile)
            val runtimeReady = WhisperCppNative.isAvailable()
            whisperLog.i(
                "checked local Whisper STT availability",
                "modelReady" to modelReady,
                "runtimeReady" to runtimeReady,
                "modelPath" to modelFile.absolutePath,
                "modelBytes" to modelFile.length(),
            )
            return if (modelReady && runtimeReady) {
                runCatching {
                    WhisperBatchSttEngine(
                        nativeFactory = { WhisperCppNative(modelFile.absolutePath) },
                        keepNativeLoaded = true,
                    )
                }.getOrElse { throwable ->
                    whisperLog.w("local Whisper STT failed to initialize", throwable)
                    UnavailableLocalSttEngine("Whisper STT failed to initialize: ${throwable.message}")
                }
            } else {
                val reason = "Whisper STT runtime or model is missing at ${modelFile.absolutePath}"
                whisperLog.w("local Whisper STT unavailable", null, "reason" to reason)
                UnavailableLocalSttEngine(reason)
            }
        }
    }
}

private fun Context.whisperModelFile(): File = File(File(filesDir, WHISPER_MODEL_FILES_DIR), WHISPER_MODEL_FILE)

private fun Context.copyWhisperModelAssetIfPresent(modelFile: File): Boolean {
    val installer = AssetInstaller(openAsset = { path -> assets.open(path) })
    return installer.installIfMissing(WHISPER_MODEL_ASSET, modelFile) != null
}
