package com.superdash.screensaver

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import com.superdash.core.log.Log

private val log = Log("BlackScreensaver")

/** Pure-black screensaver that drives the host Activity's window brightness to 0
 *  while mounted, restoring it on disposal. Used as the default night-mode
 *  rendering. The host still renders the ambient HUD over this, dimmed to match
 *  the forced-low brightness — a glance-only view of time, weather, and the
 *  next calendar event. */
class BlackScreensaver : Screensaver {
    override val id = "black"

    @Composable
    override fun Content(modifier: Modifier) {
        val view = LocalView.current
        DisposableEffect(view) {
            val activity = view.context.findActivity()
            val window = activity?.window
            val savedBrightness =
                if (window != null) {
                    applyBrightnessOverride(
                        readBrightness = { window.attributes.screenBrightness },
                        writeBrightness = { value ->
                            val attributes = window.attributes
                            attributes.screenBrightness = value
                            window.attributes = attributes
                        },
                    )
                } else {
                    log.w("no host Activity from LocalView; brightness override skipped")
                    BRIGHTNESS_OVERRIDE_NONE
                }
            onDispose {
                if (window != null) {
                    restoreBrightness(
                        saved = savedBrightness,
                        writeBrightness = { value ->
                            val attributes = window.attributes
                            attributes.screenBrightness = value
                            window.attributes = attributes
                        },
                    )
                }
            }
        }
        Box(modifier.fillMaxSize().background(Color.Black))
    }

    companion object {
        const val BRIGHTNESS_OVERRIDE_NONE: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

        fun applyBrightnessOverride(
            readBrightness: () -> Float,
            writeBrightness: (Float) -> Unit,
        ): Float {
            val saved = readBrightness()
            writeBrightness(0f)
            return saved
        }

        fun restoreBrightness(
            saved: Float,
            writeBrightness: (Float) -> Unit,
        ) {
            writeBrightness(saved)
        }
    }
}

private fun android.content.Context.findActivity(): Activity? {
    var current: android.content.Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}
