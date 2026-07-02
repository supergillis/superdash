package com.superdash.voice.intent

import com.superdash.ha.HaServiceCall

sealed interface LocalIntentAction {
    val transcript: String

    data class ServiceCall(
        override val transcript: String,
        val call: HaServiceCall,
    ) : LocalIntentAction

    data class SkillInvocation(
        override val transcript: String,
        val skillId: String,
    ) : LocalIntentAction
}
