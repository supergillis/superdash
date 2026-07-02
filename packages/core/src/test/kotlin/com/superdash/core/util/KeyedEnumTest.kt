package com.superdash.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyedEnumTest {
    private enum class Color(
        override val key: String,
    ) : KeyedEnum {
        Red("red"),
        Green("green"),
        Blue("blue"),
    }

    @Test
    fun `keyOf returns matching enum`() {
        assertEquals(Color.Green, keyOf<Color>("green", default = Color.Red))
    }

    @Test
    fun `keyOf returns default on miss`() {
        assertEquals(Color.Red, keyOf<Color>("unknown", default = Color.Red))
    }

    @Test
    fun `keyOf returns default on empty key`() {
        assertEquals(Color.Red, keyOf<Color>("", default = Color.Red))
    }
}
