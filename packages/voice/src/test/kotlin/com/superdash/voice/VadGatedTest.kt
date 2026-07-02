package com.superdash.voice

import com.superdash.voice.audio.SpeechDetector
import com.superdash.voice.audio.VadGateConfig
import com.superdash.voice.audio.vadGated
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VadGatedTest {
    @Test fun `ignored initial wake frames do not start trailing silence tracking`() =
        runTest {
            val preRollWakeFrames = speechFrames(80)
            val pauseBeforeCommandFrames = silenceFrames(120)
            val commandFrames = speechFrames(40)
            val trailingSilenceFrames = silenceFrames(80)
            val lateFrames = speechFrames(20)
            val frames =
                preRollWakeFrames +
                    pauseBeforeCommandFrames +
                    commandFrames +
                    trailingSilenceFrames +
                    lateFrames
            val output =
                vadGated(
                    audio = frames.asFlow(),
                    config =
                        VadGateConfig(
                            trailingSilenceMs = 500,
                            initialSilenceTimeoutMs = 2_000,
                            ignoredInitialFrames = preRollWakeFrames.size,
                        ),
                    detectorFactory = {
                        FakeSpeechDetector(
                            speechByCall =
                                List(60) { false } +
                                    List(20) { true } +
                                    List(40) { false } +
                                    List(10) { true },
                        )
                    },
                ).toList()

            assertEquals(preRollWakeFrames.size + pauseBeforeCommandFrames.size + commandFrames.size + 2, output.size)
        }

    @Test fun `long pause after wake completes before later command audio`() =
        runTest {
            val frames = silenceFrames(150) + speechFrames(20)
            val output =
                vadGated(
                    audio = frames.asFlow(),
                    config =
                        VadGateConfig(
                            trailingSilenceMs = 500,
                            initialSilenceTimeoutMs = 1_000,
                        ),
                    detectorFactory = { FakeSpeechDetector(speechByCall = List(75) { false } + List(10) { true }) },
                ).toList()

            assertEquals(100, output.size)
        }

    @Test fun `long pause mid sentence completes before second phrase`() =
        runTest {
            val firstPhraseFrames = speechFrames(60)
            val pauseFrames = silenceFrames(80)
            val secondPhraseFrames = speechFrames(40)
            val frames = firstPhraseFrames + pauseFrames + secondPhraseFrames
            val output =
                vadGated(
                    audio = frames.asFlow(),
                    config =
                        VadGateConfig(
                            trailingSilenceMs = 500,
                            initialSilenceTimeoutMs = 1_000,
                        ),
                    detectorFactory = {
                        FakeSpeechDetector(
                            speechByCall =
                                List(30) { true } +
                                    List(25) { true } +
                                    List(15) { false } +
                                    List(20) { true },
                        )
                    },
                ).toList()

            assertEquals(firstPhraseFrames.size + 52, output.size)
        }

    private class FakeSpeechDetector(
        private val speechByCall: List<Boolean>,
    ) : SpeechDetector {
        private var calls = 0

        override fun isSpeech(frame: ShortArray): Boolean {
            val value = speechByCall.getOrElse(calls) { false }
            calls += 1
            return value
        }

        override fun close() {}
    }

    private companion object {
        private const val FRAME_SAMPLES = 160

        private fun silenceFrames(count: Int): List<ShortArray> = List(count) { ShortArray(FRAME_SAMPLES) }

        private fun speechFrames(count: Int): List<ShortArray> = List(count) { ShortArray(FRAME_SAMPLES) { 1_000 } }

        private fun List<ShortArray>.asFlow() =
            flow {
                for (frame in this@asFlow) {
                    emit(frame)
                }
            }
    }
}
