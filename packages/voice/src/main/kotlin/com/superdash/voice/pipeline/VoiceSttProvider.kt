package com.superdash.voice.pipeline

import androidx.annotation.StringRes
import com.superdash.core.util.KeyedEnum
import com.superdash.core.util.keyOf
import com.superdash.voice.R

enum class VoiceSttProvider(
    override val key: String,
    @StringRes val labelRes: Int,
) : KeyedEnum {
    None(key = "none", labelRes = R.string.voice_stt_provider_none),
    HaAssist(key = "ha_assist", labelRes = R.string.voice_stt_provider_ha_assist),
    Whisper(key = "whisper", labelRes = R.string.voice_stt_provider_whisper),
    Moonshine(key = "moonshine", labelRes = R.string.voice_stt_provider_moonshine),
    ;

    companion object {
        fun fromKey(key: String): VoiceSttProvider = keyOf(key, default = HaAssist)
    }
}
