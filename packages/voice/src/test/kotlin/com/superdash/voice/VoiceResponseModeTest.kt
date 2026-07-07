package com.superdash.voice

import com.superdash.ha.AssistPipelineStage
import com.superdash.voice.pipeline.VoiceResponseMode
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceResponseModeTest {
    @Test fun `unknown key defaults to speak`() {
        assertEquals(VoiceResponseMode.Speak, VoiceResponseMode.fromKey("missing"))
    }

    @Test fun `silent maps to intent end stage`() {
        assertEquals(AssistPipelineStage.Intent, VoiceResponseMode.Silent.assistEndStage)
    }

    @Test fun `visual maps to intent end stage`() {
        assertEquals(AssistPipelineStage.Intent, VoiceResponseMode.Visual.assistEndStage)
    }

    @Test fun `speak maps to tts end stage`() {
        assertEquals(AssistPipelineStage.Tts, VoiceResponseMode.Speak.assistEndStage)
    }

    @Test fun `all non speaking modes disable HA TTS`() {
        val nonSpeakingModes = VoiceResponseMode.entries.filterNot { mode -> mode == VoiceResponseMode.Speak }

        assertEquals(
            listOf(AssistPipelineStage.Intent, AssistPipelineStage.Intent),
            nonSpeakingModes.map { mode -> mode.assistEndStage },
        )
    }
}
