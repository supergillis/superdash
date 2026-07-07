package com.superdash.kiosk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinPathTest {
    @Test
    fun `pin root is allowed`() {
        assertTrue(PinPath.isInside("/dashboard-tablet", "dashboard-tablet"))
    }

    @Test
    fun `single view tab is allowed`() {
        assertTrue(PinPath.isInside("/dashboard-tablet/kitchen", "dashboard-tablet"))
    }

    @Test
    fun `deep paths are rejected`() {
        assertFalse(PinPath.isInside("/dashboard-tablet/kitchen/sub", "dashboard-tablet"))
    }

    @Test
    fun `outside pin is rejected`() {
        assertFalse(PinPath.isInside("/lovelace", "dashboard-tablet"))
    }

    @Test
    fun `null path is rejected`() {
        assertFalse(PinPath.isInside(null, "dashboard-tablet"))
    }

    @Test
    fun `percent-encoded depth-2 is rejected after decode`() {
        // /dashboard-tablet/foo%2Fbar would slip past a naive contains('/') check
        // before decoding. After decoding the slash, depth-2 is rejected.
        assertFalse(PinPath.isInside("/dashboard-tablet/foo%2Fbar", "dashboard-tablet"))
    }

    @Test
    fun `percent-encoded parent traversal is rejected`() {
        // /dashboard-tablet/%2E%2E decodes to /dashboard-tablet/.. which would
        // resolve to /dashboard-tablet's parent. Rejected.
        assertFalse(PinPath.isInside("/dashboard-tablet/%2E%2E", "dashboard-tablet"))
    }

    @Test
    fun `bare parent traversal segment is rejected`() {
        assertFalse(PinPath.isInside("/dashboard-tablet/..", "dashboard-tablet"))
        assertFalse(PinPath.isInside("/dashboard-tablet/.", "dashboard-tablet"))
    }

    @Test
    fun `percent-encoded view name is decoded and matches`() {
        // /dashboard-tablet/kitchen%20area decodes to /dashboard-tablet/kitchen area
        // which is depth-1 with no slash, so it's allowed.
        assertTrue(PinPath.isInside("/dashboard-tablet/kitchen%20area", "dashboard-tablet"))
    }

    @Test
    fun `malformed percent-encoding is rejected`() {
        assertFalse(PinPath.isInside("/dashboard-tablet/%ZZ", "dashboard-tablet"))
    }
}
