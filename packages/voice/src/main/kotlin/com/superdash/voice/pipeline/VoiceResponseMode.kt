package com.superdash.voice.pipeline

import com.superdash.core.util.KeyedEnum
import com.superdash.core.util.keyOf
import com.superdash.ha.AssistPipelineStage

enum class VoiceResponseMode(
    override val key: String,
    val label: String,
    val assistEndStage: AssistPipelineStage,
) : KeyedEnum {
    Speak(
        key = "speak",
        label = "Speak",
        assistEndStage = AssistPipelineStage.Tts,
    ),
    Silent(
        key = "silent",
        label = "Silent",
        assistEndStage = AssistPipelineStage.Intent,
    ),
    Visual(
        key = "visual",
        label = "Visual",
        assistEndStage = AssistPipelineStage.Intent,
    ),
    ;

    companion object {
        fun fromKey(key: String): VoiceResponseMode = keyOf(key, default = Speak)
    }
}
