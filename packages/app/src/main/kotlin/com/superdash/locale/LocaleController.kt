package com.superdash.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.superdash.core.locale.SupportedLanguage

/** Reads and applies the per-app UI language via AppCompat per-app locales.
 *  Works on all supported API levels: below 33 AppCompat wraps activity
 *  contexts and persists the choice itself (autoStoreLocales); on 33+ it
 *  forwards to the framework LocaleManager, which also auto-migrates a
 *  stored choice when the OS upgrades across 32→33. */
class LocaleController {
    fun currentLanguage(): SupportedLanguage? =
        AppCompatDelegate
            .getApplicationLocales()
            .takeUnless { it.isEmpty }
            ?.get(0)
            ?.let { SupportedLanguage.fromTag(it.toLanguageTag()) }

    /** [language] null follows the system locale; otherwise forces that language. */
    fun setLanguage(language: SupportedLanguage?) {
        AppCompatDelegate.setApplicationLocales(
            if (language == null) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.tag)
            },
        )
    }
}
