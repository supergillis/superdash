package com.superdash.voice.intent

import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.executors.HaServiceCallExecutor
import com.superdash.voice.action.executors.executeAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Fan-out from a recognized [LocalIntentAction] to its executor.
 *  Open so tests can substitute fakes that do not exercise the HA service
 *  layer. The production constructor is just LocalIntentActionDispatcher(...). */
open class LocalIntentActionDispatcher(
    private val haServiceCallExecutor: HaServiceCallExecutor,
    private val skillExecutor: SkillExecutor = UnsupportedSkillExecutor(),
) {
    open fun dispatch(action: LocalIntentAction): Flow<VoiceActionEvent> =
        when (action) {
            is LocalIntentAction.ServiceCall ->
                flow { emit(haServiceCallExecutor.executeAction(action)) }
            is LocalIntentAction.SkillInvocation ->
                skillExecutor.execute(action)
        }
}

fun interface SkillExecutor {
    fun execute(action: LocalIntentAction.SkillInvocation): Flow<VoiceActionEvent>
}

class UnsupportedSkillExecutor : SkillExecutor {
    override fun execute(action: LocalIntentAction.SkillInvocation): Flow<VoiceActionEvent> =
        flowOf(
            VoiceActionEvent.Error(
                code = "skill_not_implemented",
                message = "Skill not implemented: ${action.skillId}",
            ),
        )
}
