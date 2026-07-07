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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.superdash.R
import com.superdash.ha.HaConnectionState

@Composable
fun ConnectionStatusRow(state: HaConnectionState, entityCount: Int) {
    val (color, label) =
        when (state) {
            is HaConnectionState.Connected ->
                Color(0xFF1E8E3E) to stringResource(R.string.connection_status_connected, state.haVersion)
            HaConnectionState.Connecting -> Color(0xFFFBBC04) to stringResource(R.string.connection_status_connecting)
            HaConnectionState.Disconnected ->
                Color(0xFF9AA0A6) to
                    stringResource(R.string.connection_status_disconnected)
            is HaConnectionState.Failed ->
                Color(0xFFD93025) to stringResource(R.string.connection_status_failed, state.reason)
            is HaConnectionState.NeedsReauth ->
                Color(0xFFD93025) to
                    stringResource(R.string.connection_status_needs_reauth)
        }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent =
            if (state is HaConnectionState.Connected) {
                { Text(pluralStringResource(R.plurals.connection_status_entity_count, entityCount, entityCount)) }
            } else {
                null
            },
        trailingContent = { Box(Modifier.size(12.dp).background(color, CircleShape)) },
    )
}
