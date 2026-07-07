package com.superdash.voice.wake

import kotlinx.coroutines.flow.Flow

interface WakeWordDetector : AutoCloseable {
    fun detect(audio: Flow<ShortArray>): Flow<WakeEvent>
}
