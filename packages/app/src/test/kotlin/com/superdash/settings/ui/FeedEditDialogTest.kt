package com.superdash.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedEditDialogTest {
    @Test fun `a new sustained feed prefills no auto close`() {
        assertEquals("0", defaultFeedAutoCloseSecFor(sustained = true))
    }

    @Test fun `a new momentary feed prefills a one minute auto close`() {
        assertEquals("60", defaultFeedAutoCloseSecFor(sustained = false))
    }

    @Test fun `a new sustained feed prefills the lower priority`() {
        assertEquals("0", defaultFeedOrderFor(sustained = true))
    }

    @Test fun `a new momentary feed prefills the higher priority`() {
        assertEquals("10", defaultFeedOrderFor(sustained = false))
    }
}
