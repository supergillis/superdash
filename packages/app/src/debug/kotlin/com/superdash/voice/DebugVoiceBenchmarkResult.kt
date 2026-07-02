package com.superdash.voice

import kotlinx.serialization.Serializable

@Serializable
data class DebugVoiceBenchmarkResult(
    val runId: String,
    val provider: String,
    val primaryModelId: String?,
    val secondaryProvider: String?,
    val transcript: String?,
    val expected: String?,
    val matched: Boolean?,
    val completed: Boolean,
    val elapsedMs: Long,
    val providerTrace: List<DebugVoiceProviderAttempt>,
)

@Serializable
data class DebugVoiceProviderAttempt(
    val provider: String,
    val modelId: String?,
    val elapsedMs: Long,
    val result: String,
)
