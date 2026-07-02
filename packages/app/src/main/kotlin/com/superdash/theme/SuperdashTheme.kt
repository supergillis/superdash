package com.superdash.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** Shared theme used by MainActivity and SettingsActivity. Static colour scheme
 *  (no dynamic colour) so the kiosk looks identical across vendors. */
@Composable
fun SuperdashTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors =
        if (darkTheme) {
            darkColorScheme()
        } else {
            lightColorScheme()
        }
    MaterialTheme(colorScheme = colors, content = content)
}
