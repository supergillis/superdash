package com.superdash.voice

import com.superdash.voice.audio.ReplayableAudioBuffer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayableAudioBufferTest {
    @Test
    fun `live flow forwards every frame from the source`() =
        runTest {
            val buffer =
                ReplayableAudioBuffer(
                    source = flowOf(shortArrayOf(1), shortArrayOf(2), shortArrayOf(3)),
                    scope = backgroundScope,
                )
            val frames = buffer.liveFlow().toList()
            buffer.cancel()
            assertEquals(listOf(1, 2, 3), frames.map { it[0].toInt() })
        }

    @Test
    fun `replay flow returns every frame after awaitComplete`() =
        runTest {
            val buffer =
                ReplayableAudioBuffer(
                    source = flowOf(shortArrayOf(1), shortArrayOf(2), shortArrayOf(3)),
                    scope = backgroundScope,
                )
            buffer.liveFlow().toList()
            buffer.awaitComplete()
            val replayed = buffer.replayFlow().toList()
            buffer.cancel()
            assertEquals(listOf(1, 2, 3), replayed.map { it[0].toInt() })
            assertFalse(buffer.truncated)
        }

    @Test
    fun `bounded buffer drops oldest frames past the cap and marks truncated`() =
        runTest {
            val buffer =
                ReplayableAudioBuffer(
                    source =
                        flowOf(
                            shortArrayOf(1),
                            shortArrayOf(2),
                            shortArrayOf(3),
                            shortArrayOf(4),
                        ),
                    scope = backgroundScope,
                    maxFrames = 2,
                )
            buffer.liveFlow().toList()
            buffer.awaitComplete()
            val replayed = buffer.replayFlow().toList()
            buffer.cancel()
            assertEquals(listOf(3, 4), replayed.map { it[0].toInt() })
            assertTrue(buffer.truncated)
        }
}
