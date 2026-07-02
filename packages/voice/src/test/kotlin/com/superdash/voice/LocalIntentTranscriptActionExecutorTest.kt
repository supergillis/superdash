package com.superdash.voice

import com.superdash.ha.HaServiceCall
import com.superdash.ha.HaServiceTarget
import com.superdash.voice.action.TranscriptActionExecutor
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.executors.HaServiceCallExecutor
import com.superdash.voice.intent.LocalIntentAction
import com.superdash.voice.intent.LocalIntentActionDispatcher
import com.superdash.voice.intent.LocalIntentRecognitionResult
import com.superdash.voice.intent.LocalIntentRecognizer
import com.superdash.voice.intent.LocalIntentStatus
import com.superdash.voice.intent.LocalIntentTranscriptActionExecutor
import com.superdash.voice.pipeline.VoiceProviderProvenance
import com.superdash.voice.stt.RecognitionUpdate
import com.superdash.voice.stt.RecognizedWord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIntentTranscriptActionExecutorTest {
    private val action =
        LocalIntentAction.ServiceCall(
            transcript = "turn on kitchen lights",
            call =
                HaServiceCall(
                    domain = "light",
                    service = "turn_on",
                    target = HaServiceTarget(entityId = listOf("light.kitchen")),
                ),
        )

    @Test fun `matched generated phrase executes direct action and skips HA text`() =
        runTest {
            var executed: HaServiceCall? = null
            var haTextCalls = 0
            val executor =
                LocalIntentTranscriptActionExecutor(
                    enabled = { true },
                    recognizer = StaticRecognizer(LocalIntentStatus.Matched, action = action, intentId = "turn_on", confidence = 0.94f),
                    dispatcher =
                        FakeDispatcher(onServiceCall = { call ->
                            executed = call
                            VoiceActionEvent.ActionComplete(
                                transcript = action.transcript,
                                response = buildJsonObject { put("source", JsonPrimitive("local_intent")) },
                            )
                        }),
                    fallbackExecutor =
                        TranscriptActionExecutor { text ->
                            haTextCalls += 1
                            flowOf(VoiceActionEvent.ActionComplete(transcript = text, response = buildJsonObject {}))
                        },
                )

            val events = executor.execute("turn on kitchen lights").toList()

            assertEquals(action.call, executed)
            assertEquals(0, haTextCalls)
            assertEquals(
                LocalIntentStatus.Matched,
                events
                    .filterIsInstance<VoiceActionEvent.ProviderProvenance>()
                    .single()
                    .provenance
                    .let { provenance -> provenance as VoiceProviderProvenance.LocalIntent }
                    .status,
            )
            assertEquals("turn on kitchen lights", events.filterIsInstance<VoiceActionEvent.ActionComplete>().single().transcript)
        }

    @Test fun `disabled feature falls back to HA text`() =
        runTest {
            val executor =
                LocalIntentTranscriptActionExecutor(
                    enabled = { false },
                    recognizer = errorRecognizer(),
                    dispatcher = errorDispatcher(),
                    fallbackExecutor =
                        TranscriptActionExecutor { text ->
                            flowOf(VoiceActionEvent.ActionComplete(transcript = text, response = buildJsonObject {}))
                        },
                )

            val events = executor.execute("turn off living room lights").toList()

            assertEquals(
                LocalIntentStatus.Disabled,
                events
                    .filterIsInstance<VoiceActionEvent.ProviderProvenance>()
                    .single()
                    .provenance
                    .let { provenance -> provenance as VoiceProviderProvenance.LocalIntent }
                    .status,
            )
            assertEquals("turn off living room lights", events.filterIsInstance<VoiceActionEvent.ActionComplete>().single().transcript)
        }

    @Test fun `low confidence falls back to HA text`() =
        runTest {
            val executor =
                LocalIntentTranscriptActionExecutor(
                    enabled = { true },
                    recognizer = StaticRecognizer(LocalIntentStatus.LowConfidence, intentId = "turn_on", confidence = 0.77f),
                    dispatcher = errorDispatcher(),
                    fallbackExecutor =
                        TranscriptActionExecutor { text ->
                            flowOf(VoiceActionEvent.ActionComplete(transcript = text, response = buildJsonObject {}))
                        },
                )

            val events = executor.execute("turn on something").toList()

            val provenance =
                events
                    .filterIsInstance<VoiceActionEvent.ProviderProvenance>()
                    .single()
                    .provenance as VoiceProviderProvenance.LocalIntent
            assertEquals(LocalIntentStatus.LowConfidence, provenance.status)
            assertEquals("turn on something", events.filterIsInstance<VoiceActionEvent.ActionComplete>().single().transcript)
        }

    @Test fun `direct action failure falls back to HA text`() =
        runTest {
            val executor =
                LocalIntentTranscriptActionExecutor(
                    enabled = { true },
                    recognizer = StaticRecognizer(LocalIntentStatus.Matched, action = action, intentId = "turn_on", confidence = 0.94f),
                    dispatcher =
                        FakeDispatcher(onServiceCall = { _ ->
                            VoiceActionEvent.Error("direct-action-failed", "service call failed")
                        }),
                    fallbackExecutor =
                        TranscriptActionExecutor { text ->
                            flowOf(
                                VoiceActionEvent.ActionComplete(
                                    transcript = text,
                                    response = buildJsonObject { put("fallback", JsonPrimitive(true)) },
                                ),
                            )
                        },
                )

            val events = executor.execute("turn on kitchen lights").toList()

            val provenances =
                events
                    .filterIsInstance<VoiceActionEvent.ProviderProvenance>()
                    .map { event -> event.provenance as VoiceProviderProvenance.LocalIntent }
            assertEquals(listOf(null, "direct-action-failed"), provenances.map { provenance -> provenance.fallbackReason })
            assertEquals("turn on kitchen lights", events.filterIsInstance<VoiceActionEvent.ActionComplete>().single().transcript)
        }

    @Test fun `ambiguous phrase falls back to HA text`() =
        runTest {
            val executor =
                LocalIntentTranscriptActionExecutor(
                    enabled = { true },
                    recognizer = StaticRecognizer(LocalIntentStatus.AmbiguousPhrase, intentId = "turn_on", confidence = 0.94f),
                    dispatcher = errorDispatcher(),
                    fallbackExecutor =
                        TranscriptActionExecutor { text ->
                            flowOf(VoiceActionEvent.ActionComplete(transcript = text, response = buildJsonObject {}))
                        },
                )

            val events = executor.execute("turn on kitchen").toList()

            val provenance =
                events
                    .filterIsInstance<VoiceActionEvent.ProviderProvenance>()
                    .single()
                    .provenance as VoiceProviderProvenance.LocalIntent
            assertEquals(LocalIntentStatus.AmbiguousPhrase, provenance.status)
            assertEquals("turn on kitchen", events.filterIsInstance<VoiceActionEvent.ActionComplete>().single().transcript)
        }

    @Test fun `stale registry falls back to HA text`() =
        runTest {
            val executor =
                LocalIntentTranscriptActionExecutor(
                    enabled = { true },
                    recognizer = StaticRecognizer(LocalIntentStatus.StaleRegistry),
                    dispatcher = errorDispatcher(),
                    fallbackExecutor =
                        TranscriptActionExecutor { text ->
                            flowOf(VoiceActionEvent.ActionComplete(transcript = text, response = buildJsonObject {}))
                        },
                )

            val events = executor.execute("turn on office lights").toList()

            val provenance =
                events
                    .filterIsInstance<VoiceActionEvent.ProviderProvenance>()
                    .single()
                    .provenance as VoiceProviderProvenance.LocalIntent
            assertEquals(LocalIntentStatus.StaleRegistry, provenance.status)
            assertEquals("turn on office lights", events.filterIsInstance<VoiceActionEvent.ActionComplete>().single().transcript)
        }

    @Test fun `recognition result accepts skill invocation action`() =
        runTest {
            val action: LocalIntentAction =
                LocalIntentAction.SkillInvocation(
                    transcript = "what time is it",
                    skillId = "time.current",
                )
            val result =
                LocalIntentRecognitionResult(
                    status = LocalIntentStatus.Matched,
                    transcript = "what time is it",
                    action = action,
                )

            assertEquals("what time is it", result.action?.transcript)
            assertTrue(result.action is LocalIntentAction.SkillInvocation)
            assertEquals("time.current", (result.action as LocalIntentAction.SkillInvocation).skillId)
        }

    @Test fun `skill stream forwards recognition events and completion`() =
        runTest {
            val skillAction =
                LocalIntentAction.SkillInvocation(
                    transcript = "what is the weather",
                    skillId = "weather.current",
                )
            val executor =
                LocalIntentTranscriptActionExecutor(
                    enabled = { true },
                    recognizer =
                        StaticRecognizer(
                            LocalIntentStatus.Matched,
                            action = skillAction,
                            intentId = "weather.current",
                            confidence = 0.95f,
                        ),
                    dispatcher =
                        FakeDispatcher(onSkill = { skillInvocation ->
                            flow {
                                emit(
                                    VoiceActionEvent.Recognition(
                                        RecognitionUpdate.Partial(
                                            words = listOf(RecognizedWord(text = "weather", isFinal = false)),
                                        ),
                                    ),
                                )
                                emit(
                                    VoiceActionEvent.ActionComplete(
                                        transcript = skillInvocation.transcript,
                                        response = buildJsonObject {},
                                    ),
                                )
                            }
                        }),
                    fallbackExecutor =
                        TranscriptActionExecutor { _ ->
                            error("fallback not expected")
                        },
                )

            val events = executor.execute("what is the weather").toList()

            assertEquals(1, events.filterIsInstance<VoiceActionEvent.Recognition>().size)
            assertEquals(
                "what is the weather",
                events.filterIsInstance<VoiceActionEvent.ActionComplete>().single().transcript,
            )
        }

    @Test fun `skill stream falls back when first event is an error`() =
        runTest {
            val skillAction =
                LocalIntentAction.SkillInvocation(
                    transcript = "what is the weather",
                    skillId = "weather.current",
                )
            var fallbackCalled = 0
            val executor =
                LocalIntentTranscriptActionExecutor(
                    enabled = { true },
                    recognizer =
                        StaticRecognizer(
                            LocalIntentStatus.Matched,
                            action = skillAction,
                            intentId = "weather.current",
                            confidence = 0.95f,
                        ),
                    dispatcher =
                        FakeDispatcher(onSkill = { _ ->
                            flowOf(VoiceActionEvent.Error("skill_failed", "boom"))
                        }),
                    fallbackExecutor =
                        TranscriptActionExecutor { text ->
                            fallbackCalled += 1
                            flowOf(
                                VoiceActionEvent.ActionComplete(
                                    transcript = text,
                                    response = buildJsonObject { put("fallback", JsonPrimitive(true)) },
                                ),
                            )
                        },
                )

            val events = executor.execute("what is the weather").toList()

            assertEquals(1, fallbackCalled)
            val fallbackReasons =
                events
                    .filterIsInstance<VoiceActionEvent.ProviderProvenance>()
                    .map { event -> event.provenance as VoiceProviderProvenance.LocalIntent }
                    .map { provenance -> provenance.fallbackReason }
            assertEquals(listOf(null, "skill_failed"), fallbackReasons)
            assertEquals(
                "what is the weather",
                events.filterIsInstance<VoiceActionEvent.ActionComplete>().single().transcript,
            )
        }

    @Test fun `matched skill invocation completes via dispatcher`() =
        runTest {
            val skillAction =
                LocalIntentAction.SkillInvocation(
                    transcript = "what time is it",
                    skillId = "time.current",
                )
            val executor =
                LocalIntentTranscriptActionExecutor(
                    enabled = { true },
                    recognizer =
                        StaticRecognizer(
                            LocalIntentStatus.Matched,
                            action = skillAction,
                            intentId = "time.current",
                            confidence = 0.95f,
                        ),
                    dispatcher =
                        FakeDispatcher(onSkill = { skillInvocation ->
                            flowOf(
                                VoiceActionEvent.ActionComplete(
                                    transcript = skillInvocation.transcript,
                                    response = buildJsonObject {},
                                ),
                            )
                        }),
                    fallbackExecutor =
                        TranscriptActionExecutor { _ ->
                            error("fallback not expected")
                        },
                )

            val events = executor.execute("what time is it").toList()

            assertEquals("what time is it", events.filterIsInstance<VoiceActionEvent.ActionComplete>().single().transcript)
        }

    private class StaticRecognizer(
        private val status: LocalIntentStatus,
        private val action: LocalIntentAction? = null,
        private val intentId: String? = null,
        private val confidence: Float? = null,
    ) : LocalIntentRecognizer {
        override suspend fun recognize(text: String): LocalIntentRecognitionResult =
            LocalIntentRecognitionResult(
                status = status,
                transcript = text,
                intentId = intentId,
                action = action,
                confidence = confidence,
                threshold = 0.88f,
            )
    }

    private class FakeDispatcher(
        private val onServiceCall: suspend (HaServiceCall) -> VoiceActionEvent =
            { _ -> error("service call not expected") },
        private val onSkill: (LocalIntentAction.SkillInvocation) -> Flow<VoiceActionEvent> =
            { _ -> error("skill not expected") },
    ) : LocalIntentActionDispatcher(haServiceCallExecutor = HaServiceCallExecutor { error("not used") }) {
        override fun dispatch(action: LocalIntentAction): Flow<VoiceActionEvent> =
            when (action) {
                is LocalIntentAction.ServiceCall -> flow { emit(onServiceCall(action.call)) }
                is LocalIntentAction.SkillInvocation -> onSkill(action)
            }
    }

    private fun errorRecognizer(): LocalIntentRecognizer =
        LocalIntentRecognizer {
            error("recognizer should not run")
        }

    private fun errorDispatcher(): LocalIntentActionDispatcher =
        FakeDispatcher(
            onServiceCall = { _ -> error("service call dispatch not expected") },
            onSkill = { _ -> error("skill dispatch not expected") },
        )
}
