package com.superdash.device

import android.annotation.SuppressLint
import android.app.Application

/** Stable device-identity fields: app version, ANDROID_ID (per-app on Android 8+),
 *  manufacturer/model. Consumed by ESPHome native API for device metadata. */
class DeviceInfo(
    application: Application,
) {
    val appVersionName: String =
        runCatching {
            application.packageManager
                .getPackageInfo(application.packageName, 0)
                .versionName
        }.getOrNull() ?: "unknown"

    @SuppressLint("HardwareIds")
    val androidId: String =
        runCatching {
            android.provider.Settings.Secure
                .getString(application.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        }.getOrNull() ?: "unknown"

    val deviceManufacturer: String = android.os.Build.MANUFACTURER ?: ""

    val deviceModel: String = android.os.Build.MODEL ?: ""

    val deviceName: String = deviceModel.ifEmpty { "superdash" }
}
