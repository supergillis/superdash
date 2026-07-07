package com.superdash.core.locale

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/** Reads and applies the per-app UI language via the framework LocaleManager.
 *  The OS persists the choice, so there is no separate stored setting. */
class LocaleController(
    context: Context,
) {
    private val manager = context.getSystemService(LocaleManager::class.java)

    fun currentLanguage(): SupportedLanguage? =
        manager.applicationLocales
            .takeUnless { it.isEmpty }
            ?.get(0)
            ?.let { SupportedLanguage.fromTag(it.toLanguageTag()) }

    /** [language] null follows the system locale; otherwise forces that language. */
    fun setLanguage(language: SupportedLanguage?) {
        manager.applicationLocales =
            if (language == null) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.tag)
            }
    }
}
