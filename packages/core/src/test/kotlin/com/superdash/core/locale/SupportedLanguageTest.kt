package com.superdash.core.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportedLanguageTest {
    @Test fun `fromTag matches exact language tag`() {
        assertEquals(SupportedLanguage.DUTCH, SupportedLanguage.fromTag("nl"))
    }

    @Test fun `fromTag matches region-qualified tag by language`() {
        assertEquals(SupportedLanguage.DUTCH, SupportedLanguage.fromTag("nl-BE"))
    }

    @Test fun `fromTag returns null for unsupported or null`() {
        assertNull(SupportedLanguage.fromTag("de"))
        assertNull(SupportedLanguage.fromTag(null))
    }

    @Test fun `native names are set`() {
        assertEquals("Français", SupportedLanguage.FRENCH.nativeName)
    }
}
