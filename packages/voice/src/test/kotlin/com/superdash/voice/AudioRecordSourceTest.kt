package com.superdash.voice

import com.superdash.voice.audio.AudioRecordSession
import com.superdash.voice.audio.downsampleLinear
import com.superdash.voice.audio.frameSamplesForRate
import com.superdash.voice.audio.recordingSessionsFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioRecordSourceTest {
    @Test
    fun `downsampleLinear keeps audio unchanged when rates match`() {
        val input = shortArrayOf(0, 10, -10, 20)

        val output = downsampleLinear(input, fromRate = 16000, toRate = 16000)

        assertArrayEquals(input, output)
    }

    @Test
    fun `downsampleLinear reduces 44100Hz audio to 16000Hz`() {
        val input = ShortArray(441) { index -> index.toShort() }

        val output = downsampleLinear(input, fromRate = 44100, toRate = 16000)

        assertEquals(160, output.size)
        assertEquals(0, output.first().toInt())
        assertTrue(output.last() > 430)
    }

    @Test
    fun `frameSamplesForRate uses ten milliseconds of the actual sample rate`() {
        assertEquals(160, frameSamplesForRate(16000))
        assertEquals(441, frameSamplesForRate(44100))
        assertEquals(480, frameSamplesForRate(48000))
    }

    @Test
    fun `downsampleLinear reduces 48000Hz ten millisecond frame to 160 samples`() {
        val input = ShortArray(480) { index -> index.toShort() }

        val output = downsampleLinear(input, fromRate = 48000, toRate = 16000)

        assertEquals(160, output.size)
    }

    @Test
    fun `emits zero length restart sentinel between consecutive sessions`() =
        runTest {
            val sessions = ArrayDeque<AudioRecordSession>()
            sessions += scriptedSession(framesToReturn = 2)
            sessions += scriptedSession(framesToReturn = 2)

            val frames =
                recordingSessionsFlow(openSession = { sessions.removeFirstOrNull() })
                    .take(5)
                    .toList()

            assertEquals("two frames then sentinel then two frames", 5, frames.size)
            assertEquals("first session frame is full", 160, frames[0].size)
            assertEquals("second session frame is full", 160, frames[1].size)
            assertEquals("sentinel is zero length", 0, frames[2].size)
            assertEquals("third frame is full again", 160, frames[3].size)
            assertEquals(160, frames[4].size)
        }

    @Test
    fun `does not emit sentinel before the first session emits any frame`() =
        runTest {
            val openCalls = intArrayOf(0)
            val openSession = {
                openCalls[0] = openCalls[0] + 1
                when (openCalls[0]) {
                    1 -> scriptedSession(framesToReturn = 1)
                    else -> null
                }
            }

            val frames =
                recordingSessionsFlow(openSession)
                    .take(1)
                    .toList()

            assertEquals("only the real frame is emitted", 1, frames.size)
            assertEquals(160, frames[0].size)
        }

    private fun scriptedSession(framesToReturn: Int): AudioRecordSession {
        var framesRead = 0
        return AudioRecordSession(
            sampleRate = 16000,
            read = { buffer, offset, size ->
                if (framesRead >= framesToReturn) {
                    -1
                } else {
                    framesRead += 1
                    for (i in 0 until size) {
                        buffer[offset + i] = 1
                    }
                    size
                }
            },
            release = { },
        )
    }
}
