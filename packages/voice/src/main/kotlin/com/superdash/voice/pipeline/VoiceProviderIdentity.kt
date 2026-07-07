package com.superdash.voice.pipeline

data class VoiceProviderIdentity(
    val providerKey: String,
    val modelId: String?,
) {
    val stableKey: String =
        if (modelId.isNullOrBlank()) {
            providerKey
        } else {
            "$providerKey:$modelId"
        }
}

data class VoiceProviderSelection(
    val primary: VoiceProviderIdentity,
    val secondary: VoiceProviderIdentity?,
)
