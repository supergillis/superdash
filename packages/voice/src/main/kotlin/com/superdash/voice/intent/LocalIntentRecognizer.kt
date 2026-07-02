package com.superdash.voice.intent

import com.superdash.voice.intent.registry.LocalGeneratedIntentStatus
import com.superdash.voice.intent.registry.LocalIntentRegistrySnapshot

const val DEFAULT_LOCAL_INTENT_THRESHOLD = 0.88f

enum class LocalIntentStatus {
    Disabled,
    Matched,
    Unknown,
    LowConfidence,
    Unavailable,
    StaleRegistry,
    AmbiguousPhrase,
}

data class LocalIntentRecognitionResult(
    val status: LocalIntentStatus,
    val transcript: String,
    val intentId: String? = null,
    val action: LocalIntentAction? = null,
    val confidence: Float? = null,
    val threshold: Float = DEFAULT_LOCAL_INTENT_THRESHOLD,
)

fun interface LocalIntentRecognizer {
    suspend fun recognize(text: String): LocalIntentRecognitionResult
}

class LocalIntentRegistryRecognizer(
    private val registryProvider: () -> LocalIntentRegistrySnapshot,
) : LocalIntentRecognizer {
    override suspend fun recognize(text: String): LocalIntentRecognitionResult {
        val snapshot = registryProvider()
        if (!snapshot.available) {
            return LocalIntentRecognitionResult(
                status = LocalIntentStatus.StaleRegistry,
                transcript = text,
            )
        }
        val lookup = snapshot.registry.lookup(text)
        return when (lookup.status) {
            LocalGeneratedIntentStatus.Matched -> {
                val command = lookup.command
                if (command == null) {
                    LocalIntentRecognitionResult(
                        status = LocalIntentStatus.Unknown,
                        transcript = text,
                    )
                } else {
                    LocalIntentRecognitionResult(
                        status = LocalIntentStatus.Matched,
                        transcript = text,
                        intentId = command.intentId,
                        action = command.action.copy(transcript = text),
                        confidence = 1.0f,
                    )
                }
            }
            LocalGeneratedIntentStatus.Ambiguous -> {
                LocalIntentRecognitionResult(
                    status = LocalIntentStatus.AmbiguousPhrase,
                    transcript = text,
                    confidence = 1.0f,
                )
            }
            LocalGeneratedIntentStatus.Unknown -> {
                LocalIntentRecognitionResult(
                    status = LocalIntentStatus.Unknown,
                    transcript = text,
                )
            }
        }
    }
}
