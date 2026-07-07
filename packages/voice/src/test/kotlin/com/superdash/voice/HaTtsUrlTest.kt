package com.superdash.voice

import com.superdash.voice.action.executors.resolveHaMediaUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class HaTtsUrlTest {
    @Test
    fun `relative HA TTS path resolves against base URL`() {
        assertEquals(
            "http://homeassistant.local:8123/api/tts_proxy/abc.mp3",
            resolveHaMediaUrl(
                mediaUrl = "/api/tts_proxy/abc.mp3",
                haBaseUrl = "http://homeassistant.local:8123/",
            ),
        )
    }

    @Test
    fun `absolute HA TTS URL is preserved`() {
        assertEquals(
            "https://example.com/api/tts_proxy/abc.mp3",
            resolveHaMediaUrl(
                mediaUrl = "https://example.com/api/tts_proxy/abc.mp3",
                haBaseUrl = "http://homeassistant.local:8123",
            ),
        )
    }
}
