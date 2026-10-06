package com.superdash.kiosk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.superdash.MainActivity
import com.superdash.R
import com.superdash.SuperdashApp
import com.superdash.core.log.Log
import com.superdash.kiosk.boot.BootLauncher
import com.superdash.kiosk.boot.BootStartupHandler
import kotlinx.coroutines.launch

private val log = Log("KioskService")

class KioskService : LifecycleService() {
    // Android stopped delivering USER_PRESENT to manifest receivers in API 26, so the
    // launch-on-wake receiver lives here, for as long as the keep-alive service runs.
    private val unlockReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val settings = (application as SuperdashApp).graph.settings
                lifecycleScope.launch {
                    BootStartupHandler(
                        loadSnapshot = { settings.snapshot() },
                        launch = { BootLauncher.launch(this@KioskService) },
                    ).handle(intent.action)
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        log.i("onCreate")
        ensureChannel()
        // Exported: SystemUI sends USER_PRESENT under its own app uid, which a
        // not-exported receiver ignores. It is a protected broadcast, so only the
        // system can send it.
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        val mgr = getSystemService<NotificationManager>() ?: return
        if (mgr.getNotificationChannel(CHANNEL) != null) {
            return
        }
        val channel =
            NotificationChannel(
                CHANNEL,
                getString(R.string.notification_kiosk_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                setShowBadge(false)
                description = getString(R.string.notification_kiosk_channel_description)
            }
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi =
            PendingIntent.getActivity(
                this,
                REQ_OPEN,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat
            .Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.notification_kiosk_title))
            .setContentText(getString(R.string.notification_kiosk_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setShowWhen(false)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        private const val CHANNEL = "superdash_keep_alive"
        private const val NOTIF_ID = 1
        private const val REQ_OPEN = 100

        fun start(context: Context) {
            context.startForegroundService(Intent(context, KioskService::class.java))
        }
    }
}
