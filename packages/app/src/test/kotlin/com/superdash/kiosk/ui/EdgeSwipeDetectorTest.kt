package com.superdash.kiosk.ui

import com.superdash.kiosk.SidebarPosition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeSwipeDetectorTest {
    private val params =
        EdgeSwipeParams(
            edgeZonePx = 32f,
            minPrimaryPx = 80f,
            maxCrossAxisPx = 120f,
            screenWidthPx = 1000f,
            screenHeightPx = 600f,
            edge = SidebarPosition.Left,
        )

    @Test fun `left edge drag meets threshold`() =
        assertTriggered(
            downX = 10f,
            downY = 300f,
            upX = 100f,
            upY = 310f,
            edge = SidebarPosition.Left,
        )

    @Test fun `left edge drag in reverse direction does not trigger`() =
        assertNotTriggered(
            downX = 10f,
            downY = 300f,
            upX = -100f,
            upY = 310f,
            edge = SidebarPosition.Left,
        )

    @Test fun `right edge drag meets threshold`() =
        assertTriggered(
            downX = 990f,
            downY = 300f,
            upX = 900f,
            upY = 310f,
            edge = SidebarPosition.Right,
        )

    @Test fun `right edge drag in reverse direction does not trigger`() =
        assertNotTriggered(
            downX = 990f,
            downY = 300f,
            upX = 1100f,
            upY = 310f,
            edge = SidebarPosition.Right,
        )

    @Test fun `top edge drag meets threshold`() =
        assertTriggered(
            downX = 500f,
            downY = 10f,
            upX = 510f,
            upY = 100f,
            edge = SidebarPosition.Top,
        )

    @Test fun `top edge drag in reverse direction does not trigger`() =
        assertNotTriggered(
            downX = 500f,
            downY = 10f,
            upX = 510f,
            upY = -100f,
            edge = SidebarPosition.Top,
        )

    @Test fun `bottom edge drag meets threshold`() =
        assertTriggered(
            downX = 500f,
            downY = 590f,
            upX = 510f,
            upY = 500f,
            edge = SidebarPosition.Bottom,
        )

    @Test fun `bottom edge drag in reverse direction does not trigger`() =
        assertNotTriggered(
            downX = 500f,
            downY = 590f,
            upX = 510f,
            upY = 700f,
            edge = SidebarPosition.Bottom,
        )

    @Test fun `wrong configured edge does not trigger`() =
        assertNotTriggered(
            downX = 10f,
            downY = 300f,
            upX = 100f,
            upY = 310f,
            edge = SidebarPosition.Right,
        )

    @Test fun `primary distance below min does not trigger`() =
        assertNotTriggered(
            downX = 10f,
            downY = 300f,
            upX = 50f,
            upY = 310f,
            edge = SidebarPosition.Left,
        )

    @Test fun `cross axis drift too large does not trigger`() =
        assertNotTriggered(
            downX = 10f,
            downY = 300f,
            upX = 100f,
            upY = 500f,
            edge = SidebarPosition.Left,
        )

    private fun assertTriggered(
        downX: Float,
        downY: Float,
        upX: Float,
        upY: Float,
        edge: SidebarPosition,
    ) {
        assertTrue(
            EdgeSwipeDetector.classify(
                downX = downX,
                downY = downY,
                upX = upX,
                upY = upY,
                params = params.copy(edge = edge),
            ) is EdgeSwipeResult.Triggered,
        )
    }

    private fun assertNotTriggered(
        downX: Float,
        downY: Float,
        upX: Float,
        upY: Float,
        edge: SidebarPosition,
    ) {
        assertFalse(
            EdgeSwipeDetector.classify(
                downX = downX,
                downY = downY,
                upX = upX,
                upY = upY,
                params = params.copy(edge = edge),
            ) is EdgeSwipeResult.Triggered,
        )
    }
}
