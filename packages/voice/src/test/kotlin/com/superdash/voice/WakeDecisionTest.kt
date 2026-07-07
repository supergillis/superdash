package com.superdash.voice

import com.superdash.voice.wake.WakeDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class WakeDecisionTest {
    @Test fun `not enough history is no fire`() {
        val state = WakeDecision.State(probHistory = listOf(1f, 1f, 1f, 1f), cooldownChunksRemaining = 0)
        assertEquals(WakeDecision.Outcome.NoFire, WakeDecision.classify(state))
    }

    @Test fun `cooldown blocks fire even with high probabilities`() {
        val state = WakeDecision.State(probHistory = listOf(1f, 1f, 1f, 1f, 1f), cooldownChunksRemaining = 50)
        assertEquals(WakeDecision.Outcome.NoFire, WakeDecision.classify(state))
    }

    @Test fun `average above threshold fires`() {
        val state = WakeDecision.State(probHistory = listOf(0.8f, 0.8f, 0.8f, 0.8f, 0.8f), cooldownChunksRemaining = 0)
        assertEquals(WakeDecision.Outcome.Fire, WakeDecision.classify(state))
    }

    @Test fun `average below threshold does not fire`() {
        val state = WakeDecision.State(probHistory = listOf(0.6f, 0.6f, 0.6f, 0.6f, 0.6f), cooldownChunksRemaining = 0)
        assertEquals(WakeDecision.Outcome.NoFire, WakeDecision.classify(state))
    }

    @Test fun `only the last 5 entries are considered`() {
        val state =
            WakeDecision.State(
                probHistory = listOf(0.0f, 0.0f, 0.0f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f),
                cooldownChunksRemaining = 0,
            )
        assertEquals(WakeDecision.Outcome.Fire, WakeDecision.classify(state))
    }

    @Test fun `at exactly the threshold fires`() {
        val state = WakeDecision.State(probHistory = listOf(0.7f, 0.7f, 0.7f, 0.7f, 0.7f), cooldownChunksRemaining = 0)
        assertEquals(WakeDecision.Outcome.Fire, WakeDecision.classify(state))
    }

    @Test fun `mixed window above threshold on average fires`() {
        val state = WakeDecision.State(probHistory = listOf(0.5f, 1.0f, 0.5f, 1.0f, 0.5f), cooldownChunksRemaining = 0)
        assertEquals(WakeDecision.Outcome.Fire, WakeDecision.classify(state)) // avg = 0.7
    }

    @Test fun `custom window size can fire with fewer samples`() {
        val state = WakeDecision.State(probHistory = listOf(0.8f, 0.8f, 0.8f), cooldownChunksRemaining = 0)

        assertEquals(
            WakeDecision.Outcome.Fire,
            WakeDecision.classify(state, threshold = 0.7f, windowSize = 3),
        )
    }

    @Test fun `custom threshold blocks low average`() {
        val state = WakeDecision.State(probHistory = listOf(0.8f, 0.8f, 0.8f), cooldownChunksRemaining = 0)

        assertEquals(
            WakeDecision.Outcome.NoFire,
            WakeDecision.classify(state, threshold = 0.9f, windowSize = 3),
        )
    }
}
