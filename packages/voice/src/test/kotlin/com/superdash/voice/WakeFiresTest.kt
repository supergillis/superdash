package com.superdash.voice

import com.superdash.voice.wake.WakeEvent
import com.superdash.voice.wake.wakeFires
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WakeFiresTest {
    @Test
    fun `every wake fire reaches a slow collector`() =
        runTest {
            val frames = (1..300).map { shortArrayOf(it.toShort()) }

            val fires =
                wakeFires(frames.asFlow()) { samples -> WakeEvent("fire-${samples[0]}") }
                    .onEach { delay(1) }
                    .toList()

            assertEquals((1..300).map { WakeEvent("fire-$it") }, fires)
        }
}
