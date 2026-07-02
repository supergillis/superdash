package com.superdash.voice.intent

data class LocalIntentDefinition(
    val id: String,
    val triggerPhrases: List<String>,
)

class LocalIntentCatalog(
    definitions: List<LocalIntentDefinition>,
) {
    private val definitionsByPhrase: Map<String, LocalIntentDefinition> =
        definitions
            .flatMap { definition ->
                definition.triggerPhrases.map { phrase -> normalizeLocalIntentPhrase(phrase) to definition }
            }.toMap()

    fun intentForPhrase(phrase: String): LocalIntentDefinition? =
        definitionsByPhrase[normalizeLocalIntentPhrase(phrase)]
}

fun defaultLocalIntentCatalog(): List<LocalIntentDefinition> =
    listOf(
        LocalIntentDefinition(
            id = "turn_on",
            triggerPhrases =
                listOf(
                    "turn on",
                    "switch on",
                    "enable",
                ),
        ),
        LocalIntentDefinition(
            id = "turn_off",
            triggerPhrases =
                listOf(
                    "turn off",
                    "switch off",
                    "disable",
                ),
        ),
        LocalIntentDefinition(
            id = "set_brightness",
            triggerPhrases =
                listOf(
                    "set",
                    "dim",
                ),
        ),
    )

internal fun normalizeLocalIntentPhrase(phrase: String): String =
    phrase
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
