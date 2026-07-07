package com.superdash.screensaver

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * `DateFormat.getBestDateTimePattern`/`is24HourFormat` are real Android framework
 * calls. The screensaver module's `src/test` unit tests run plain-JVM with
 * `unitTests.isReturnDefaultValues = true` (see SuperdashAndroidLibraryConventionPlugin),
 * which stubs these to return null rather than real patterns, so this test lives in
 * `src/androidTest` and runs on-device/emulator via `connectedAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class DateTimeFormattingTest {
    @Test fun pattern24hHasNoAmPmMarker() {
        val pattern = timePattern(is24Hour = true, locale = Locale.US)
        assertTrue("expected H in $pattern", pattern.contains("H"))
        assertTrue("expected no a in $pattern", !pattern.contains("a"))
    }

    @Test fun pattern12hHasAmPmMarker() {
        val pattern = timePattern(is24Hour = false, locale = Locale.US)
        assertTrue("expected h in $pattern", pattern.contains("h"))
        assertTrue("expected a in $pattern", pattern.contains("a"))
    }
}
