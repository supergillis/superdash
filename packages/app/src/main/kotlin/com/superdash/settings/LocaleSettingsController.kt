package com.superdash.settings

import com.superdash.core.locale.SupportedLanguage

/**
 * Narrow seam over [com.superdash.core.locale.LocaleController] so
 * [SettingsViewModel] can be unit tested without a real Android [android.content.Context].
 */
interface LocaleSettingsController {
    fun isPerAppLanguageSupported(): Boolean

    fun currentLanguage(): SupportedLanguage?

    fun setLanguage(language: SupportedLanguage?)
}
