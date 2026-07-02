package com.superdash.voice

data class VoiceCommandScore(
    val expectedNormalized: String,
    val actualNormalized: String,
    val matches: Boolean,
)
