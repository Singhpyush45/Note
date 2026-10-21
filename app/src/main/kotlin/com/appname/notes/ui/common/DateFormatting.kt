package com.appname.notes.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Short, human dates for note cards: "14:30" today, "21 Oct" this year, "21 Oct 2025" otherwise.
 * Uses the device's locale and time zone.
 */
object NoteDates {
    fun format(
        instant: Instant,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val date = instant.atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        return when {
            date.toLocalDate() == today -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(date)
            date.year == today.year -> DateTimeFormatter.ofPattern("d MMM", locale).format(date)
            else -> DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(date)
        }
    }
}
