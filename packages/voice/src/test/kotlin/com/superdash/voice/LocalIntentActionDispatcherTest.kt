package com.superdash.voice

import com.superdash.ha.HaServiceCall
import com.superdash.ha.HaServiceCallResult
import com.superdash.ha.HaServiceTarget
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.executors.HaServiceCallExecutor
import com.superdash.voice.intent.LocalIntentAction
import com.superdash.voice.intent.LocalIntentActionDispatcher
import com.superdash.voice.intent.SkillExecutor
import com.superdash.voice.intent.UnsupportedSkillExecutor
import com.superdash.voice.stt.RecognitionUpdate
import com.superdash.voice.stt.RecognizedWord
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIntentActionDispatcherTest {
    @Test fun `service call routes to HaServiceCallExecutor`() =
        runTest {
            var executed: HaServiceCall? = null
            val dispatcher =
                LocalIntentActionDispatcher(
                    haServiceCallExecutor =
                        HaServiceCallExecutor { call ->
                            executed = call
                            HaServiceCallResult(success = true, result = buildJsonObject {})
                        },
                    skillExecutor = UnsupportedSkillExecutor(),
                )

            val action =
                LocalIntentAction.ServiceCall(
                    transcript = "turn on kitchen",
                    call =
                        HaServiceCall(
                            domain = "light",
                            service = "turn_on",
                            target = HaServiceTarget(entityId = listOf("light.kitchen")),
                        ),
                )

            val event = dispatcher.dispatch(action).toList().single()

            assertEquals("light", executed?.domain)
            assertEquals("turn on kitchen", (event as VoiceActionEvent.ActionComplete).transcript)
        }

    @Test fun `skill invocation routes to SkillExecutor`() =
        runTest {
            val dispatcher =
                LocalIntentActionDispatcher(
                    haServiceCallExecutor = HaServiceCallExecutor { error("ha executor should not be called") },
                    skillExecutor =
                        SkillExecutor { action ->
                            flowOf(
                                VoiceActionEvent.ActionComplete(
                                    transcript = action.transcript,
                                    response = buildJsonObject {},
                                ),
                            )
                        },
                )

            val event =
                dispatcher
                    .dispatch(
                        LocalIntentAction.SkillInvocation(
                            transcript = "what time is it",
                            skillId = "time.current",
                        ),
                    ).toList()
                    .single()

            assertEquals("what time is it", (event as VoiceActionEvent.ActionComplete).transcript)
        }

    @Test fun `unsupported skill returns skill_not_implemented error`() =
        runTest {
            val dispatcher =
                LocalIntentActionDispatcher(
                    haServiceCallExecutor = HaServiceCallExecutor { error("ha executor should not be called") },
                    skillExecutor = UnsupportedSkillExecutor(),
                )

            val event =
                dispatcher
                    .dispatch(
                        LocalIntentAction.SkillInvocation(transcript = "x", skillId = "weather.current"),
                    ).toList()
                    .single()

            val error = event as VoiceActionEvent.Error
            assertEquals("skill_not_implemented", error.code)
        }

    @Test fun `skill executor that emits a stream of events is forwarded as a Flow`() =
        runTest {
            val dispatcher =
                LocalIntentActionDispatcher(
                    haServiceCallExecutor = HaServiceCallExecutor { error("ha executor should not be called") },
                    skillExecutor =
                        SkillExecutor { action ->
                            flow {
                                emit(
                                    VoiceActionEvent.Recognition(
                                        RecognitionUpdate.Partial(
                                            words = listOf(RecognizedWord(text = "weather query", isFinal = false)),
                                        ),
                                    ),
                                )
                                emit(
                                    VoiceActionEvent.ActionComplete(
                                        transcript = action.transcript,
                                        response = buildJsonObject {},
                                    ),
                                )
                            }
                        },
                )

            val events =
                dispatcher
                    .dispatch(
                        LocalIntentAction.SkillInvocation(
                            transcript = "what is the weather",
                            skillId = "weather.current",
                        ),
                    ).toList()

            assertEquals(2, events.size)
            assertTrue(events[0] is VoiceActionEvent.Recognition)
            assertTrue(events[1] is VoiceActionEvent.ActionComplete)
        }
}
