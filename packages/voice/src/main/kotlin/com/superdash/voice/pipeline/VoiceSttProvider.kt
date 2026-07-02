package com.superdash.voice.pipeline

import com.superdash.core.util.KeyedEnum
import com.superdash.core.util.keyOf

enum class VoiceSttProvider(
    override val key: String,
    val label: String,
) : KeyedEnum {
    None(key = "none", label = "None"),
    HaAssist(key = "ha_assist", label = "Home Assistant Assist"),
    Whisper(key = "whisper", label = "Local Whisper STT (experimental)"),
    Moonshine(key = "moonshine", label = "Local Moonshine STT (experimental)"),
    ;

    companion object {
        fun fromKey(key: String): VoiceSttProvider = keyOf(key, default = HaAssist)
    }
}
