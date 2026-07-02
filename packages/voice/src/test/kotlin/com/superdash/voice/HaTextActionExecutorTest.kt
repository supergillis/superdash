package com.superdash.voice

import com.superdash.ha.AssistEvent
import com.superdash.ha.AssistPipelineStage
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.executors.HaTextActionExecutor
import com.superdash.voice.pipeline.VoiceResponseMode
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class HaTextActionExecutorTest {
    @Test
    fun `runs text pipeline with current response mode`() =
        runTest {
            val calls = mutableListOf<Pair<String, AssistPipelineStage>>()
            val executor =
                HaTextActionExecutor(
                    runTextPipeline = { text, endStage ->
                        calls += text to endStage
                        flowOf(AssistEvent.IntentEnd(buildJsonObject {}))
                    },
                    responseMode = { VoiceResponseMode.Silent },
                )

            val events = executor.execute("turn on desk lights").toList()

            assertEquals(listOf("turn on desk lights" to AssistPipelineStage.Intent), calls)
            assertEquals(listOf(VoiceActionEvent.ActionComplete(response = buildJsonObject {})), events)
        }
}
