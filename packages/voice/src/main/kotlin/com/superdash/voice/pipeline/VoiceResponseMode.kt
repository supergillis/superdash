package com.superdash.voice.pipeline

import androidx.annotation.StringRes
import com.superdash.core.util.KeyedEnum
import com.superdash.core.util.keyOf
import com.superdash.ha.AssistPipelineStage
import com.superdash.voice.R

enum class VoiceResponseMode(
    override val key: String,
    @StringRes val labelRes: Int,
    val assistEndStage: AssistPipelineStage,
) : KeyedEnum {
    Speak(
        key = "speak",
        labelRes = R.string.voice_response_mode_speak,
        assistEndStage = AssistPipelineStage.Tts,
    ),
    Silent(
        key = "silent",
        labelRes = R.string.voice_response_mode_silent,
        assistEndStage = AssistPipelineStage.Intent,
    ),
    Visual(
        key = "visual",
        labelRes = R.string.voice_response_mode_visual,
        assistEndStage = AssistPipelineStage.Intent,
    ),
    ;

    companion object {
        fun fromKey(key: String): VoiceResponseMode = keyOf(key, default = Speak)
    }
}
