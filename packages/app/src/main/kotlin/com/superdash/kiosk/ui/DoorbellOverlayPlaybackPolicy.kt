package com.superdash.kiosk.ui

import com.superdash.doorbell.DoorbellState

fun shouldStartDoorbellStream(
    doorbellState: DoorbellState,
    activityForeground: Boolean,
): Boolean = activityForeground && doorbellState is DoorbellState.Showing
