package com.superdash.screensaver

import android.text.format.DateFormat
import java.util.Locale

/** Best localized time pattern honoring the system 12/24-hour preference. */
fun timePattern(is24Hour: Boolean, locale: Locale): String =
    DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm")

/** Best localized pattern for a date skeleton (e.g. "EEEEdMMMM"), locale-ordered. */
fun localizedDatePattern(skeleton: String, locale: Locale): String =
    DateFormat.getBestDateTimePattern(locale, skeleton)
