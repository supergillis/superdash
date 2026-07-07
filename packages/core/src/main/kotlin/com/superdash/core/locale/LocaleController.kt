package com.superdash.core.locale

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/** Reads and applies the per-app UI language via the framework LocaleManager.
 *  The OS persists the choice, so there is no separate stored setting.
 *  Per-app language needs API 33; below that the app follows the system
 *  locale, [currentLanguage] is always null, and [setLanguage] is a no-op. */
class LocaleController(
    private val context: Context,
) {
    fun isPerAppLanguageSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun currentLanguage(): SupportedLanguage? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null
        }
        return context
            .getSystemService(LocaleManager::class.java)
            ?.applicationLocales
            ?.takeUnless { it.isEmpty }
            ?.get(0)
            ?.let { SupportedLanguage.fromTag(it.toLanguageTag()) }
    }

    /** [language] null follows the system locale; otherwise forces that language. */
    fun setLanguage(language: SupportedLanguage?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            if (language == null) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.tag)
            }
    }
}
