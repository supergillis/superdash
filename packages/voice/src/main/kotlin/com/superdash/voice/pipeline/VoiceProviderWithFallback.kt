package com.superdash.voice.pipeline

import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.audio.ReplayableAudioBuffer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

class VoiceProviderWithFallback(
    private val providerRegistry: VoiceProviderRegistry,
) : VoiceProviderRunner {
    override fun run(
        selection: VoiceProviderSelection,
        audio: Flow<ShortArray>,
    ): Flow<VoiceProviderRunEvent> =
        flow {
            val secondaryIdentity =
                selection.secondary
                    ?.takeIf { identity -> identity.stableKey != selection.primary.stableKey }
            if (secondaryIdentity == null) {
                runProvider(
                    identity = selection.primary,
                    audio = audio,
                    onAttempt = { attempt -> emit(VoiceProviderRunEvent.AttemptFinished(attempt)) },
                    onEvent = { event -> emit(VoiceProviderRunEvent.Action(event)) },
                )
                return@flow
            }

            coroutineScope {
                val replayableAudio = ReplayableAudioBuffer(audio, this)
                try {
                    val primaryResult =
                        runProvider(
                            identity = selection.primary,
                            audio = replayableAudio.liveFlow(),
                            emitErrors = false,
                            onAttempt = { attempt -> emit(VoiceProviderRunEvent.AttemptFinished(attempt)) },
                            onEvent = { event -> emit(VoiceProviderRunEvent.Action(event)) },
                        )
                    if (primaryResult.completedAction) {
                        return@coroutineScope
                    }

                    replayableAudio.awaitComplete()
                    runProvider(
                        identity = secondaryIdentity,
                        audio = replayableAudio.replayFlow(),
                        onAttempt = { attempt -> emit(VoiceProviderRunEvent.AttemptFinished(attempt)) },
                        onEvent = { event -> emit(VoiceProviderRunEvent.Action(event)) },
                    )
                } finally {
                    replayableAudio.cancel()
                }
            }
        }

    private suspend fun runProvider(
        identity: VoiceProviderIdentity,
        audio: Flow<ShortArray>,
        emitErrors: Boolean = true,
        onAttempt: suspend (VoiceProviderAttempt) -> Unit,
        onEvent: suspend (VoiceActionEvent) -> Unit,
    ): ProviderRunResult {
        val startNs = System.nanoTime()
        val provider = providerRegistry.resolve(identity)
        if (provider == null) {
            val event = providerMissing(identity)
            val attempt =
                VoiceProviderAttempt(
                    identity = identity,
                    elapsedMs = elapsedMs(startNs),
                    result = VoiceProviderAttemptResult.Skipped(event.message),
                )
            onAttempt(attempt)
            if (emitErrors) {
                onEvent(event)
            }
            return ProviderRunResult(
                attempt = attempt,
                completedAction = false,
            )
        }

        val events = mutableListOf<VoiceActionEvent>()
        val provenance = mutableListOf<VoiceProviderProvenance>()
        var earlyResult: ProviderRunResult? = null
        // transformWhile lets us terminate the collection by returning false from the
        // predicate, which keeps the flow contract intact for any wrapping operators.
        // The previous implementation threw a sentinel exception to break out of
        // `collect`, which would be silently swallowed by any operator that catches
        // throwables (e.g. retry/catch) and corrupt the fallback decision.
        provider
            .provider(audio)
            .transformWhile<VoiceActionEvent, Unit> { event ->
                when (event) {
                    is VoiceActionEvent.ProviderProvenance -> {
                        provenance += event.provenance
                        true
                    }
                    is VoiceActionEvent.Error -> {
                        events += event
                        val attempt = providerAttempt(provider.identity, startNs, events, provenance)
                        onAttempt(attempt)
                        if (emitErrors) {
                            onEvent(event)
                        }
                        earlyResult =
                            ProviderRunResult(
                                attempt = attempt,
                                completedAction =
                                    events.any { collectedEvent ->
                                        collectedEvent is VoiceActionEvent.ActionComplete
                                    },
                            )
                        false
                    }
                    else -> {
                        events += event
                        onEvent(event)
                        true
                    }
                }
            }.collect {}
        earlyResult?.let { result ->
            return result
        }
        val attempt = providerAttempt(provider.identity, startNs, events, provenance)
        onAttempt(attempt)
        return ProviderRunResult(
            attempt = attempt,
            completedAction = events.any { event -> event is VoiceActionEvent.ActionComplete },
        )
    }

    private fun providerAttempt(
        identity: VoiceProviderIdentity,
        startNs: Long,
        events: List<VoiceActionEvent>,
        provenance: List<VoiceProviderProvenance>,
    ): VoiceProviderAttempt {
        val completedAction = events.any { event -> event is VoiceActionEvent.ActionComplete }
        val error = events.filterIsInstance<VoiceActionEvent.Error>().lastOrNull()
        val attemptResult =
            if (error != null) {
                VoiceProviderAttemptResult.Failed("${error.code}: ${error.message}")
            } else {
                VoiceProviderAttemptResult.Completed(actionComplete = completedAction)
            }
        return VoiceProviderAttempt(
            identity = identity,
            elapsedMs = elapsedMs(startNs),
            result = attemptResult,
            provenance = provenance,
        )
    }

    private fun providerMissing(identity: VoiceProviderIdentity): VoiceActionEvent.Error =
        VoiceActionEvent.Error("voice-provider-missing", "Voice provider missing: ${identity.stableKey}")

    private fun elapsedMs(startNs: Long): Long =
        (System.nanoTime() - startNs) / 1_000_000L
}

private data class ProviderRunResult(
    val attempt: VoiceProviderAttempt,
    val completedAction: Boolean,
)
