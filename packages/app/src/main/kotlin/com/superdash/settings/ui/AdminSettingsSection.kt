package com.superdash.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superdash.settings.AdminSettingsActions

@Composable
fun AdminSettingsSection(
    actions: AdminSettingsActions,
) {
    Button(
        onClick = actions.onBatteryHelp,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text("Battery saving help") }
    Button(
        onClick = actions.onOpenWsDebug,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text("Show WS debug log") }
}
