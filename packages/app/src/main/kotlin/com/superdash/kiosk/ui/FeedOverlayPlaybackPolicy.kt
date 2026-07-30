package com.superdash.kiosk.ui

import com.superdash.feed.FeedState

fun shouldStartFeedStream(
    feedState: FeedState,
    activityForeground: Boolean,
): Boolean = activityForeground && feedState is FeedState.Showing
