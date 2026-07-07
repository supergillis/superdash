package com.superdash.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.superdash.R
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageSwitchTest {
    private fun stringIn(
        tag: String,
        id: Int,
    ): String {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocale(java.util.Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config).getString(id)
    }

    @Test
    fun `voice title differs between en and nl`() {
        assertNotEquals(
            stringIn("en", R.string.settings_voice_title),
            stringIn("nl", R.string.settings_voice_title),
        )
    }
}
