package com.superdash.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandAccuracyTest {
    @Test
    fun `matches command text ignoring punctuation and case`() {
        val score = scoreVoiceCommand("Turn on the kitchen lights.", "turn on the kitchen lights")

        assertTrue(score.matches)
        assertEquals("turn on the kitchen lights", score.expectedNormalized)
        assertEquals("turn on the kitchen lights", score.actualNormalized)
    }

    @Test
    fun `strips wake phrase before scoring`() {
        val score = scoreVoiceCommand("Turn off the living room lights.", "hey jarvis turn off living room lights")

        assertTrue(score.matches)
    }

    @Test
    fun `matches hallway brightness number word and digits`() {
        val score = scoreVoiceCommand("Set the hallway brightness to twenty percent.", "set hallway brightness to 20 percent")

        assertTrue(score.matches)
    }

    @Test
    fun `does not match wrong room`() {
        val score = scoreVoiceCommand("Turn on the kitchen lights.", "turn on the hallway lights")

        assertFalse(score.matches)
    }
}
