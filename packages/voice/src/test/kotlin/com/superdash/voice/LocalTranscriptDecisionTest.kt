package com.superdash.voice

import com.superdash.voice.action.LocalTranscriptDecider
import com.superdash.voice.action.LocalTranscriptDecision
import com.superdash.voice.action.recognizedWordsFromText
import com.superdash.voice.stt.RecognitionUpdate
import com.superdash.voice.stt.RecognizedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTranscriptDecisionTest {
    private val decider = LocalTranscriptDecider(minConfidence = 0.6f)

    @Test
    fun `accepts confident home assistant command without wake phrase`() {
        val decision =
            decider.decide(
                RecognitionUpdate.Final(words = recognizedWordsFromText("Turn on desk lights")),
            )

        assertEquals(LocalTranscriptDecision.Accepted("Turn on desk lights"), decision)
    }

    @Test
    fun `strips wake phrase before accepting command`() {
        val decision =
            decider.decide(
                RecognitionUpdate.Final(words = recognizedWordsFromText("Hey Jarvis turn off desk lights")),
            )

        assertEquals(LocalTranscriptDecision.Accepted("turn off desk lights"), decision)
    }

    @Test
    fun `rejects low confidence transcript`() {
        val decision =
            decider.decide(
                RecognitionUpdate.Final(
                    words =
                        listOf(
                            RecognizedWord(text = "turn", isFinal = true, confidence = 0.2f),
                            RecognizedWord(text = "on", isFinal = true, confidence = 0.2f),
                        ),
                ),
            )

        assertEquals(
            LocalTranscriptDecision.Rejected(
                transcript = "turn on",
                reason = "local-transcript-confidence",
            ),
            decision,
        )
    }

    @Test
    fun `rejects unknown single token transcript`() {
        val decision =
            decider.decide(
                RecognitionUpdate.Final(words = recognizedWordsFromText("desk")),
            )

        assertEquals(
            LocalTranscriptDecision.Rejected(
                transcript = "desk",
                reason = "local-transcript-not-plausible",
            ),
            decision,
        )
    }

    @Test
    fun `rejects missing final transcript`() {
        val decision = decider.decide(null)

        assertTrue(decision is LocalTranscriptDecision.Rejected)
        assertEquals("local-stt-unavailable", (decision as LocalTranscriptDecision.Rejected).reason)
    }
}
