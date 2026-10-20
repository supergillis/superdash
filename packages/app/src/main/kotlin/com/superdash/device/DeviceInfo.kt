package com.superdash.device

import android.annotation.SuppressLint
import android.app.Application

/** Stable device-identity fields: app version, ANDROID_ID (per-app on Android 8+),
 *  manufacturer/model. Consumed by ESPHome native API for device metadata. */
class DeviceInfo(
    application: Application,
    fallbackId: () -> String,
) {
    val appVersionName: String =
        runCatching {
            application.packageManager
                .getPackageInfo(application.packageName, 0)
                .versionName
        }.getOrNull() ?: "unknown"

    @SuppressLint("HardwareIds")
    val androidId: String =
        resolveStableDeviceId(
            androidId =
                runCatching {
                    android.provider.Settings.Secure
                        .getString(application.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                }.getOrNull(),
            fallbackId = fallbackId,
        )

    val deviceManufacturer: String = android.os.Build.MANUFACTURER ?: ""

    val deviceModel: String = android.os.Build.MODEL ?: ""

    val deviceName: String = deviceModel.ifEmpty { "superdash" }
}

/** A shared constant id would give every such tablet one MAC and merge them in HA. */
internal fun resolveStableDeviceId(
    androidId: String?,
    fallbackId: () -> String,
): String = androidId?.takeIf { it.isNotBlank() } ?: fallbackId()
