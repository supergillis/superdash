package com.superdash.voice

import com.superdash.voice.pipeline.VoiceProviderIdentity
import com.superdash.voice.pipeline.VoiceProviderSelection
import com.superdash.voice.pipeline.VoiceRunContext
import com.superdash.voice.pipeline.VoiceRunId
import com.superdash.voice.pipeline.VoiceRunResult
import com.superdash.voice.pipeline.VoiceRunTerminalState
import com.superdash.voice.recording.VoiceCommandRecording
import com.superdash.voice.recording.VoiceCommandRecordingService
import com.superdash.voice.recording.VoiceRecordingComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceRecordingComponentTest {
    @Test
    fun `component exposes transform and clear boundary`() =
        runTest {
            val saved = mutableListOf<VoiceCommandRecording>()
            val results = MutableSharedFlow<VoiceRunResult>(extraBufferCapacity = 1)
            var cleared = false
            val service =
                VoiceCommandRecordingService(
                    enabled = { true },
                    runResults = results,
                    saveRecording = { recording, _ -> saved += recording },
                    scope = this,
                )
            val component =
                VoiceRecordingComponent(
                    service = service,
                    clearRecordings = { cleared = true },
                )
            val context = testRunContext("component")

            val collectJob =
                launch {
                    component
                        .transformCommandAudio(context, listOf(shortArrayOf(1)).asFlow())
                        .toList()
                }
            collectJob.join()
            results.emit(testRunResult(context, "turn on desk lights"))
            advanceUntilIdle()
            component.clear()

            assertEquals(listOf("turn on desk lights"), saved.map { recording -> recording.metadata.transcript })
            assertTrue(cleared)
        }

    private fun testRunContext(id: String): VoiceRunContext =
        VoiceRunContext(
            id = VoiceRunId(id),
            wakeWord = "hey_jarvis",
            startedAtEpochMs = 1_000L,
            providerSelection =
                VoiceProviderSelection(
                    primary = VoiceProviderIdentity("moonshine", "moonshine-tiny-en"),
                    secondary = VoiceProviderIdentity("ha_assist", null),
                ),
        )

    private fun testRunResult(
        context: VoiceRunContext,
        transcript: String,
    ): VoiceRunResult =
        VoiceRunResult(
            context = context,
            terminalState = VoiceRunTerminalState.Completed(transcript),
            providerTrace = emptyList(),
        )
}
