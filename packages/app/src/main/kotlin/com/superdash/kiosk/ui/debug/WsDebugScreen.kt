package com.superdash.kiosk.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.superdash.ha.HaConnectionState
import com.superdash.ha.HaWebSocketClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class WsDebugUiState(
    val haState: HaConnectionState,
    val entityCount: Int,
    val recentFrames: ImmutableList<String>,
)

@Composable
fun WsDebugScreen(
    haClient: HaWebSocketClient,
    onForceReconnect: () -> Unit,
    onClearTokens: () -> Unit,
    onBack: () -> Unit,
) {
    val haState by haClient.state.collectAsStateWithLifecycle()
    val entities by haClient.entities.collectAsStateWithLifecycle()
    val recent by haClient.recentEvents.collectAsStateWithLifecycle()
    WsDebugContent(
        state =
            WsDebugUiState(
                haState = haState,
                entityCount = entities.size,
                recentFrames = recent.toImmutableList(),
            ),
        onForceReconnect = onForceReconnect,
        onClearTokens = onClearTokens,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WsDebugContent(
    state: WsDebugUiState,
    onForceReconnect: () -> Unit,
    onClearTokens: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WS Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("<") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("State: ${state.haState}")
            Text("Entities cached: ${state.entityCount}")
            HorizontalDivider()
            Text("Recent frames (newest first, max 50):")
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                items(state.recentFrames) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onForceReconnect, modifier = Modifier.weight(1f)) {
                    Text("Force reconnect")
                }
                Button(onClick = onClearTokens, modifier = Modifier.weight(1f)) {
                    Text("Clear tokens")
                }
            }
        }
    }
}
