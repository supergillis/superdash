package com.superdash.voice

import com.superdash.voice.wake.WakeWordModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeWordModelTest {
    @Test fun `registry ships only hey jarvis initially`() {
        assertEquals(listOf("hey_jarvis"), WakeWordModel.supported.map { it.id })
    }

    @Test fun `hey jarvis model exposes label and asset paths`() {
        val model = WakeWordModel.require("hey_jarvis")

        assertEquals("hey_jarvis", model.id)
        assertEquals("Hey Jarvis", model.label)
        assertEquals("models/wakeword/hey_jarvis.tflite", model.assetPath)
        assertEquals("models/wakeword/hey_jarvis.json", model.manifestPath)
    }

    @Test fun `hey jarvis model exposes decision metadata`() {
        val model = WakeWordModel.require("hey_jarvis")

        assertEquals(0.97f, model.probabilityCutoff)
        assertEquals(5, model.slidingWindowAverageSize)
    }

    @Test fun `unknown model lookup returns null`() {
        assertNull(WakeWordModel.find("unknown_wake_word"))
    }

    @Test fun `supported model ids match runtime asset filenames`() {
        val assetNames = WakeWordModel.supported.map { it.assetPath.substringAfterLast("/") }

        assertEquals(listOf("hey_jarvis.tflite"), assetNames)
    }
}
