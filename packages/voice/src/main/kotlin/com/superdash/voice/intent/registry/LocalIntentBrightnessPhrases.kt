package com.superdash.voice.intent.registry

import com.superdash.voice.intent.LocalIntentDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

// Expand the full 0..100 range so phrase recognition is a pure map lookup
// at runtime — no slot extraction. Roughly 100 phrases per area-light pair;
// for a typical home this is < 5000 phrases total. When slot extraction
// lands, this loop becomes a single phrase template.
internal fun brightnessCommandsForTarget(
    definitionsById: Map<String, LocalIntentDefinition>,
    target: LocalIntentTarget,
    targetName: String,
): List<LocalGeneratedIntentCommand> {
    val definition = definitionsById["set_brightness"] ?: return emptyList()
    return (0..100).flatMap { percent ->
        brightnessCommandsForPercent(definition, target, targetName, percent)
    }
}

private fun brightnessCommandsForPercent(
    definition: LocalIntentDefinition,
    target: LocalIntentTarget,
    targetName: String,
    percent: Int,
): List<LocalGeneratedIntentCommand> {
    val phrases =
        buildList {
            for (triggerPhrase in definition.triggerPhrases) {
                add("$triggerPhrase $targetName to $percent percent")
                val word = percentWord(percent)
                if (word != null) {
                    add("$triggerPhrase $targetName to $word percent")
                }
            }
        }
    return phrases.map { phrase ->
        generatedServiceCommand(
            phrase = phrase,
            target = target,
            intentId = definition.id,
            domain = "light",
            service = "turn_on",
            serviceData =
                buildJsonObject {
                    put("brightness_pct", JsonPrimitive(percent))
                },
        )
    }
}
