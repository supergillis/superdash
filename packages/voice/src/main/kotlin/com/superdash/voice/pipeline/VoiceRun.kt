package com.superdash.voice.pipeline

import java.util.UUID

@JvmInline
value class VoiceRunId(
    val value: String,
) {
    companion object {
        fun new(): VoiceRunId = VoiceRunId(UUID.randomUUID().toString())
    }
}

data class VoiceFixtureMetadata(
    val source: String,
    val name: String,
    val expectedText: String?,
)

data class VoiceRunContext(
    val id: VoiceRunId,
    val wakeWord: String,
    val startedAtEpochMs: Long,
    val providerSelection: VoiceProviderSelection,
    val fixture: VoiceFixtureMetadata? = null,
)

sealed interface VoiceRunTerminalState {
    data class Completed(
        val transcript: String?,
    ) : VoiceRunTerminalState

    data class Speaking(
        val transcript: String?,
    ) : VoiceRunTerminalState

    data class Failed(
        val reason: String,
    ) : VoiceRunTerminalState

    data object Cancelled : VoiceRunTerminalState
}

data class VoiceRunResult(
    val context: VoiceRunContext,
    val terminalState: VoiceRunTerminalState,
    val providerTrace: List<VoiceProviderAttempt>,
) {
    val transcript: String? =
        when (terminalState) {
            is VoiceRunTerminalState.Completed -> {
                terminalState.transcript
            }
            is VoiceRunTerminalState.Speaking -> {
                terminalState.transcript
            }
            is VoiceRunTerminalState.Failed,
            VoiceRunTerminalState.Cancelled,
            -> {
                null
            }
        }
}
