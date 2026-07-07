package com.superdash.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.superdash.R
import com.superdash.SuperdashApp
import com.superdash.core.log.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "superdash_voice"
private const val NOTIFICATION_ID = 0xD0E

private val log = Log("VoiceService")

class VoiceService : LifecycleService() {
    private var voiceCaptureJob: Job? = null
    private var voiceEnabledJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        log.i("onCreate")
        ensureChannel()
        val foregroundStarted =
            VoiceServiceStartPolicy.tryStartForeground {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            }
        if (!foregroundStarted) {
            log.w("foreground start denied; stopping voice service")
            stopSelf()
            return
        }
        val graph = (application as SuperdashApp).graph
        voiceEnabledJob =
            lifecycleScope.launch {
                combine(
                    graph.voiceSettings.enabled,
                    graph.haUrlFlow,
                    graph.tokenStore.tokensFlow,
                    graph.haClient.state,
                ) { voiceEnabled, haUrl, tokens, haState ->
                    VoiceServiceRunPolicy.shouldRun(
                        voiceEnabled = voiceEnabled,
                        haUrl = haUrl,
                        tokens = tokens,
                        haState = haState,
                    )
                }.distinctUntilChanged().collect { shouldRun ->
                    if (VoiceServiceStartPolicy.shouldStopForShouldRun(shouldRun)) {
                        log.i("voice should not run; stopping voice service")
                        voiceCaptureJob?.cancel()
                        voiceCaptureJob = null
                        stopSelf()
                    }
                }
            }
        voiceCaptureJob =
            lifecycleScope.launch {
                graph.voiceCaptureLoop.run()
            }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        // Don't let the system restart this service in the background after
        // process death: a microphone foreground service cannot be started from
        // the background, so a sticky restart would only crash-loop.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        log.i("onDestroy")
        voiceEnabledJob?.cancel()
        voiceCaptureJob?.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_voice_channel_name),
                    NotificationManager.IMPORTANCE_MIN,
                ),
            )
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_voice_title))
            .setContentText(getString(R.string.notification_voice_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    companion object {
        fun start(
            context: Context,
            shouldRun: Boolean,
        ) {
            val hasMicPermission =
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            val shouldRequestStart =
                VoiceServiceStartPolicy.shouldRequestStart(
                    shouldRun = shouldRun,
                    hasMicPermission = hasMicPermission,
                )
            if (!shouldRequestStart) {
                val reason =
                    VoiceServiceStartPolicy.skipStartReason(
                        shouldRun = shouldRun,
                        hasMicPermission = hasMicPermission,
                    )
                log.w(
                    "voice service start skipped",
                    null,
                    "reason" to reason,
                )
                return
            }
            val intent = Intent(context, VoiceService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceService::class.java))
        }
    }
}
