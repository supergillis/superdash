package com.superdash.voice.stt

data class RecognizedWord(
    val text: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val confidence: Float? = null,
    val isFinal: Boolean,
)

sealed interface RecognitionUpdate {
    val words: List<RecognizedWord>
    val text: String

    data class Partial(
        override val words: List<RecognizedWord>,
    ) : RecognitionUpdate {
        override val text: String = words.joinToString(" ") { it.text }
    }

    data class Final(
        override val words: List<RecognizedWord>,
        val confidence: Float? = null,
    ) : RecognitionUpdate {
        override val text: String = words.joinToString(" ") { it.text }
    }
}
