package com.superdash.voice

import com.superdash.voice.pipeline.VoiceSttProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSttProviderTest {
    @Test
    fun `moonshine provider has stable settings key`() {
        assertEquals("moonshine", VoiceSttProvider.Moonshine.key)
        assertEquals(VoiceSttProvider.Moonshine, VoiceSttProvider.fromKey("moonshine"))
    }
}
