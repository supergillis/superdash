package com.superdash.voice.pipeline

import com.superdash.core.log.Log
import com.superdash.voice.action.VoiceActionProvider

private val log = Log("VoiceProviderRegistry")

class VoiceProviderRegistry(
    private val providerFactory: (VoiceProviderIdentity) -> ResolvedVoiceProvider?,
) {
    private val providerCache = mutableMapOf<String, ResolvedVoiceProvider>()

    @Synchronized
    fun resolve(identity: VoiceProviderIdentity): ResolvedVoiceProvider? =
        providerCache.getOrPut(identity.stableKey) {
            providerFactory(identity) ?: return null
        }

    // suspend so engine.close() can suspend on its in-flight mutex without
    // tying up the calling dispatcher (e.g. the selectedSttModelId collector)
    // for the duration of an active multi-second transcription.
    suspend fun evict(providerKey: String) {
        val toClose: List<Pair<String, ResolvedVoiceProvider>>
        synchronized(this) {
            val keys = providerCache.keys.filter { stableKey -> stableKey.substringBefore(":") == providerKey }
            toClose = keys.mapNotNull { key -> providerCache.remove(key)?.let { entry -> key to entry } }
        }
        for ((key, entry) in toClose) {
            runCatching { entry.closeable?.invoke() }
                .onFailure { throwable -> log.w("close failed", throwable, "stableKey" to key) }
        }
    }

    suspend fun closeAll() {
        val toClose: List<ResolvedVoiceProvider>
        synchronized(this) {
            toClose = providerCache.values.toList()
            providerCache.clear()
        }
        for (entry in toClose) {
            runCatching { entry.closeable?.invoke() }
                .onFailure { throwable ->
                    log.w("close failed", throwable, "stableKey" to entry.identity.stableKey)
                }
        }
    }
}

data class ResolvedVoiceProvider(
    val identity: VoiceProviderIdentity,
    val provider: VoiceActionProvider,
    val closeable: (suspend () -> Unit)? = null,
)
