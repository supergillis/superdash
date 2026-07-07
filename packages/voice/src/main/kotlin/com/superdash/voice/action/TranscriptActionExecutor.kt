package com.superdash.voice.action

import kotlinx.coroutines.flow.Flow

fun interface TranscriptActionExecutor {
    fun execute(transcript: String): Flow<VoiceActionEvent>
}
