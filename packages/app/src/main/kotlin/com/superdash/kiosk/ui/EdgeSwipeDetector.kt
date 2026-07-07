package com.superdash.kiosk.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.superdash.kiosk.SidebarPosition
import kotlin.math.abs

data class EdgeSwipeParams(
    val edgeZonePx: Float,
    val minPrimaryPx: Float,
    val maxCrossAxisPx: Float,
    val screenWidthPx: Float,
    val screenHeightPx: Float,
    val edge: SidebarPosition,
)

sealed interface EdgeSwipeResult {
    data object Triggered : EdgeSwipeResult

    data object NotTriggered : EdgeSwipeResult
}

object EdgeSwipeDetector {
    fun classify(
        downX: Float,
        downY: Float,
        upX: Float,
        upY: Float,
        params: EdgeSwipeParams,
    ): EdgeSwipeResult {
        if (!isConfiguredEdge(downX, downY, params)) {
            return EdgeSwipeResult.NotTriggered
        }
        val primaryDistance =
            when (params.edge) {
                SidebarPosition.Left -> upX - downX
                SidebarPosition.Right -> downX - upX
                SidebarPosition.Top -> upY - downY
                SidebarPosition.Bottom -> downY - upY
            }
        val crossAxisDistance =
            when (params.edge) {
                SidebarPosition.Left,
                SidebarPosition.Right,
                -> abs(upY - downY)
                SidebarPosition.Top,
                SidebarPosition.Bottom,
                -> abs(upX - downX)
            }
        if (primaryDistance < params.minPrimaryPx) {
            return EdgeSwipeResult.NotTriggered
        }
        if (crossAxisDistance > params.maxCrossAxisPx) {
            return EdgeSwipeResult.NotTriggered
        }
        return EdgeSwipeResult.Triggered
    }

    fun isConfiguredEdge(
        downX: Float,
        downY: Float,
        params: EdgeSwipeParams,
    ): Boolean =
        when (params.edge) {
            SidebarPosition.Left -> downX <= params.edgeZonePx
            SidebarPosition.Right -> downX >= params.screenWidthPx - params.edgeZonePx
            SidebarPosition.Top -> downY <= params.edgeZonePx
            SidebarPosition.Bottom -> downY >= params.screenHeightPx - params.edgeZonePx
        }
}

@Composable
fun Modifier.detectEdgeSwipe(
    edge: SidebarPosition,
    onTriggered: () -> Unit,
): Modifier {
    val density = LocalDensity.current
    val edgeZonePx = remember(density) { with(density) { 32.dp.toPx() } }
    val minPrimaryPx = remember(density) { with(density) { 80.dp.toPx() } }
    val maxCrossAxisPx = remember(density) { with(density) { 120.dp.toPx() } }
    return this.pointerInput(edge) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val downPos = down.position
            val params =
                EdgeSwipeParams(
                    edgeZonePx = edgeZonePx,
                    minPrimaryPx = minPrimaryPx,
                    maxCrossAxisPx = maxCrossAxisPx,
                    screenWidthPx = size.width.toFloat(),
                    screenHeightPx = size.height.toFloat(),
                    edge = edge,
                )
            if (!EdgeSwipeDetector.isConfiguredEdge(downPos.x, downPos.y, params)) {
                return@awaitEachGesture
            }
            var lastPos = downPos
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val pointer = event.changes.firstOrNull { change -> change.id == down.id } ?: break
                lastPos = pointer.position
                if (!pointer.pressed) {
                    break
                }
            }
            if (EdgeSwipeDetector.classify(
                    downX = downPos.x,
                    downY = downPos.y,
                    upX = lastPos.x,
                    upY = lastPos.y,
                    params = params,
                ) is EdgeSwipeResult.Triggered
            ) {
                onTriggered()
            }
        }
    }
}
