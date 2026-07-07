package com.superdash.kiosk.ui

import android.content.Context

/** The kiosk script + JS bridge installer, injected via
 *  WebViewCompat.addDocumentStartJavaScript().
 *
 *  The actual script lives at packages/app/src/main/assets/scripts/kiosk.js.
 *  Loaded on first call and cached so subsequent WebView builds reuse the
 *  same string. */
object KioskWebViewScripts {
    @Volatile
    private var cachedKioskJs: String? = null

    fun kioskJs(context: Context): String {
        cachedKioskJs?.let { return it }
        synchronized(this) {
            cachedKioskJs?.let { return it }
            val loaded =
                context.applicationContext.assets
                    .open("scripts/kiosk.js")
                    .use { it.readBytes().decodeToString() }
            cachedKioskJs = loaded
            return loaded
        }
    }
}
