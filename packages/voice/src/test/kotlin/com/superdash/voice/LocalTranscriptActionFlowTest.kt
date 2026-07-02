package com.superdash.voice

import com.superdash.voice.action.LocalTranscriptActionFlow
import com.superdash.voice.action.TranscriptActionExecutor
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.recognizedWordsFromText
import com.superdash.voice.pipeline.LocalSttRoute
import com.superdash.voice.pipeline.VoiceProviderProvenance
import com.superdash.voice.stt.LocalSttEngine
import com.superdash.voice.stt.RecognitionUpdate
import com.superdash.voice.stt.RecognizedWord
import com.superdash.voice.stt.UnavailableLocalSttEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTranscriptActionFlowTest {
    @Test fun `nonblank final local transcript runs HA text provider`() =
        runTest {
            val response =
                buildJsonObject {
                    put("action", JsonPrimitive("light.turn_on"))
                }
            val provider =
                LocalTranscriptActionFlow(
                    localStt = FakeLocalStt(RecognitionUpdate.Final(words = recognizedWordsFromText("turn on kitchen"))),
                    transcriptActionExecutor =
                        TranscriptActionExecutor { text ->
                            flow {
                                emit(VoiceActionEvent.ActionComplete(transcript = text, response = response))
                            }
                        },
                    audioActionProvider = { error("audio fallback should not run") },
                )

            val events = provider.invoke(twoFrameAudio()).toList()

            assertEquals(
                listOf(
                    VoiceActionEvent.Recognition(RecognitionUpdate.Final(words = recognizedWordsFromText("turn on kitchen"))),
                    VoiceActionEvent.ProviderProvenance(
                        VoiceProviderProvenance.LocalStt(
                            route = LocalSttRoute.HaText,
                            transcript = "turn on kitchen",
                            reason = null,
                        ),
                    ),
                    VoiceActionEvent.ActionComplete(transcript = "turn on kitchen", response = response),
                ),
                events,
            )
        }

    @Test fun `blank local transcript replays buffered audio to HA audio provider`() =
        runTest {
            val replayed = mutableListOf<ShortArray>()
            val fallbackResponse =
                buildJsonObject {
                    put("fallback", JsonPrimitive(true))
                }
            val provider =
                LocalTranscriptActionFlow(
                    localStt = FakeLocalStt(RecognitionUpdate.Final(words = emptyList())),
                    transcriptActionExecutor = TranscriptActionExecutor { error("text provider should not run") },
                    audioActionProvider = { audio ->
                        flow {
                            audio.collect { replayed += it }
                            emit(VoiceActionEvent.ActionComplete(transcript = "ha heard it", response = fallbackResponse))
                        }
                    },
                )

            val events = provider.invoke(twoFrameAudio()).toList()

            assertEquals(2, replayed.size)
            assertEquals(
                VoiceActionEvent.ActionComplete(transcript = "ha heard it", response = fallbackResponse),
                events.last(),
            )
        }

    @Test fun `unavailable local stt replays full audio to HA audio provider`() =
        runTest {
            val replayed = mutableListOf<ShortArray>()
            val fallbackResponse =
                buildJsonObject {
                    put("fallback", JsonPrimitive(true))
                }
            val provider =
                LocalTranscriptActionFlow(
                    localStt = UnavailableLocalSttEngine("missing local runtime"),
                    transcriptActionExecutor = TranscriptActionExecutor { error("text provider should not run") },
                    audioActionProvider = { audio ->
                        flow {
                            audio.collect { replayed += it }
                            emit(VoiceActionEvent.ActionComplete(transcript = "ha heard original", response = fallbackResponse))
                        }
                    },
                )

            val events = provider.invoke(twoFrameAudio()).toList()

            assertEquals(2, replayed.size)
            assertEquals(shortArrayOf(1, 2).toList(), replayed[0].toList())
            assertEquals(shortArrayOf(3, 4).toList(), replayed[1].toList())
            assertEquals(
                VoiceActionEvent.ActionComplete(transcript = "ha heard original", response = fallbackResponse),
                events.last(),
            )
        }

    @Test fun `local stt cancellation is not converted to HA audio fallback`() =
        runTest {
            val provider =
                LocalTranscriptActionFlow(
                    localStt =
                        object : LocalSttEngine {
                            override fun recognize(audio: Flow<ShortArray>): Flow<RecognitionUpdate> =
                                flow {
                                    throw CancellationException("cancelled")
                                }
                        },
                    transcriptActionExecutor = TranscriptActionExecutor { error("text provider should not run") },
                    audioActionProvider = { error("audio fallback should not run") },
                )

            try {
                provider.invoke(twoFrameAudio()).toList()
                error("expected cancellation")
            } catch (e: CancellationException) {
                assertEquals("cancelled", e.message)
            }
        }

    @Test fun `local STT failure after consuming audio emits error instead of partial HA fallback`() =
        runTest {
            val localStt =
                localStt { audio ->
                    flow {
                        audio.collect {
                            throw IllegalStateException("native failed")
                        }
                    }
                }
            val haEvents = mutableListOf<VoiceActionEvent>()
            val provider =
                LocalTranscriptActionFlow(
                    localStt = localStt,
                    transcriptActionExecutor =
                        TranscriptActionExecutor { text ->
                            flowOf(VoiceActionEvent.ActionComplete(text, buildJsonObject {}))
                        },
                    audioActionProvider = { audio ->
                        flow {
                            audio.collect {}
                            val event = VoiceActionEvent.ActionComplete("audio fallback", buildJsonObject {})
                            haEvents += event
                            emit(event)
                        }
                    },
                )

            val events = provider(twoFrameAudio()).toList()

            assertTrue(events.single() is VoiceActionEvent.Error)
            assertTrue(haEvents.isEmpty())
        }

    @Test fun `rejected transcript falls back with full audio when local stt stops early`() =
        runTest {
            val seenByFallback = mutableListOf<ShortArray>()
            val provider =
                LocalTranscriptActionFlow(
                    localStt =
                        localStt { audio ->
                            flow {
                                audio.first()
                                emit(RecognitionUpdate.Final(words = recognizedWordsFromText("desk")))
                            }
                        },
                    transcriptActionExecutor = TranscriptActionExecutor { error("text executor should not run") },
                    audioActionProvider = { audio ->
                        flow {
                            audio.collect { frame -> seenByFallback += frame.copyOf() }
                            emit(VoiceActionEvent.ActionComplete("fallback", buildJsonObject {}))
                        }
                    },
                )

            provider(flowOf(shortArrayOf(1), shortArrayOf(2), shortArrayOf(3))).toList()

            assertEquals(3, seenByFallback.size)
            assertEquals(1, seenByFallback[0][0].toInt())
            assertEquals(2, seenByFallback[1][0].toInt())
            assertEquals(3, seenByFallback[2][0].toInt())
        }

    @Test fun `low confidence transcript replays full audio to HA audio provider`() =
        runTest {
            val seenByFallback = mutableListOf<ShortArray>()
            val provider =
                LocalTranscriptActionFlow(
                    localStt =
                        localStt { audio ->
                            flow {
                                audio.first()
                                emit(
                                    RecognitionUpdate.Final(
                                        words =
                                            listOf(
                                                RecognizedWord(text = "turn", isFinal = true, confidence = 0.1f),
                                                RecognizedWord(text = "on", isFinal = true, confidence = 0.1f),
                                            ),
                                    ),
                                )
                            }
                        },
                    transcriptActionExecutor = TranscriptActionExecutor { error("text executor should not run") },
                    audioActionProvider = { audio ->
                        flow {
                            audio.collect { frame -> seenByFallback += frame.copyOf() }
                            emit(VoiceActionEvent.ActionComplete("fallback", buildJsonObject {}))
                        }
                    },
                )

            provider(flowOf(shortArrayOf(1), shortArrayOf(2), shortArrayOf(3), shortArrayOf(4))).toList()

            assertEquals(listOf(1, 2, 3, 4), seenByFallback.map { frame -> frame[0].toInt() })
        }

    @Test fun `local stt with no final transcript replays full audio to HA audio provider`() =
        runTest {
            val seenByFallback = mutableListOf<ShortArray>()
            val provider =
                LocalTranscriptActionFlow(
                    localStt =
                        localStt { audio ->
                            flow {
                                audio.first()
                            }
                        },
                    transcriptActionExecutor = TranscriptActionExecutor { error("text executor should not run") },
                    audioActionProvider = { audio ->
                        flow {
                            audio.collect { frame -> seenByFallback += frame.copyOf() }
                            emit(VoiceActionEvent.ActionComplete("fallback", buildJsonObject {}))
                        }
                    },
                )

            provider(flowOf(shortArrayOf(7), shortArrayOf(8), shortArrayOf(9))).toList()

            assertEquals(listOf(7, 8, 9), seenByFallback.map { frame -> frame[0].toInt() })
        }

    @Test fun `wake phrase prefix is stripped before HA text provider`() =
        runTest {
            val provider =
                LocalTranscriptActionFlow(
                    localStt = FakeLocalStt(RecognitionUpdate.Final(words = recognizedWordsFromText("hey jarvis turn off kitchen"))),
                    transcriptActionExecutor =
                        TranscriptActionExecutor { text ->
                            flow {
                                emit(VoiceActionEvent.ActionComplete(transcript = text, response = buildJsonObject {}))
                            }
                        },
                    audioActionProvider = { error("audio fallback should not run") },
                )

            val events = provider.invoke(twoFrameAudio()).toList()

            assertEquals(
                VoiceActionEvent.ActionComplete(transcript = "turn off kitchen", response = buildJsonObject {}),
                events.last(),
            )
        }

    @Test fun `wake phrase with varied spacing is stripped before HA text provider`() =
        runTest {
            val provider =
                LocalTranscriptActionFlow(
                    localStt =
                        FakeLocalStt(
                            RecognitionUpdate.Final(
                                words = recognizedWordsFromText("hey   jarvis,   turn off the kitchen lights"),
                            ),
                        ),
                    transcriptActionExecutor =
                        TranscriptActionExecutor { text ->
                            flow {
                                emit(VoiceActionEvent.ActionComplete(transcript = text, response = buildJsonObject {}))
                            }
                        },
                    audioActionProvider = { error("audio fallback should not run") },
                )

            val events = provider.invoke(twoFrameAudio()).toList()

            assertEquals(
                VoiceActionEvent.ActionComplete(transcript = "turn off the kitchen lights", response = buildJsonObject {}),
                events.last(),
            )
        }

    @Test fun `wake phrase tail is stripped before HA text provider`() =
        runTest {
            val provider =
                LocalTranscriptActionFlow(
                    localStt = FakeLocalStt(RecognitionUpdate.Final(words = recognizedWordsFromText("s turn off kitchen"))),
                    transcriptActionExecutor =
                        TranscriptActionExecutor { text ->
                            flow {
                                emit(VoiceActionEvent.ActionComplete(transcript = text, response = buildJsonObject {}))
                            }
                        },
                    audioActionProvider = { error("audio fallback should not run") },
                )

            val events = provider.invoke(twoFrameAudio()).toList()

            assertEquals(
                VoiceActionEvent.ActionComplete(transcript = "turn off kitchen", response = buildJsonObject {}),
                events.last(),
            )
        }

    @Test fun `question starting with is is not stripped as wake tail`() =
        runTest {
            val provider =
                LocalTranscriptActionFlow(
                    localStt = FakeLocalStt(RecognitionUpdate.Final(words = recognizedWordsFromText("is it going to rain"))),
                    transcriptActionExecutor =
                        TranscriptActionExecutor { text ->
                            flow {
                                emit(VoiceActionEvent.ActionComplete(transcript = text, response = buildJsonObject {}))
                            }
                        },
                    audioActionProvider = { error("audio fallback should not run") },
                )

            val events = provider.invoke(twoFrameAudio()).toList()

            assertEquals(
                VoiceActionEvent.ActionComplete(transcript = "is it going to rain", response = buildJsonObject {}),
                events.last(),
            )
        }

    @Test fun `wake only local transcript falls back to HA audio provider`() =
        runTest {
            val replayed = mutableListOf<ShortArray>()
            val fallbackResponse =
                buildJsonObject {
                    put("fallback", JsonPrimitive(true))
                }
            val provider =
                LocalTranscriptActionFlow(
                    localStt = FakeLocalStt(RecognitionUpdate.Final(words = recognizedWordsFromText("hey jarvis"))),
                    transcriptActionExecutor = TranscriptActionExecutor { error("text provider should not run") },
                    audioActionProvider = { audio ->
                        flow {
                            audio.collect { replayed += it }
                            emit(VoiceActionEvent.ActionComplete(transcript = "ha heard audio", response = fallbackResponse))
                        }
                    },
                )

            val events = provider.invoke(twoFrameAudio()).toList()

            assertEquals(2, replayed.size)
            assertEquals(
                VoiceActionEvent.ActionComplete(transcript = "ha heard audio", response = fallbackResponse),
                events.last(),
            )
        }

    @Test fun `single letter local transcript falls back to HA audio provider`() =
        runTest {
            val replayed = mutableListOf<ShortArray>()
            val fallbackResponse =
                buildJsonObject {
                    put("fallback", JsonPrimitive(true))
                }
            val provider =
                LocalTranscriptActionFlow(
                    localStt = FakeLocalStt(RecognitionUpdate.Final(words = recognizedWordsFromText("S"))),
                    transcriptActionExecutor = TranscriptActionExecutor { error("text provider should not run") },
                    audioActionProvider = { audio ->
                        flow {
                            audio.collect { replayed += it }
                            emit(VoiceActionEvent.ActionComplete(transcript = "ha heard audio", response = fallbackResponse))
                        }
                    },
                )

            val events = provider.invoke(twoFrameAudio()).toList()

            assertEquals(2, replayed.size)
            assertEquals(
                VoiceActionEvent.ActionComplete(transcript = "ha heard audio", response = fallbackResponse),
                events.last(),
            )
        }

    @Test fun `single unknown token local transcript falls back to HA audio provider`() =
        runTest {
            val replayed = mutableListOf<ShortArray>()
            val fallbackResponse =
                buildJsonObject {
                    put("fallback", JsonPrimitive(true))
                }
            val provider =
                LocalTranscriptActionFlow(
                    localStt = FakeLocalStt(RecognitionUpdate.Final(words = recognizedWordsFromText("ITES"))),
                    transcriptActionExecutor = TranscriptActionExecutor { error("text provider should not run") },
                    audioActionProvider = { audio ->
                        flow {
                            audio.collect { replayed += it }
                            emit(VoiceActionEvent.ActionComplete(transcript = "ha heard audio", response = fallbackResponse))
                        }
                    },
                )

            val events = provider.invoke(twoFrameAudio()).toList()

            assertEquals(2, replayed.size)
            assertEquals(
                VoiceActionEvent.ActionComplete(transcript = "ha heard audio", response = fallbackResponse),
                events.last(),
            )
        }

    @Test fun `single known command local transcript runs HA text provider`() =
        runTest {
            val response =
                buildJsonObject {
                    put("action", JsonPrimitive("media_player.media_stop"))
                }
            val provider =
                LocalTranscriptActionFlow(
                    localStt = FakeLocalStt(RecognitionUpdate.Final(words = recognizedWordsFromText("stop"))),
                    transcriptActionExecutor =
                        TranscriptActionExecutor { text ->
                            flow {
                                emit(VoiceActionEvent.ActionComplete(transcript = text, response = response))
                            }
                        },
                    audioActionProvider = { error("audio fallback should not run") },
                )

            val events = provider.invoke(twoFrameAudio()).toList()

            assertEquals(
                VoiceActionEvent.ActionComplete(transcript = "stop", response = response),
                events.last(),
            )
        }

    @Test fun `cleaned local transcript is used for action complete transcript`() =
        runTest {
            val provider =
                LocalTranscriptActionFlow(
                    localStt = FakeLocalStt(RecognitionUpdate.Final(words = recognizedWordsFromText("s turn off kitchen"))),
                    transcriptActionExecutor =
                        TranscriptActionExecutor {
                            flow {
                                emit(VoiceActionEvent.ActionComplete(response = buildJsonObject {}))
                            }
                        },
                    audioActionProvider = { error("audio fallback should not run") },
                )

            val events = provider.invoke(twoFrameAudio()).toList()

            assertEquals(
                VoiceActionEvent.ActionComplete(transcript = "turn off kitchen", response = buildJsonObject {}),
                events.last(),
            )
        }

    private class FakeLocalStt(
        private val final: RecognitionUpdate.Final,
    ) : LocalSttEngine {
        override fun recognize(audio: Flow<ShortArray>): Flow<RecognitionUpdate> =
            flow {
                audio.collect {}
                emit(final)
            }
    }

    private fun localStt(recognizer: (Flow<ShortArray>) -> Flow<RecognitionUpdate>): LocalSttEngine =
        object : LocalSttEngine {
            override fun recognize(audio: Flow<ShortArray>): Flow<RecognitionUpdate> = recognizer(audio)
        }

    private fun twoFrameAudio(): Flow<ShortArray> =
        flow {
            emit(shortArrayOf(1, 2))
            emit(shortArrayOf(3, 4))
        }
}
