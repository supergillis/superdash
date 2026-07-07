package com.superdash.screensaver

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Fullscreen clock screensaver. Simple anti-burn-in: random offset re-jittered every
 *  15 minutes so the rendered text doesn't stick to the same pixels for hours. */
class ClockScreensaver : Screensaver {
    override val id = "clock"

    @Composable
    override fun Content(modifier: Modifier) {
        val context = LocalContext.current
        val locale = context.resources.configuration.locales[0]
        val is24Hour = DateFormat.is24HourFormat(context)
        val pattern = remember(locale, is24Hour) { timePattern(is24Hour, locale) }
        var now by remember(pattern) { mutableStateOf(formatTime(pattern, locale)) }
        LaunchedEffect(pattern, locale) {
            while (true) {
                now = formatTime(pattern, locale)
                delay(1_000)
            }
        }
        var offset by remember { mutableStateOf(IntOffset.Zero) }
        LaunchedEffect(Unit) {
            while (true) {
                offset = IntOffset(Random.nextInt(-40, 41), Random.nextInt(-40, 41))
                delay(15 * 60 * 1_000)
            }
        }
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(
                text = now,
                fontSize = 96.sp,
                color = Color.White,
                fontWeight = FontWeight.Light,
                modifier = Modifier.offset { offset },
            )
        }
    }

    private fun formatTime(pattern: String, locale: Locale): String =
        SimpleDateFormat(pattern, locale).format(Date())
}
