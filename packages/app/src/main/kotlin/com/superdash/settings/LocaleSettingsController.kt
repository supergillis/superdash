package com.superdash.settings

import com.superdash.core.locale.SupportedLanguage

/**
 * Narrow seam over [com.superdash.locale.LocaleController] so
 * [SettingsViewModel] can be unit tested without a real Android [android.content.Context].
 */
interface LocaleSettingsController {
    fun currentLanguage(): SupportedLanguage?

    fun setLanguage(language: SupportedLanguage?)
}
