package com.superdash.voice.wake

/** Pure decision logic for sliding-window microWakeWord inference. */
object WakeDecision {
    const val WINDOW_SIZE = 5
    const val DEFAULT_THRESHOLD = 0.7f

    data class State(
        val probHistory: List<Float>,
        val cooldownChunksRemaining: Int,
    )

    sealed interface Outcome {
        data object NoFire : Outcome

        data object Fire : Outcome
    }

    fun classify(
        state: State,
        threshold: Float = DEFAULT_THRESHOLD,
        windowSize: Int = WINDOW_SIZE,
    ): Outcome {
        if (state.cooldownChunksRemaining > 0) {
            return Outcome.NoFire
        }
        if (state.probHistory.size < windowSize) {
            return Outcome.NoFire
        }
        val avg =
            state.probHistory
                .takeLast(windowSize)
                .average()
                .toFloat()
        return if (avg >= threshold) {
            Outcome.Fire
        } else {
            Outcome.NoFire
        }
    }
}
