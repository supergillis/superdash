package com.superdash.camera

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.superdash.R
import com.superdash.SuperdashApp
import com.superdash.core.log.Log
import com.superdash.service.ForegroundServiceStartPolicy
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "superdash_camera"
private const val NOTIFICATION_ID = 0xCA3

private val log = Log("CameraService")

/** Foreground service holding the `camera` FGS type while the camera feature
 *  is enabled, so capture keeps working when the kiosk activity is not
 *  resumed (screensaver, doorbell overlay). The pipeline itself lives in
 *  AppGraph's CameraController; this service only anchors the FGS type. */
class CameraService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        log.i("onCreate")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            log.w("camera permission missing; stopping camera service")
            stopSelf()
            return
        }
        ensureChannel()
        val foregroundStarted =
            ForegroundServiceStartPolicy.tryStartForeground {
                // The camera FGS type exists from API 30 (R) only; below that,
                // minSdk 28 devices must use the untyped overload.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            }
        if (!foregroundStarted) {
            log.w("foreground start denied; stopping camera service")
            stopSelf()
            return
        }
        val graph = (application as SuperdashApp).graph
        lifecycleScope.launch {
            graph.cameraSettings.enabled.distinctUntilChanged().collect { enabled ->
                if (!enabled) {
                    log.i("camera disabled; stopping camera service")
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        // Don't let the system restart this service in the background after
        // process death: a while-in-use camera foreground service cannot be
        // started from the background, so a sticky restart would only
        // crash-loop.
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_camera_channel_name),
                    NotificationManager.IMPORTANCE_MIN,
                ),
            )
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_camera_title))
            .setContentText(getString(R.string.notification_camera_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    companion object {
        fun start(
            context: Context,
            shouldRun: Boolean,
        ) {
            val hasCameraPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            val shouldRequestStart =
                ForegroundServiceStartPolicy.shouldRequestStart(
                    shouldRun = shouldRun,
                    permissionGranted = hasCameraPermission,
                )
            if (!shouldRequestStart) {
                val reason =
                    ForegroundServiceStartPolicy.skipStartReason(
                        shouldRun = shouldRun,
                        permissionGranted = hasCameraPermission,
                    )
                log.w(
                    "camera service start skipped",
                    null,
                    "reason" to reason,
                )
                return
            }
            context.startForegroundService(Intent(context, CameraService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CameraService::class.java))
        }
    }
}
