package com.superdash.voice

import com.superdash.voice.pipeline.VoiceSttProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class DebugSttProviderKeysTest {
    @Test
    fun `legacy sherpa debug key maps to moonshine provider`() {
        assertEquals(VoiceSttProvider.Moonshine, sttProviderFromDebugKey("sherpa_stt_ha"))
    }

    @Test
    fun `legacy whisper debug key maps to whisper provider`() {
        assertEquals(VoiceSttProvider.Whisper, sttProviderFromDebugKey("whisper_stt_ha"))
    }

    @Test
    fun `current moonshine key maps to moonshine provider`() {
        assertEquals(VoiceSttProvider.Moonshine, sttProviderFromDebugKey("moonshine"))
    }

    @Test
    fun `blank secondary provider maps to none`() {
        assertEquals(VoiceSttProvider.None, secondarySttProviderFromDebugKey(null))
        assertEquals(VoiceSttProvider.None, secondarySttProviderFromDebugKey(""))
    }

    @Test
    fun `ha assist secondary provider maps to HA Assist`() {
        assertEquals(VoiceSttProvider.HaAssist, secondarySttProviderFromDebugKey("ha_assist"))
    }
}
