package com.superdash.service

/** Reusable start policy for while-in-use foreground services (mic, camera):
 *  gates start requests on both a feature-enabled flag and a runtime
 *  permission, and swallows the foreground-start exceptions the OS throws
 *  when a background process is not allowed to start a foreground service
 *  (API 31+), so callers can fail gracefully instead of crash-looping. */
internal object ForegroundServiceStartPolicy {
    fun shouldRequestStart(
        shouldRun: Boolean,
        permissionGranted: Boolean,
    ): Boolean = shouldRun && permissionGranted

    fun skipStartReason(
        shouldRun: Boolean,
        permissionGranted: Boolean,
    ): String? =
        if (!shouldRun) {
            "disabled"
        } else if (!permissionGranted) {
            "permission_missing"
        } else {
            null
        }

    fun tryStartForeground(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+): the system
            // restarted the service while the app was in the background. A
            // background foreground service can never start, so fail
            // gracefully instead of crashing.
            false
        }
}
