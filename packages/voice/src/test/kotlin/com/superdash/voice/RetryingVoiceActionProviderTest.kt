package com.superdash.voice

import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.retryingVoiceActionProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryingVoiceActionProviderTest {
    @Test
    fun `retries stt stream failure with captured audio`() =
        runTest {
            val attempts = mutableListOf<List<List<Short>>>()
            val response =
                buildJsonObject {
                    put("ok", JsonPrimitive(true))
                }
            val provider =
                retryingVoiceActionProvider(
                    delegate = { audio ->
                        flow {
                            attempts += audio.toLists()
                            if (attempts.size == 1) {
                                emit(VoiceActionEvent.Error("stt-stream-failed", "speech-to-text failed"))
                            } else {
                                emit(VoiceActionEvent.ActionComplete(transcript = "turn on lights", response = response))
                            }
                        }
                    },
                )

            val events = provider(twoFrameAudio()).toList()

            assertEquals(2, attempts.size)
            assertEquals(listOf(listOf(1.toShort(), 2.toShort()), listOf(3.toShort(), 4.toShort())), attempts[0])
            assertEquals(attempts[0], attempts[1])
            assertEquals(
                listOf(VoiceActionEvent.ActionComplete(transcript = "turn on lights", response = response)),
                events,
            )
        }

    @Test
    fun `paces replayed audio frames`() =
        runTest {
            var attempt = 0
            val replayFrameTimes = mutableListOf<Long>()
            val response =
                buildJsonObject {
                    put("ok", JsonPrimitive(true))
                }
            val provider =
                retryingVoiceActionProvider(
                    delegate = { audio ->
                        flow {
                            attempt += 1
                            audio.collect { frame ->
                                if (attempt == 2) {
                                    replayFrameTimes += currentTime
                                }
                            }
                            if (attempt == 1) {
                                emit(VoiceActionEvent.Error("stt-stream-failed", "speech-to-text failed"))
                            } else {
                                emit(VoiceActionEvent.ActionComplete(transcript = "turn on lights", response = response))
                            }
                        }
                    },
                )

            provider(twoFrameAudio()).toList()

            assertTrue("expected at least two replay frames", replayFrameTimes.size >= 2)
            assertTrue("expected replay frame cadence", replayFrameTimes[1] - replayFrameTimes[0] >= 10L)
        }

    @Test
    fun `surfaces stt stream failure after retry is exhausted`() =
        runTest {
            val provider =
                retryingVoiceActionProvider(
                    delegate = {
                        flow {
                            emit(VoiceActionEvent.Error("stt-stream-failed", "speech-to-text failed"))
                        }
                    },
                )

            val events = provider(twoFrameAudio()).toList()

            assertEquals(
                listOf(VoiceActionEvent.Error("stt-stream-failed", "speech-to-text failed")),
                events,
            )
        }

    private suspend fun Flow<ShortArray>.toLists(): List<List<Short>> {
        val frames = mutableListOf<List<Short>>()
        collect { frame -> frames += frame.toList() }
        return frames
    }

    private fun twoFrameAudio(): Flow<ShortArray> =
        flow {
            emit(shortArrayOf(1, 2))
            emit(shortArrayOf(3, 4))
        }
}
