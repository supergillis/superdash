package com.superdash.voice.stt

import kotlinx.coroutines.flow.Flow

interface LocalSttEngine {
    fun recognize(audio: Flow<ShortArray>): Flow<RecognitionUpdate>

    // Suspend so implementations can acquire the in-flight recognition mutex
    // before releasing native resources; without that, a model-swap eviction
    // can race with an active transcribe() and free the runtime mid-call.
    suspend fun close() {
        // Default: nothing to release. Engines that hold native handles
        // (e.g. Whisper or Moonshine batch engines with keepResourceLoaded)
        // override this to release them on registry eviction.
    }
}
