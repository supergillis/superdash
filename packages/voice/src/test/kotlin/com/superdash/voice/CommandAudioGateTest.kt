package com.superdash.voice

import com.superdash.voice.audio.CommandAudioGateConfig
import com.superdash.voice.audio.SpeechDetector
import com.superdash.voice.audio.commandAudioGated
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandAudioGateTest {
    @Test fun `drops wake tail but preserves command start with speech prebuffer`() =
        runTest {
            val preRollWake = frames(count = 80, value = 1)
            val wakeTail = frames(count = 25, value = 2)
            val command = frames(count = 80, value = 3)
            val trailingSilence = frames(count = 80, value = 0)
            val lateSpeech = frames(count = 20, value = 9)
            val input = preRollWake + wakeTail + command + trailingSilence + lateSpeech

            val output =
                commandAudioGated(
                    audio = input.asFlow(),
                    config =
                        CommandAudioGateConfig(
                            dropInitialFrames = preRollWake.size + wakeTail.size,
                            speechStartPreBufferFrames = 60,
                            initialCommandTimeoutMs = 5_000,
                            trailingSilenceMs = 500,
                        ),
                    detectorFactory = {
                        FakeSpeechDetector(
                            speechByCall =
                                List(40) { true } +
                                    List(40) { false } +
                                    List(10) { true },
                        )
                    },
                ).toList()
            val values = output.map { it.first().toInt() }

            assertEquals(3, values.first())
            assertEquals(command.size, values.count { it == 3 })
            assertFalse(values.any { it == 1 })
            assertFalse(values.any { it == 2 })
            assertFalse(values.any { it == 9 })
        }

    @Test fun `preserves command start after leading command silence`() =
        runTest {
            val preRollWake = frames(count = 80, value = 1)
            val leadingSilence = frames(count = 30, value = 0)
            val command = frames(count = 80, value = 3)
            val trailingSilence = frames(count = 80, value = 0)
            val input = preRollWake + leadingSilence + command + trailingSilence

            val output =
                commandAudioGated(
                    audio = input.asFlow(),
                    config =
                        CommandAudioGateConfig(
                            dropInitialFrames = preRollWake.size,
                            speechStartPreBufferFrames = 60,
                            initialCommandTimeoutMs = 5_000,
                            trailingSilenceMs = 500,
                        ),
                    detectorFactory = {
                        FakeSpeechDetector(
                            speechByCall =
                                List(15) { false } +
                                    List(40) { true } +
                                    List(40) { false },
                        )
                    },
                ).toList()
            val values = output.map { it.first().toInt() }

            assertFalse(values.any { it == 1 })
            assertEquals(command.size, values.count { it == 3 })
            assertTrue(values.indexOf(3) <= leadingSilence.size)
        }

    @Test fun `times out when no command speech starts`() =
        runTest {
            val input = frames(count = 700, value = 0)
            val detector = FakeSpeechDetector(speechByCall = List(400) { false })

            val output =
                commandAudioGated(
                    audio = input.asFlow(),
                    config =
                        CommandAudioGateConfig(
                            dropInitialFrames = 80,
                            speechStartPreBufferFrames = 60,
                            initialCommandTimeoutMs = 1_000,
                            trailingSilenceMs = 500,
                        ),
                    detectorFactory = { detector },
                ).toList()

            assertEquals(emptyList<ShortArray>(), output)
            assertTrue(detector.closed)
        }

    @Test fun `stops before late speech after trailing silence`() =
        runTest {
            val command = frames(count = 60, value = 3)
            val trailingSilence = frames(count = 80, value = 0)
            val lateSpeech = frames(count = 20, value = 9)
            val input = command + trailingSilence + lateSpeech
            val detector =
                FakeSpeechDetector(
                    speechByCall =
                        List(30) { true } +
                            List(40) { false } +
                            List(10) { true },
                )

            val output =
                commandAudioGated(
                    audio = input.asFlow(),
                    config =
                        CommandAudioGateConfig(
                            dropInitialFrames = 0,
                            speechStartPreBufferFrames = 60,
                            initialCommandTimeoutMs = 1_000,
                            trailingSilenceMs = 500,
                        ),
                    detectorFactory = { detector },
                ).toList()
            val values = output.map { it.first().toInt() }

            assertEquals(command.size, values.count { it == 3 })
            assertFalse(values.any { it == 9 })
            assertTrue(detector.closed)
        }

    @Test fun `zero speech prebuffer still includes speech start frames`() =
        runTest {
            val command = frames(count = 10, value = 3)
            val trailingSilence = frames(count = 20, value = 0)
            val input = command + trailingSilence

            val output =
                commandAudioGated(
                    audio = input.asFlow(),
                    config =
                        CommandAudioGateConfig(
                            dropInitialFrames = 0,
                            speechStartPreBufferFrames = 0,
                            initialCommandTimeoutMs = 1_000,
                            trailingSilenceMs = 500,
                        ),
                    detectorFactory = {
                        FakeSpeechDetector(
                            speechByCall =
                                List(5) { true } +
                                    List(10) { false },
                        )
                    },
                ).toList()
            val values = output.map { it.first().toInt() }

            assertEquals(command.size, values.count { it == 3 })
        }

    private class FakeSpeechDetector(
        private val speechByCall: List<Boolean>,
    ) : SpeechDetector {
        private var calls = 0
        var closed = false
            private set

        override fun isSpeech(frame: ShortArray): Boolean {
            val value = speechByCall.getOrElse(calls) { false }
            calls += 1
            return value
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        private const val FRAME_SAMPLES = 160

        private fun frames(count: Int, value: Short): List<ShortArray> =
            List(count) { ShortArray(FRAME_SAMPLES) { value } }

        private fun List<ShortArray>.asFlow() =
            flow {
                for (frame in this@asFlow) {
                    emit(frame)
                }
            }
    }
}
