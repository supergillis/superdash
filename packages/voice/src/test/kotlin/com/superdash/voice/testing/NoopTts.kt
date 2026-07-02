package com.superdash.voice.testing

import com.superdash.voice.action.executors.TtsPlay

/** No-op [TtsPlay] for tests that exercise pipelines unrelated to playback. */
object NoopTts : TtsPlay {
    override suspend fun play(url: String) {}

    override fun stop() {}
}
