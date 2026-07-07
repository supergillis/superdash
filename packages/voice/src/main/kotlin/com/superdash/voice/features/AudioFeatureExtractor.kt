package com.superdash.voice.features

import androidx.annotation.Keep

@Keep
class AudioFeatureExtractor(
    sampleRateHz: Int,
    stepSizeMs: Int,
) : AutoCloseable {
    private var handle: Long = nativeInit(sampleRateHz, stepSizeMs)

    fun extract(samples: ShortArray): List<FloatArray> {
        check(handle != 0L) { "AudioFeatureExtractor already closed" }
        return nativeExtract(handle, samples)
    }

    override fun close() {
        if (handle != 0L) {
            nativeRelease(handle)
            handle = 0L
        }
    }

    private external fun nativeInit(
        sampleRateHz: Int,
        stepSizeMs: Int,
    ): Long

    private external fun nativeRelease(handle: Long)

    private external fun nativeExtract(
        handle: Long,
        samples: ShortArray,
    ): List<FloatArray>

    companion object {
        init {
            System.loadLibrary("superdash_audio_features")
        }
    }
}
