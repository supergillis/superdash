package com.superdash.voice

import com.superdash.voice.pipeline.VoiceSttProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugVoiceArgsTest {
    @Test
    fun `unknown provider is rejected`() {
        val result = parseDebugSttProvider("moonshien")

        assertTrue(result.isFailure)
    }

    @Test
    fun `legacy sherpa key maps to moonshine`() {
        val result = parseDebugSttProvider("sherpa_stt_ha")

        assertEquals(VoiceSttProvider.Moonshine, result.getOrThrow())
    }
}
