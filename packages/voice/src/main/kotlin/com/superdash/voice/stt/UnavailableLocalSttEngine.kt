package com.superdash.voice.stt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class UnavailableLocalSttEngine(
    private val reason: String,
) : LocalSttEngine {
    override fun recognize(audio: Flow<ShortArray>): Flow<RecognitionUpdate> =
        flow {
            error(reason)
        }
}
