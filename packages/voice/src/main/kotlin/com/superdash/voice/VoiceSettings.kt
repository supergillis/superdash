package com.superdash.voice

import kotlinx.coroutines.flow.Flow

/**
 * Typed settings view owned by the voice feature.
 *
 * Lives in the feature package so the feature never imports the persistence
 * layer. The `app` module provides the implementation. Provider/wake-word/response
 * normalization happens inside the impl using the feature's own enums.
 */
interface VoiceSettings {
    val enabled: Flow<Boolean>

    val activeWakeWord: Flow<String>

    val assistProvider: Flow<String>

    val primarySttProvider: Flow<String>

    val secondarySttProvider: Flow<String>

    val selectedSttModelId: Flow<String>

    val selectedIntentEmbeddingModelId: Flow<String>

    val localIntentRecognizerEnabled: Flow<Boolean>

    val responseMode: Flow<String>

    val commandRecordingEnabled: Flow<Boolean>

    val commandRecordingRetention: Flow<Int>

    val vadSilenceMs: Flow<Int>

    suspend fun setEnabled(value: Boolean)

    suspend fun setActiveWakeWord(value: String)

    suspend fun setAssistProvider(value: String)

    suspend fun setPrimarySttProvider(value: String)

    suspend fun setSecondarySttProvider(value: String)

    suspend fun setSelectedSttModelId(value: String)

    suspend fun setSelectedIntentEmbeddingModelId(value: String)

    suspend fun setLocalIntentRecognizerEnabled(value: Boolean)

    suspend fun setResponseMode(value: String)

    suspend fun setCommandRecordingEnabled(value: Boolean)

    suspend fun setCommandRecordingRetention(value: Int)

    suspend fun setVadSilenceMs(value: Int)
}
