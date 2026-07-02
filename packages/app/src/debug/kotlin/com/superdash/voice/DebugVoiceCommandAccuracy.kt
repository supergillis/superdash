package com.superdash.voice

private object DebugVoiceCommandAccuracyRules {
    val digitTwenty = Regex("\\b20\\b")
    val punctuation = Regex("[^a-z0-9\\s]")
    val whitespace = Regex("\\s+")
    val wakePhrasePrefixes = listOf("hey jarvis", "jarvis")
}

fun scoreVoiceCommand(
    expectedText: String,
    actualText: String,
): VoiceCommandScore {
    val expected = expectedText.normalizeVoiceCommand()
    val actual = actualText.stripWakePhrasePrefixForScore().normalizeVoiceCommand()
    return VoiceCommandScore(
        expectedNormalized = expected,
        actualNormalized = actual,
        matches = expected.comparisonForm() == actual.comparisonForm(),
    )
}

private fun String.stripWakePhrasePrefixForScore(): String {
    val trimmed = trim()
    for (prefix in DebugVoiceCommandAccuracyRules.wakePhrasePrefixes) {
        val pattern = Regex("^${Regex.escape(prefix)}(?:[\\s,.:;!?]+|$)", RegexOption.IGNORE_CASE)
        val stripped = trimmed.replaceFirst(pattern, "")
        if (stripped != trimmed) {
            return stripped.trim()
        }
    }
    return trimmed
}

private fun String.normalizeVoiceCommand(): String =
    lowercase()
        .replace(DebugVoiceCommandAccuracyRules.digitTwenty, "twenty")
        .replace(DebugVoiceCommandAccuracyRules.punctuation, " ")
        .split(DebugVoiceCommandAccuracyRules.whitespace)
        .filter { token -> token.isNotBlank() }
        .joinToString(" ")

private fun String.comparisonForm(): String =
    split(" ")
        .filter { token -> token != "the" }
        .joinToString(" ")
