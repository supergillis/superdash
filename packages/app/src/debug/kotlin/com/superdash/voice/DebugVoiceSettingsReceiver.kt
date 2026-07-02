package com.superdash.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.superdash.SuperdashApp
import com.superdash.core.log.Log
import com.superdash.voice.models.VoiceModelCatalog
import com.superdash.voice.models.VoiceModelKind
import com.superdash.voice.pipeline.VoiceSttProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val log = Log("DebugVoiceSettings")

class DebugVoiceSettingsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as SuperdashApp
                intent.getStringExtra("wakeword")?.takeIf { it.isNotBlank() }?.let { wakeWord ->
                    app
                        .graph
                        .voiceSettings
                        .setActiveWakeWord(wakeWord)
                    log.i("set wake word", "wakeWord" to wakeWord)
                }
                val providerKey = intent.getStringExtra("provider")?.takeIf { it.isNotBlank() }
                if (providerKey != null) {
                    val provider =
                        parseDebugSttProvider(providerKey).getOrElse { throwable ->
                            log.w("rejected debug STT provider", throwable, "key" to providerKey)
                            return@launch
                        }
                    app
                        .graph
                        .voiceSettings
                        .setPrimarySttProvider(provider.key)
                    app
                        .graph
                        .voiceSettings
                        .setSecondarySttProvider(VoiceSttProvider.None.key)
                    log.i(
                        "set voice provider",
                        "primary" to provider.key,
                        "secondary" to VoiceSttProvider.None.key,
                        "debugProvider" to providerKey,
                    )
                }
                val primarySttProviderKey = intent.getStringExtra("primary_stt")?.takeIf { it.isNotBlank() }
                if (primarySttProviderKey != null) {
                    val provider =
                        parseDebugSttProvider(primarySttProviderKey).getOrElse { throwable ->
                            log.w("rejected debug primary STT provider", throwable, "key" to primarySttProviderKey)
                            return@launch
                        }
                    app
                        .graph
                        .voiceSettings
                        .setPrimarySttProvider(provider.key)
                    log.i("set primary STT provider", "provider" to provider.key)
                }
                val secondarySttProviderKey = intent.getStringExtra("secondary_stt")?.takeIf { it.isNotBlank() }
                if (secondarySttProviderKey != null) {
                    val provider =
                        parseDebugSttProvider(secondarySttProviderKey).getOrElse { throwable ->
                            log.w("rejected debug secondary STT provider", throwable, "key" to secondarySttProviderKey)
                            return@launch
                        }
                    app
                        .graph
                        .voiceSettings
                        .setSecondarySttProvider(provider.key)
                    log.i("set secondary STT provider", "provider" to provider.key)
                }
                intent.getStringExtra("stt_model")?.takeIf { it.isNotBlank() }?.let { modelId ->
                    val model = VoiceModelCatalog.requireModel(modelId)
                    require(model.kind == VoiceModelKind.Stt) {
                        "Model $modelId is not an STT model"
                    }
                    app
                        .graph
                        .voiceSettings
                        .setSelectedSttModelId(model.id)
                    log.i("set STT model", "modelId" to model.id)
                }
                intent.getStringExtra("ha_url")?.takeIf { it.isNotBlank() }?.let { haUrl ->
                    app
                        .graph
                        .settings
                        .setHaUrl(haUrl)
                    log.i("set HA URL", "haUrl" to haUrl)
                }
                if (intent.hasExtra("local_intent_enabled")) {
                    val enabled = intent.getBooleanExtra("local_intent_enabled", false)
                    app
                        .graph
                        .voiceSettings
                        .setLocalIntentRecognizerEnabled(enabled)
                    log.i("set local intent recognizer", "enabled" to enabled)
                }
                if (intent.getBooleanExtra("dump_ha_error_log", false)) {
                    dumpHaErrorLog(app)
                }
            } catch (t: Throwable) {
                log.w("failed to set debug voice settings", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun dumpHaErrorLog(app: SuperdashApp) {
        val haUrl =
            app
                .graph
                .haUrlFlow
                .value
                ?.trimEnd('/')
        if (haUrl.isNullOrBlank()) {
            log.w("cannot dump HA error log; HA URL missing")
            return
        }
        val token = app.graph.tokenProvider.get()
        val body =
            withContext(Dispatchers.IO) {
                val connection = URL("$haUrl/api/error_log").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    val stream =
                        if (connection.responseCode in 200..299) {
                            connection.inputStream
                        } else {
                            connection.errorStream
                        }
                    stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                } finally {
                    connection.disconnect()
                }
            }
        log.w("HA error log tail", null, "tail" to body.takeLast(4_000))
    }
}
