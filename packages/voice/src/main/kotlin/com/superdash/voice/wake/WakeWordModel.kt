package com.superdash.voice.wake

data class WakeWordModel(
    val id: String,
    val label: String,
    val assetPath: String,
    val manifestPath: String,
    val probabilityCutoff: Float,
    val slidingWindowAverageSize: Int,
) {
    companion object {
        const val DEFAULT_ID = "hey_jarvis"

        val supported =
            listOf(
                WakeWordModel(
                    id = DEFAULT_ID,
                    label = "Hey Jarvis",
                    assetPath = "models/wakeword/hey_jarvis.tflite",
                    manifestPath = "models/wakeword/hey_jarvis.json",
                    probabilityCutoff = 0.97f,
                    slidingWindowAverageSize = 5,
                ),
            )

        fun find(id: String): WakeWordModel? = supported.firstOrNull { it.id == id }

        fun require(id: String): WakeWordModel =
            find(id) ?: error("Unsupported wake word model: $id")
    }
}
