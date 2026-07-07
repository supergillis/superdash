package com.superdash.voice.intent.registry

import com.superdash.ha.HaServiceCall
import com.superdash.ha.HaServiceTarget
import com.superdash.voice.intent.LocalIntentAction
import com.superdash.voice.intent.LocalIntentDefinition
import kotlinx.serialization.json.JsonObject

internal data class LocalIntentTarget(
    val id: String,
    val kind: LocalGeneratedIntentTargetKind,
    val names: List<String>,
    val entityIds: List<String> = emptyList(),
)

internal fun LocalIntentTarget.toHaServiceTarget(): HaServiceTarget =
    when (kind) {
        LocalGeneratedIntentTargetKind.Area -> {
            // Resolve areas to entity IDs locally so unexposed entities are never included in direct actions.
            HaServiceTarget(entityId = entityIds)
        }
        LocalGeneratedIntentTargetKind.Entity -> {
            HaServiceTarget(entityId = listOf(id))
        }
    }

internal fun generatedServiceCommand(
    phrase: String,
    target: LocalIntentTarget,
    intentId: String,
    domain: String,
    service: String,
    serviceData: JsonObject? = null,
): LocalGeneratedIntentCommand =
    LocalGeneratedIntentCommand(
        phrase = phrase,
        targetId = target.id,
        targetKind = target.kind,
        intentId = intentId,
        action =
            LocalIntentAction.ServiceCall(
                transcript = phrase,
                call =
                    HaServiceCall(
                        domain = domain,
                        service = service,
                        serviceData = serviceData,
                        target = target.toHaServiceTarget(),
                    ),
            ),
    )

internal fun serviceCommandsForDefinition(
    definition: LocalIntentDefinition?,
    target: LocalIntentTarget,
    targetName: String,
    domain: String,
    service: String,
): List<LocalGeneratedIntentCommand> {
    if (definition == null) {
        return emptyList()
    }
    return definition.triggerPhrases.map { triggerPhrase ->
        generatedServiceCommand(
            phrase = "$triggerPhrase $targetName",
            target = target,
            intentId = definition.id,
            domain = domain,
            service = service,
        )
    }
}

internal fun areaGenericCommands(
    target: LocalIntentTarget,
    intentDefinitions: List<LocalIntentDefinition>,
): List<LocalGeneratedIntentCommand> {
    val definitionsById = intentDefinitions.associateBy { definition -> definition.id }
    return target.names.flatMap { areaName ->
        serviceCommandsForDefinition(
            definition = definitionsById["turn_on"],
            target = target,
            targetName = areaName,
            domain = "homeassistant",
            service = "turn_on",
        ) +
            serviceCommandsForDefinition(
                definition = definitionsById["turn_off"],
                target = target,
                targetName = areaName,
                domain = "homeassistant",
                service = "turn_off",
            )
    }
}

internal fun areaLightCommands(
    target: LocalIntentTarget,
    intentDefinitions: List<LocalIntentDefinition>,
): List<LocalGeneratedIntentCommand> {
    val definitionsById = intentDefinitions.associateBy { definition -> definition.id }
    return target.names.flatMap { areaName ->
        val targetName = "$areaName lights"
        serviceCommandsForDefinition(
            definition = definitionsById["turn_on"],
            target = target,
            targetName = targetName,
            domain = "light",
            service = "turn_on",
        ) +
            serviceCommandsForDefinition(
                definition = definitionsById["turn_off"],
                target = target,
                targetName = targetName,
                domain = "light",
                service = "turn_off",
            ) +
            brightnessCommandsForTarget(definitionsById, target, targetName)
    }
}

internal fun entityCommands(
    target: LocalIntentTarget,
    domain: String,
    entityName: String,
    intentDefinitions: List<LocalIntentDefinition>,
): List<LocalGeneratedIntentCommand> {
    val definitionsById = intentDefinitions.associateBy { definition -> definition.id }
    val commands = mutableListOf<LocalGeneratedIntentCommand>()
    if (domain == "light" || domain == "switch") {
        commands +=
            serviceCommandsForDefinition(
                definition = definitionsById["turn_on"],
                target = target,
                targetName = entityName,
                domain = domain,
                service = "turn_on",
            )
        commands +=
            serviceCommandsForDefinition(
                definition = definitionsById["turn_off"],
                target = target,
                targetName = entityName,
                domain = domain,
                service = "turn_off",
            )
    }
    if (domain == "light") {
        commands += brightnessCommandsForTarget(definitionsById, target, entityName)
    }
    return commands
}
