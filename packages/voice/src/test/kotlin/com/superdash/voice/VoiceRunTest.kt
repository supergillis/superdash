package com.superdash.voice

import com.superdash.voice.pipeline.VoiceProviderIdentity
import com.superdash.voice.pipeline.VoiceProviderSelection
import com.superdash.voice.pipeline.VoiceRunId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VoiceRunTest {
    @Test
    fun `new run ids are unique`() {
        val first = VoiceRunId.new()
        val second = VoiceRunId.new()

        assertNotEquals(first, second)
    }

    @Test
    fun `provider selection exposes stable key with model id`() {
        val selection =
            VoiceProviderSelection(
                primary = VoiceProviderIdentity(providerKey = "moonshine", modelId = "moonshine-base-en"),
                secondary = VoiceProviderIdentity(providerKey = "ha_assist", modelId = null),
            )

        assertEquals("moonshine:moonshine-base-en", selection.primary.stableKey)
        assertEquals("ha_assist", selection.secondary?.stableKey)
    }
}
