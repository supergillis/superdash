package com.superdash.icons

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MdiCodepointsTest {
    private val codepoints = MdiCodepoints(File("src/main/assets/mdi/codepoints.json").readText())

    @Test fun `codepoint resolves bare names, prefixed names and aliases and rejects unknown names`() {
        val lookups =
            listOf("doorbell-video", "mdi:doorbell-video", "mdi:writing-system-arabic", "mdi:image", "mdi:arrow-left", "mdi:no-such-icon")
                .associateWith { name -> codepoints.codepoint(name) }
        assertEquals(
            mapOf(
                "doorbell-video" to 985193,
                "mdi:doorbell-video" to 985193,
                "mdi:writing-system-arabic" to 987944,
                "mdi:image" to 0xF02E9,
                "mdi:arrow-left" to 0xF004D,
                "mdi:no-such-icon" to null,
            ),
            lookups,
        )
    }

    @Test fun `search lists prefix matches before substring matches`() {
        assertEquals(listOf("lamp-outline", "floor-lamp-outline"), codepoints.search("lamp-outline"))
        assertEquals(listOf("doorbell", "doorbell-video"), codepoints.search("mdi:DOORBELL "))
        assertEquals(emptyList<String>(), codepoints.search("zzzzzzzz"))
    }
}
