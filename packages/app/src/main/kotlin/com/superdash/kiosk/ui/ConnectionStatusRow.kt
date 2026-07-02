package com.superdash.kiosk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.superdash.ha.HaConnectionState

@Composable
fun ConnectionStatusRow(state: HaConnectionState, entityCount: Int) {
    val (color, label) =
        when (state) {
            is HaConnectionState.Connected -> Color(0xFF1E8E3E) to "Connected to HA  (v" + state.haVersion + ")"
            HaConnectionState.Connecting -> Color(0xFFFBBC04) to "Connecting…"
            HaConnectionState.Disconnected -> Color(0xFF9AA0A6) to "Disconnected"
            is HaConnectionState.Failed -> Color(0xFFD93025) to "Disconnected: " + state.reason
            is HaConnectionState.NeedsReauth -> Color(0xFFD93025) to "Re-auth required"
        }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent =
            if (state is HaConnectionState.Connected) {
                { Text("$entityCount entities") }
            } else {
                null
            },
        trailingContent = { Box(Modifier.size(12.dp).background(color, CircleShape)) },
    )
}
