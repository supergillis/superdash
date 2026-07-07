package com.superdash.voice

import com.superdash.ha.HaConnectionState
import com.superdash.ha.HaTokens
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceServiceRunPolicyTest {
    private val tokens =
        HaTokens(
            accessToken = "access",
            refreshToken = "refresh",
            expiresAtEpochMs = Long.MAX_VALUE,
        )

    @Test
    fun `runs when voice is enabled and HA is authenticated`() {
        assertTrue(
            VoiceServiceRunPolicy.shouldRun(
                voiceEnabled = true,
                haUrl = "http://homeassistant.local:8123",
                tokens = tokens,
                haState = HaConnectionState.Connected(haVersion = "2026.1.0"),
            ),
        )
    }

    @Test
    fun `does not run when voice is disabled`() {
        assertFalse(
            VoiceServiceRunPolicy.shouldRun(
                voiceEnabled = false,
                haUrl = "http://homeassistant.local:8123",
                tokens = tokens,
                haState = HaConnectionState.Connected(haVersion = "2026.1.0"),
            ),
        )
    }

    @Test
    fun `does not run without HA URL`() {
        assertFalse(
            VoiceServiceRunPolicy.shouldRun(
                voiceEnabled = true,
                haUrl = null,
                tokens = tokens,
                haState = HaConnectionState.Connected(haVersion = "2026.1.0"),
            ),
        )
    }

    @Test
    fun `does not run without tokens`() {
        assertFalse(
            VoiceServiceRunPolicy.shouldRun(
                voiceEnabled = true,
                haUrl = "http://homeassistant.local:8123",
                tokens = null,
                haState = HaConnectionState.Connected(haVersion = "2026.1.0"),
            ),
        )
    }

    @Test
    fun `does not run when HA needs reauth`() {
        assertFalse(
            VoiceServiceRunPolicy.shouldRun(
                voiceEnabled = true,
                haUrl = "http://homeassistant.local:8123",
                tokens = tokens,
                haState = HaConnectionState.NeedsReauth(reason = "token_invalid"),
            ),
        )
    }
}
