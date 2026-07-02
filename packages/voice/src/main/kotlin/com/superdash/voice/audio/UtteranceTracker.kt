package com.superdash.voice.audio

/** Pure state machine layered on top of a VAD: tracks whether the user has
 *  spoken at all, then signals "complete" on the first trailing-silence frame.
 *  Separated from [Vad] so it can be unit-tested without the JNI backend.
 *  Reused by [CommandAudioGate] for the same had-speech-and-now-silent rule. */
internal class UtteranceTracker {
    private var hadSpeech = false

    fun next(isSpeech: Boolean): Boolean {
        if (isSpeech) {
            hadSpeech = true
            return false
        }
        return hadSpeech
    }
}
