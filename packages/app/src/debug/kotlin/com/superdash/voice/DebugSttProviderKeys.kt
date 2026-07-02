package com.superdash.voice

import com.superdash.voice.pipeline.VoiceSttProvider

fun sttProviderFromDebugKey(key: String): VoiceSttProvider =
    parseDebugSttProvider(key).getOrThrow()

fun secondarySttProviderFromDebugKey(key: String?): VoiceSttProvider =
    parseDebugSecondarySttProvider(key).getOrThrow()
