package com.superdash.kiosk.bus

/** Commands targeted at the foreground [android.app.Activity]. Submitted via
 *  [ActivityCommandQueue].
 *
 *  Unlike [KioskEvent], these are command semantics (do-this), one-consumer-per-item,
 *  and buffered while no Activity is attached. */
sealed class ActivityCommand {
    /** Reload the current WebView. */
    object RefreshWebView : ActivityCommand()

    /** Cold-restart the app process by relaunching the launch intent. */
    object RestartApp : ActivityCommand()
}
