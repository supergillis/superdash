package com.superdash.voice.action

import com.superdash.voice.stt.RecognitionUpdate

private val singleTokenCommandWords =
    setOf(
        "stop",
        "pause",
        "resume",
        "play",
        "open",
        "close",
        "lock",
        "unlock",
    )

sealed interface LocalTranscriptDecision {
    data class Accepted(
        val transcript: String,
    ) : LocalTranscriptDecision

    data class Rejected(
        val transcript: String?,
        val reason: String,
    ) : LocalTranscriptDecision
}

class LocalTranscriptDecider(
    private val minConfidence: Float = 0.6f,
) {
    fun decide(finalUpdate: RecognitionUpdate.Final?): LocalTranscriptDecision {
        if (finalUpdate == null) {
            return LocalTranscriptDecision.Rejected(
                transcript = null,
                reason = "local-stt-unavailable",
            )
        }

        val transcript = finalUpdate.text.stripWakePhrasePrefix()
        return when {
            !finalUpdate.shouldTrustLocalTranscript() -> {
                LocalTranscriptDecision.Rejected(
                    transcript = transcript,
                    reason = "local-transcript-confidence",
                )
            }
            transcript.isBlank() -> {
                LocalTranscriptDecision.Rejected(
                    transcript = transcript,
                    reason = "local-transcript-empty",
                )
            }
            !transcript.isLikelyHomeAssistantCommand() -> {
                LocalTranscriptDecision.Rejected(
                    transcript = transcript,
                    reason = "local-transcript-not-plausible",
                )
            }
            else -> {
                LocalTranscriptDecision.Accepted(transcript)
            }
        }
    }

    private fun RecognitionUpdate.Final.shouldTrustLocalTranscript(): Boolean {
        if (text.isBlank()) {
            return false
        }
        val localConfidence = confidence ?: words.mapNotNull { word -> word.confidence }.averageOrNull()
        return localConfidence == null || localConfidence >= minConfidence
    }
}

private fun List<Float>.averageOrNull(): Float? {
    if (isEmpty()) {
        return null
    }
    return sum() / size
}

private fun String.stripWakePhrasePrefix(): String {
    val trimmed = trim()
    val prefixes =
        listOf(
            "hey jarvis",
            "jarvis",
        )
    for (prefix in prefixes) {
        val pattern = Regex("^${Regex.escape(prefix)}(?:[\\s,.:;!?]+|$)", RegexOption.IGNORE_CASE)
        val stripped = trimmed.replaceFirst(pattern, "")
        if (stripped != trimmed) {
            return stripped.trim()
        }
    }
    val wakeTailPattern =
        Regex(
            "^s\\s+(?=(turn|set|switch|open|close|play|stop|pause|resume|dim|brighten|lock|unlock)\\b)",
            RegexOption.IGNORE_CASE,
        )
    // Some engines occasionally leave a lone "s" before the real command after wake-word stripping.
    return trimmed.replaceFirst(wakeTailPattern, "").trim()
}

private fun String.isLikelyHomeAssistantCommand(): Boolean {
    val tokens = tokens()
    if (tokens.size >= 2) {
        return true
    }
    val singleToken = tokens.singleOrNull() ?: return false
    return singleToken in singleTokenCommandWords
}

private fun String.tokens(): List<String> =
    lowercase()
        .split(Regex("\\s+"))
        .map { token -> token.trim { char -> !char.isLetterOrDigit() } }
        .filter { token -> token.isNotBlank() }
