package com.superdash.settings.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.superdash.R
import com.superdash.core.locale.SupportedLanguage
import com.superdash.settings.GeneralSettingsActions
import com.superdash.settings.GeneralSettingsState
import com.superdash.settings.SettingsChoice
import com.superdash.settings.SettingsChoiceRow

@Composable
fun GeneralSettingsSection(
    state: GeneralSettingsState,
    actions: GeneralSettingsActions,
) {
    if (!state.languagePickerAvailable) {
        Text(
            text = stringResource(R.string.settings_language_unavailable),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    val choices =
        listOf(
            SettingsChoice<SupportedLanguage?>(
                value = null,
                label = stringResource(R.string.settings_language_system_default),
            ),
        ) +
            SupportedLanguage.entries.map { language ->
                SettingsChoice<SupportedLanguage?>(value = language, label = language.nativeName)
            }

    SettingsChoiceRow(
        label = stringResource(R.string.settings_language_title),
        choices = choices,
        selectedValue = state.currentLanguage,
        onSelect = actions.onSelectLanguage,
    )
}
