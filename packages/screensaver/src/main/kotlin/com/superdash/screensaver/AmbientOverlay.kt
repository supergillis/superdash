package com.superdash.screensaver

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superdash.screensaver.overlay.OverlayPosition
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ambientShadow =
    Shadow(
        color = Color.Black.copy(alpha = 0.7f),
        offset = Offset(0f, 2f),
        blurRadius = 12f,
    )

private val ambientStyle = TextStyle(shadow = ambientShadow)

/** Always-on ambient HUD: time + date + (optional) weather + 3-day forecast
 *  + (optional) next calendar event.
 *
 *  No background. Text rests on a subtle drop shadow for legibility. The
 *  host has already resolved Random into a concrete corner before calling.
 *
 *  Layout reverses for bottom corners so the large time text always sits
 *  against the screen edge and the smaller rows stack inward. */
@Composable
fun AmbientOverlay(
    weather: WeatherSnapshot?,
    calendar: CalendarSnapshot?,
    energy: EnergySnapshot?,
    position: OverlayPosition,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    val timeFormatPattern = remember(locale, is24Hour) { timePattern(is24Hour, locale) }
    val calTimePattern = remember(locale, is24Hour) { timePattern(is24Hour, locale) }
    val dateFormatPattern = remember(locale) { localizedDatePattern("EEEEdMMMM", locale) }
    val nowLabel = stringResource(R.string.screensaver_calendar_now)
    val todayLabel = stringResource(R.string.screensaver_calendar_today)
    val tomorrowLabel = stringResource(R.string.screensaver_calendar_tomorrow)
    Box(modifier.fillMaxSize()) {
        var now by remember(timeFormatPattern, locale) {
            mutableStateOf(formatTime(timeFormatPattern, locale))
        }
        var date by remember(dateFormatPattern, locale) {
            mutableStateOf(formatDate(dateFormatPattern, locale))
        }
        var eventLabel by remember(locale, calTimePattern, nowLabel, todayLabel, tomorrowLabel) {
            mutableStateOf(
                calendarLabelOrNull(calendar, locale, calTimePattern, nowLabel, todayLabel, tomorrowLabel),
            )
        }
        LaunchedEffect(calendar, locale, calTimePattern, nowLabel, todayLabel, tomorrowLabel) {
            eventLabel = calendarLabelOrNull(calendar, locale, calTimePattern, nowLabel, todayLabel, tomorrowLabel)
            while (true) {
                now = formatTime(timeFormatPattern, locale)
                date = formatDate(dateFormatPattern, locale)
                eventLabel = calendarLabelOrNull(calendar, locale, calTimePattern, nowLabel, todayLabel, tomorrowLabel)
                delay(1_000)
            }
        }
        val alignment =
            when (position) {
                OverlayPosition.TopLeft -> Alignment.TopStart
                OverlayPosition.TopRight -> Alignment.TopEnd
                OverlayPosition.BottomLeft -> Alignment.BottomStart
                OverlayPosition.BottomRight -> Alignment.BottomEnd
                OverlayPosition.Random -> Alignment.BottomStart
            }
        val isBottom =
            when (position) {
                OverlayPosition.BottomLeft,
                OverlayPosition.BottomRight,
                OverlayPosition.Random,
                -> true
                OverlayPosition.TopLeft,
                OverlayPosition.TopRight,
                -> false
            }
        val isRight =
            when (position) {
                OverlayPosition.TopRight,
                OverlayPosition.BottomRight,
                -> true
                OverlayPosition.TopLeft,
                OverlayPosition.BottomLeft,
                OverlayPosition.Random,
                -> false
            }
        val sections = mutableListOf<@Composable () -> Unit>()
        val gaps = mutableListOf<Dp>()
        sections += { TimeBlock(now) }
        sections += { DateRow(date, weather) }
        gaps += 4.dp
        if (weather != null && weather.forecast.isNotEmpty()) {
            sections += { ForecastRow(weather) }
            gaps += 12.dp
        }
        if (energy != null) {
            sections += { EnergyRow(energy) }
            gaps += 12.dp
        }
        eventLabel?.let { label ->
            sections += { EventRow(label) }
            gaps += 12.dp
        }
        val orderedSections = if (isBottom) sections.asReversed() else sections
        val orderedGaps = if (isBottom) gaps.asReversed() else gaps
        Column(
            horizontalAlignment = if (isRight) Alignment.End else Alignment.Start,
            modifier =
                Modifier
                    .align(alignment)
                    .padding(32.dp),
        ) {
            orderedSections.forEachIndexed { index, render ->
                if (index > 0) {
                    Spacer(Modifier.height(orderedGaps[index - 1]))
                }
                render()
            }
        }
    }
}

@Composable
private fun TimeBlock(now: String) {
    Text(
        now,
        fontSize = 88.sp,
        color = Color.White,
        fontWeight = FontWeight.Black,
        style = ambientStyle,
    )
}

@Composable
private fun DateRow(date: String, weather: WeatherSnapshot?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (weather != null) {
            Icon(
                imageVector = weatherIconFor(weather.state),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(6.dp))
            val temp = weather.temperatureC
            if (temp != null) {
                Text(
                    text = "${temp.toInt()}${weather.unit ?: "°"}",
                    fontSize = 22.sp,
                    color = Color.White,
                    style = ambientStyle,
                )
            } else {
                Text(
                    weather.state,
                    fontSize = 20.sp,
                    color = Color.White,
                    style = ambientStyle,
                )
            }
            Spacer(Modifier.width(16.dp))
        }
        Text(
            date,
            fontSize = 22.sp,
            color = Color.White.copy(alpha = 0.85f),
            style = ambientStyle,
        )
    }
}

@Composable
private fun ForecastRow(weather: WeatherSnapshot) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        weather.forecast.take(3).forEach { day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = weatherIconFor(day.condition),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
                day.tempHi?.let {
                    Text(
                        "${it.toInt()}°",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        style = ambientStyle,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.CalendarToday,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            fontSize = 22.sp,
            color = Color.White.copy(alpha = 0.85f),
            style = ambientStyle,
            maxLines = 1,
        )
    }
}

@Composable
private fun EnergyRow(energy: EnergySnapshot) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        energy.usageW?.let { watts ->
            EnergyStat(icon = Icons.Filled.Bolt, value = formatPower(watts))
        }
        energy.solarW?.let { watts ->
            EnergyStat(icon = Icons.Filled.WbSunny, value = formatPower(watts))
        }
        energy.gridW?.let { watts ->
            val (icon, magnitude) =
                if (watts >= 0) {
                    Icons.Filled.ArrowDownward to watts
                } else {
                    Icons.Filled.ArrowUpward to -watts
                }
            EnergyStat(icon = icon, value = formatPower(magnitude))
        }
    }
}

@Composable
private fun EnergyStat(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            fontSize = 20.sp,
            color = Color.White,
            style = ambientStyle,
        )
    }
}

private fun formatPower(watts: Double): String =
    if (kotlin.math.abs(watts) >= 1000.0) {
        val kw = watts / 1000.0
        "${"%.1f".format(kw)} kW"
    } else {
        "${watts.toInt()} W"
    }

private fun calendarLabelOrNull(
    snapshot: CalendarSnapshot?,
    locale: Locale,
    timePattern: String,
    nowLabel: String,
    todayLabel: String,
    tomorrowLabel: String,
): String? {
    if (snapshot == null) {
        return null
    }
    return formatCalendarLabel(
        snapshot,
        Clock.System.now(),
        TimeZone.currentSystemDefault(),
        locale,
        nowLabel,
        todayLabel,
        tomorrowLabel,
        timePattern,
    )
}

private fun formatTime(pattern: String, locale: Locale): String =
    SimpleDateFormat(pattern, locale).format(Date())

private fun formatDate(pattern: String, locale: Locale): String =
    SimpleDateFormat(pattern, locale).format(Date())

private fun weatherIconFor(state: String): ImageVector =
    when (state.lowercase()) {
        "sunny", "clear" -> Icons.Filled.WbSunny
        "clear-night" -> Icons.Filled.NightsStay
        "cloudy" -> Icons.Filled.Cloud
        "partlycloudy" -> Icons.Filled.WbCloudy
        "rainy", "pouring" -> Icons.Filled.Grain
        "snowy" -> Icons.Filled.AcUnit
        "lightning", "lightning-rainy" -> Icons.Filled.Bolt
        "fog", "foggy" -> Icons.Filled.Visibility
        else -> Icons.Filled.Cloud
    }
