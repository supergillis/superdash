package com.superdash.voice

import com.superdash.voice.audio.UtteranceTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtteranceTrackerTest {
    @Test
    fun `does not signal complete on initial silence before speech`() {
        val tracker = UtteranceTracker()
        repeat(50) {
            assertFalse("frame $it: silence-before-speech must not signal complete", tracker.next(isSpeech = false))
        }
    }

    @Test
    fun `does not signal complete during speech`() {
        val tracker = UtteranceTracker()
        repeat(50) {
            assertFalse(tracker.next(isSpeech = true))
        }
    }

    @Test
    fun `signals complete on first silence after speech`() {
        val tracker = UtteranceTracker()
        repeat(10) { assertFalse(tracker.next(isSpeech = true)) }
        assertTrue(tracker.next(isSpeech = false))
    }

    @Test
    fun `keeps signalling complete on repeated trailing silence`() {
        val tracker = UtteranceTracker()
        tracker.next(isSpeech = true)
        repeat(10) {
            assertTrue("frame $it: trailing silence stays complete", tracker.next(isSpeech = false))
        }
    }
}
