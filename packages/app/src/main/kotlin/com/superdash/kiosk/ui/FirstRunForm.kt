package com.superdash.kiosk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.superdash.core.util.UrlNormalizer

@Composable
fun FirstRunForm(
    onSubmit: (normalizedUrl: String) -> Unit,
    initialUrl: String = "",
    banner: String? = null,
) {
    var input by remember { mutableStateOf(initialUrl) }
    val normalized = remember(input) { UrlNormalizer.normalize(input) }
    val canSubmit = normalized != null

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Welcome to superdash", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        if (banner != null) {
            Text(
                banner,
                color = Color(0xFFD93025),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
        }
        Text("Enter your Home Assistant URL to get started")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("HA URL") },
            placeholder = { Text("homeassistant.local:8123") },
            singleLine = true,
            modifier = Modifier.testTag("ha_url_field"),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { normalized?.let(onSubmit) },
            enabled = canSubmit,
            modifier = Modifier.testTag("save_button"),
        ) { Text(if (banner != null) "Sign in again" else "Continue") }
    }
}
