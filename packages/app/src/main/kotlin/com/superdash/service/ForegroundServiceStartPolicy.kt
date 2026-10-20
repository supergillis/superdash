package com.superdash.service

import android.app.ForegroundServiceStartNotAllowedException
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

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

    fun tryStartForeground(
        sdkInt: Int = Build.VERSION.SDK_INT,
        block: () -> Unit,
    ): Boolean =
        try {
            block()
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalStateException) {
            // Before API 31 the not-allowed case is a bare IllegalStateException.
            // From API 31 only the dedicated type is a graceful failure, any other
            // IllegalStateException is a real bug and must surface.
            if (isApi31OrNewer(sdkInt) && e !is ForegroundServiceStartNotAllowedException) {
                throw e
            }
            false
        }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S, parameter = 0)
    private fun isApi31OrNewer(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S
}
