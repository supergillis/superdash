package com.superdash.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTriggerTest {
    @Test
    fun `listed state is active`() {
        val trigger = FeedTrigger.Sustained(activeStates = listOf("on", "streaming"))
        assertTrue(trigger.isActive("on"))
        assertTrue(trigger.isActive("streaming"))
    }

    @Test
    fun `unlisted state is not active`() {
        val trigger = FeedTrigger.Sustained(activeStates = listOf("on"))
        assertFalse(trigger.isActive("off"))
        assertFalse(trigger.isActive("idle"))
    }

    @Test
    fun `null state is not active`() {
        assertFalse(FeedTrigger.Sustained().isActive(null))
    }

    @Test
    fun `wildcard matches any reachable state`() {
        val trigger = FeedTrigger.Sustained(activeStates = listOf("*"))
        assertTrue(trigger.isActive("idle"))
        assertTrue(trigger.isActive("recording"))
        assertFalse(trigger.isActive("unavailable"))
        assertFalse(trigger.isActive("unknown"))
        assertFalse(trigger.isActive(""))
    }

    @Test
    fun `wildcard wins over sibling entries`() {
        val trigger = FeedTrigger.Sustained(activeStates = listOf("on", "*"))
        assertTrue(trigger.isActive("off"))
        assertFalse(trigger.isActive("unavailable"))
    }

    @Test
    fun `default active states match on only`() {
        val trigger = FeedTrigger.Sustained()
        assertTrue(trigger.isActive("on"))
        assertFalse(trigger.isActive("off"))
    }
}
