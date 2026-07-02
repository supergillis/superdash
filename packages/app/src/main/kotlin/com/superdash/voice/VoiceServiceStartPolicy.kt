package com.superdash.voice

internal object VoiceServiceStartPolicy {
    fun shouldRequestStart(
        shouldRun: Boolean,
        hasMicPermission: Boolean,
    ): Boolean =
        shouldRun && hasMicPermission

    fun skipStartReason(
        shouldRun: Boolean,
        hasMicPermission: Boolean,
    ): String =
        if (!shouldRun) {
            "voice_disabled"
        } else if (!hasMicPermission) {
            "mic_permission_missing"
        } else {
            "none"
        }

    fun shouldStopForShouldRun(shouldRun: Boolean): Boolean =
        !shouldRun

    fun tryStartForeground(startForeground: () -> Unit): Boolean =
        try {
            startForeground()
            true
        } catch (e: SecurityException) {
            false
        }
}
