package com.superdash.kiosk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import com.superdash.MainActivity
import com.superdash.core.log.Log

private val log = Log("KioskService")

class KioskService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        log.i("onCreate")
        ensureChannel()
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
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val mgr = getSystemService<NotificationManager>() ?: return
        if (mgr.getNotificationChannel(CHANNEL) != null) {
            return
        }
        val channel =
            NotificationChannel(CHANNEL, "Kiosk keep-alive", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                description = "Keeps superdash alive on aggressive-battery-management devices."
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
            .setContentTitle("superdash")
            .setContentText("Dashboard active")
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
            val intent = Intent(context, KioskService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
