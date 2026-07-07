package com.superdash.voice

import com.superdash.service.ForegroundServiceStartPolicy

internal object VoiceServiceStartPolicy {
    fun shouldRequestStart(
        shouldRun: Boolean,
        hasMicPermission: Boolean,
    ): Boolean =
        ForegroundServiceStartPolicy.shouldRequestStart(
            shouldRun = shouldRun,
            permissionGranted = hasMicPermission,
        )

    fun skipStartReason(
        shouldRun: Boolean,
        hasMicPermission: Boolean,
    ): String =
        when (
            ForegroundServiceStartPolicy.skipStartReason(
                shouldRun = shouldRun,
                permissionGranted = hasMicPermission,
            )
        ) {
            null -> "none"
            "disabled" -> "voice_disabled"
            else -> "mic_permission_missing"
        }

    fun shouldStopForShouldRun(shouldRun: Boolean): Boolean =
        !shouldRun

    fun tryStartForeground(startForeground: () -> Unit): Boolean =
        ForegroundServiceStartPolicy.tryStartForeground(startForeground)
}
