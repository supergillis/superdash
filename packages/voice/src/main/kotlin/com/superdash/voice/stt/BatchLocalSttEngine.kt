package com.superdash.voice.stt

import com.superdash.core.log.Log
import com.superdash.voice.action.recognizedWordsFromText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class BatchLocalSttEngine<Resource, Input>(
    private val log: Log,
    private val label: String,
    private val sampleRate: Int,
    private val maxSamples: Int,
    private val resourceFactory: () -> Resource,
    private val closeResource: (Resource) -> Unit,
    private val convertSamples: (List<ShortArray>, Int) -> Input,
    private val transcribe: suspend (Resource, Input) -> String,
    keepResourceLoaded: Boolean = false,
) : LocalSttEngine {
    private val loadedResource: Resource? =
        if (keepResourceLoaded) {
            resourceFactory()
        } else {
            null
        }
    private val loadedResourceMutex = Mutex()

    override suspend fun close() {
        val resource = loadedResource ?: return
        // Acquire the in-flight mutex so a model swap that triggers eviction
        // does not free the runtime while transcribe() is still running.
        loadedResourceMutex.withLock {
            runCatching { closeResource(resource) }
                .onFailure { throwable -> log.w("$label close failed", throwable) }
        }
    }

    override fun recognize(audio: Flow<ShortArray>): Flow<RecognitionUpdate> =
        flow {
            val frames = mutableListOf<ShortArray>()
            var totalSamples = 0
            var truncated = false
            audio.collect { frame ->
                if (totalSamples + frame.size <= maxSamples) {
                    frames += frame.copyOf()
                    totalSamples += frame.size
                } else {
                    truncated = true
                }
            }
            if (truncated) {
                log.w("$label input truncated", null, "maxSamples" to maxSamples)
            }
            if (totalSamples == 0) {
                return@flow
            }

            val samples = convertSamples(frames, totalSamples)
            val resource = loadedResource ?: resourceFactory()
            try {
                val startedAt = System.nanoTime()
                log.i(
                    "transcribing audio with $label",
                    "samples" to totalSamples,
                    "durationMs" to (totalSamples * 1000 / sampleRate),
                    "reusedRuntime" to (loadedResource != null),
                )
                val transcript =
                    if (loadedResource != null) {
                        loadedResourceMutex.withLock {
                            transcribe(resource, samples)
                        }
                    } else {
                        transcribe(resource, samples)
                    }.trim()
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                log.i(
                    "$label transcription finished",
                    "elapsedMs" to elapsedMs,
                    "hasText" to transcript.isNotBlank(),
                )
                if (transcript.isNotBlank()) {
                    emit(RecognitionUpdate.Final(words = recognizedWordsFromText(transcript)))
                }
            } finally {
                if (loadedResource == null) {
                    closeResource(resource)
                }
            }
        }.flowOn(Dispatchers.Default)
}

internal fun copyShortFrames(
    frames: List<ShortArray>,
    totalSamples: Int,
): ShortArray {
    val samples = ShortArray(totalSamples)
    var offset = 0
    for (frame in frames) {
        frame.copyInto(samples, destinationOffset = offset)
        offset += frame.size
    }
    return samples
}
