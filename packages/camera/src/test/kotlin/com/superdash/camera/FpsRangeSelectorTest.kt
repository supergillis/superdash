package com.superdash.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FpsRangeSelectorTest {
    @Test
    fun `empty list returns null`() {
        assertNull(selectAeFpsRange(emptyList(), 10))
    }

    @Test
    fun `prefers the highest upper bound at or below the cap`() {
        val ranges = listOf(5 to 10, 7 to 15, 15 to 15, 15 to 30)
        assertEquals(7 to 15, selectAeFpsRange(ranges, 15))
    }

    @Test
    fun `tie-breaks on the lowest lower bound so AE can drop in dim light`() {
        val ranges = listOf(10 to 10, 5 to 10)
        assertEquals(5 to 10, selectAeFpsRange(ranges, 10))
    }

    @Test
    fun `falls back to the smallest upper bound when nothing fits the cap`() {
        val ranges = listOf(15 to 30, 30 to 30, 24 to 24)
        assertEquals(24 to 24, selectAeFpsRange(ranges, 10))
    }

    @Test
    fun `fallback also tie-breaks on the lowest lower bound`() {
        val ranges = listOf(7 to 24, 24 to 24, 30 to 30)
        assertEquals(7 to 24, selectAeFpsRange(ranges, 10))
    }

    @Test
    fun `typical device without low ranges falls back to its slowest range`() {
        // Pixel-style front camera set at the default cap of 10.
        val ranges = listOf(15 to 15, 15 to 30, 30 to 30)
        assertEquals(15 to 15, selectAeFpsRange(ranges, 10))
    }

    @Test
    fun `cap at 30 picks the fastest range with the widest lower bound`() {
        val ranges = listOf(15 to 15, 15 to 30, 30 to 30)
        assertEquals(15 to 30, selectAeFpsRange(ranges, 30))
    }

    @Test
    fun `intersect of no cameras is empty`() {
        assertEquals(emptyList<Pair<Int, Int>>(), intersectFpsRanges(emptyList()))
    }

    @Test
    fun `intersect of a single camera keeps its ranges in order`() {
        val ranges = listOf(15 to 15, 15 to 30, 30 to 30)
        assertEquals(ranges, intersectFpsRanges(listOf(ranges)))
    }

    @Test
    fun `intersect keeps only ranges every camera supports`() {
        val wide = listOf(7 to 30, 15 to 30, 30 to 30)
        val tele = listOf(15 to 30, 30 to 30, 24 to 24)
        assertEquals(listOf(15 to 30, 30 to 30), intersectFpsRanges(listOf(wide, tele)))
    }

    @Test
    fun `disjoint cameras intersect to empty`() {
        assertEquals(
            emptyList<Pair<Int, Int>>(),
            intersectFpsRanges(listOf(listOf(15 to 15), listOf(30 to 30))),
        )
    }
}
