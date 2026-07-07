package com.superdash.screensaver

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.superdash.screensaver.overlay.OverlayPosition

/** A pluggable screensaver. The composable IS the lifecycle. Per-screensaver state
 *  lives inside Content / Overlays via LaunchedEffect / DisposableEffect. No
 *  imperative bind/unbind/onActivate hooks. */
interface Screensaver {
    val id: String

    /** Background / focal layer of the screensaver. */
    @Composable fun Content(modifier: Modifier)

    /** Up to 3 corner-agnostic overlays this screensaver wants drawn over its
     *  own content. The host assigns each overlay to a free corner (one not
     *  used by the ambient overlay) and invokes the lambda with that corner.
     *  Default: no overlays. */
    @Composable fun Overlays(): List<ScreensaverOverlay> = emptyList()
}

fun interface ScreensaverOverlay {
    @Composable operator fun invoke(corner: OverlayPosition)
}
