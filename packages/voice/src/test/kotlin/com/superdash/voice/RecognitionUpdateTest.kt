package com.superdash.voice

import com.superdash.voice.stt.RecognitionUpdate
import com.superdash.voice.stt.RecognizedWord
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionUpdateTest {
    @Test fun `partial update exposes recognized words and text`() {
        val update =
            RecognitionUpdate.Partial(
                words =
                    listOf(
                        RecognizedWord(text = "turn", isFinal = true),
                        RecognizedWord(text = "on", isFinal = false),
                    ),
            )

        assertEquals("turn on", update.text)
        assertEquals(listOf("turn", "on"), update.words.map { it.text })
    }

    @Test fun `final update carries optional confidence`() {
        val update =
            RecognitionUpdate.Final(
                words =
                    listOf(
                        RecognizedWord(text = "kitchen", confidence = 0.91f, isFinal = true),
                    ),
                confidence = 0.91f,
            )

        assertEquals("kitchen", update.text)
        assertEquals(0.91f, update.confidence)
    }
}
