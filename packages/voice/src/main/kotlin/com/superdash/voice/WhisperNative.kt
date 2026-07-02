package com.superdash.voice

import com.superdash.core.log.Log
import com.superdash.voice.BuildConfig

private val log = Log("WhisperNative")

interface WhisperNative {
    suspend fun transcribe(samples: ShortArray): String

    fun close()
}

class WhisperCppNative(
    modelPath: String,
) : WhisperNative {
    private val nativeLock = Any()

    @Volatile
    private var handle: Long = 0L

    init {
        loadLibraryOrThrow()
        handle = createNative(modelPath)
        check(handle != 0L) { "Whisper model failed to load: $modelPath" }
    }

    override suspend fun transcribe(samples: ShortArray): String =
        synchronized(nativeLock) {
            val current = handle
            check(current != 0L) { "Whisper model is closed" }
            transcribeNative(current, samples)
        }

    override fun close() {
        synchronized(nativeLock) {
            val current = handle
            if (current != 0L) {
                releaseNative(current)
                handle = 0L
            }
        }
    }

    private external fun createNative(modelPath: String): Long

    private external fun transcribeNative(
        handle: Long,
        samples: ShortArray,
    ): String

    private external fun releaseNative(handle: Long)

    companion object {
        fun isAvailable(): Boolean {
            if (!BuildConfig.WHISPER_NATIVE_ENABLED) {
                log.w("Whisper native disabled at build time")
                return false
            }
            return runCatching {
                loadLibraryOrThrow()
                runtimeAvailable()
            }.onFailure { throwable ->
                log.w("Whisper native unavailable", throwable)
            }.getOrDefault(false)
        }

        private fun loadLibraryOrThrow() {
            System.loadLibrary("superdash_whisper")
        }

        @JvmStatic
        private external fun runtimeAvailable(): Boolean
    }
}
