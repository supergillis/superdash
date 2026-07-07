package com.superdash.kiosk.ui.debug

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.superdash.SuperdashApp
import com.superdash.theme.SuperdashTheme
import kotlinx.coroutines.launch

class WsDebugActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SuperdashApp
        val haClient = app.graph.haClient
        val tokenStore = app.graph.tokenStore
        setContent {
            SuperdashTheme {
                WsDebugScreen(
                    haClient = haClient,
                    onForceReconnect = { haClient.forceReconnect() },
                    onClearTokens = { lifecycleScope.launch { tokenStore.clear() } },
                    onBack = { finish() },
                )
            }
        }
    }
}
