package com.superdash.voice.action

import com.superdash.ha.AssistEvent
import com.superdash.voice.pipeline.VoiceProviderProvenance
import com.superdash.voice.stt.RecognitionUpdate
import com.superdash.voice.stt.RecognizedWord
import kotlinx.serialization.json.JsonObject

sealed interface VoiceActionEvent {
    data class Recognition(
        val update: RecognitionUpdate,
    ) : VoiceActionEvent

    data class ActionComplete(
        val transcript: String? = null,
        val response: JsonObject,
    ) : VoiceActionEvent

    data class TtsReady(
        val mediaUrl: String,
    ) : VoiceActionEvent

    data class Error(
        val code: String,
        val message: String,
    ) : VoiceActionEvent

    data class Other(
        val type: String,
    ) : VoiceActionEvent

    data class ProviderProvenance(
        val provenance: VoiceProviderProvenance,
    ) : VoiceActionEvent

    data object RunEnd : VoiceActionEvent
}

fun AssistEvent.toVoiceActionEvent(): VoiceActionEvent =
    when (this) {
        is AssistEvent.SttEnd ->
            VoiceActionEvent.Recognition(
                RecognitionUpdate.Final(words = recognizedWordsFromText(transcript)),
            )
        is AssistEvent.IntentEnd -> VoiceActionEvent.ActionComplete(response = response)
        is AssistEvent.TtsEnd -> VoiceActionEvent.TtsReady(mediaUrl)
        is AssistEvent.Error -> VoiceActionEvent.Error(code, message)
        is AssistEvent.RunEnd -> VoiceActionEvent.RunEnd
        is AssistEvent.RunStart -> VoiceActionEvent.Other("run-start")
        AssistEvent.SttStart -> VoiceActionEvent.Other("stt-start")
        is AssistEvent.Other -> VoiceActionEvent.Other(type)
    }

fun recognizedWordsFromText(text: String): List<RecognizedWord> =
    text
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .map { word -> RecognizedWord(text = word, isFinal = true) }
