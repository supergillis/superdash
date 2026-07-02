package com.superdash.voice

import com.superdash.voice.intent.LocalIntentCatalog
import com.superdash.voice.intent.defaultLocalIntentCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIntentCatalogTest {
    @Test fun `default catalog contains action families only`() {
        val catalog = defaultLocalIntentCatalog()

        assertEquals(
            listOf(
                "turn_on",
                "turn_off",
                "set_brightness",
            ),
            catalog.map { intent -> intent.id },
        )
    }

    @Test fun `catalog does not hard code entity names`() {
        val phrases = defaultLocalIntentCatalog().flatMap { intent -> intent.triggerPhrases }

        assertFalse(phrases.any { phrase -> phrase.contains("kitchen") })
        assertFalse(phrases.any { phrase -> phrase.contains("living room") })
        assertFalse(phrases.any { phrase -> phrase.contains("hallway") })
    }

    @Test fun `catalog lookup maps trigger phrase back to owning intent`() {
        val catalog = LocalIntentCatalog(defaultLocalIntentCatalog())

        assertEquals("turn_on", catalog.intentForPhrase("turn on")?.id)
        assertTrue(catalog.intentForPhrase("unknown command") == null)
    }
}
