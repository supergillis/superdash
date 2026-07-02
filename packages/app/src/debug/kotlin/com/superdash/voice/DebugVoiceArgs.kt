package com.superdash.voice

import com.superdash.voice.pipeline.VoiceSttProvider

private val legacyDebugSttKeys: Map<String, VoiceSttProvider> =
    mapOf(
        // TODO 2027-Q1: drop sherpa→moonshine migration shim once existing installs have rotated
        "sherpa" to VoiceSttProvider.Moonshine,
        "sherpa_stt_ha" to VoiceSttProvider.Moonshine,
        "whisper_stt_ha" to VoiceSttProvider.Whisper,
    )

fun parseDebugSttProvider(key: String): Result<VoiceSttProvider> =
    runCatching {
        legacyDebugSttKeys[key]
            ?: VoiceSttProvider.entries.firstOrNull { provider -> provider.key == key }
            ?: error("Unknown debug STT provider: $key")
    }

fun parseDebugSecondarySttProvider(key: String?): Result<VoiceSttProvider> =
    key
        ?.takeIf { value -> value.isNotBlank() }
        ?.let(::parseDebugSttProvider)
        ?: Result.success(VoiceSttProvider.None)
