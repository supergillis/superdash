package com.superdash.voice.intent.registry

import com.superdash.voice.intent.LocalIntentAction
import com.superdash.voice.intent.normalizeLocalIntentPhrase

enum class LocalGeneratedIntentStatus {
    Matched,
    Ambiguous,
    Unknown,
}

enum class LocalGeneratedIntentTargetKind {
    Area,
    Entity,
}

data class LocalGeneratedIntentCommand(
    val phrase: String,
    val targetId: String,
    val targetKind: LocalGeneratedIntentTargetKind,
    val intentId: String,
    val action: LocalIntentAction.ServiceCall,
) {
    val entityId: String
        get() = targetId
}

data class LocalGeneratedIntentLookup(
    val status: LocalGeneratedIntentStatus,
    val command: LocalGeneratedIntentCommand? = null,
    val candidates: List<String> = emptyList(),
)

class LocalIntentRegistry(
    commands: List<LocalGeneratedIntentCommand>,
) {
    private val commandsByPhrase: Map<String, List<LocalGeneratedIntentCommand>> =
        commands
            .groupBy { command -> normalizeLocalIntentPhrase(command.phrase) }
            .mapValues { entry -> prioritizedCommands(entry.value) }

    fun lookup(phrase: String): LocalGeneratedIntentLookup {
        val candidates = commandsByPhrase[normalizeLocalIntentPhrase(phrase)].orEmpty()
        return when (candidates.size) {
            0 -> {
                LocalGeneratedIntentLookup(status = LocalGeneratedIntentStatus.Unknown)
            }
            1 -> {
                LocalGeneratedIntentLookup(
                    status = LocalGeneratedIntentStatus.Matched,
                    command = candidates.single(),
                    candidates = listOf(candidates.single().targetId),
                )
            }
            else -> {
                LocalGeneratedIntentLookup(
                    status = LocalGeneratedIntentStatus.Ambiguous,
                    candidates = candidates.map { command -> command.targetId },
                )
            }
        }
    }

    fun commandForPhrase(phrase: String): LocalGeneratedIntentCommand? = lookup(phrase).command
}

private fun prioritizedCommands(commands: List<LocalGeneratedIntentCommand>): List<LocalGeneratedIntentCommand> {
    val areaCommands = commands.filter { command -> command.targetKind == LocalGeneratedIntentTargetKind.Area }
    return if (areaCommands.isNotEmpty()) {
        areaCommands
    } else {
        commands
    }
}
