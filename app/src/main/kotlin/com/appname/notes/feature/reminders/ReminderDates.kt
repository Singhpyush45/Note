package com.appname.notes.feature.reminders

import android.content.Context
import android.text.format.DateFormat
import com.appname.notes.R
import com.appname.notes.core.domain.model.ReminderRepeat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reminder times as people say them: "Today, 6:00 PM", "Tomorrow, 9:00 AM", "Mon, 26 Oct, 9:00 AM",
 * "5 Jan 2027, 9:00 AM". The 12/24-hour choice follows the phone's setting.
 */
object ReminderDates {

    data class Words(val yesterday: String, val today: String, val tomorrow: String)

    fun format(at: LocalDateTime, today: LocalDate, words: Words, is24Hour: Boolean, locale: Locale): String {
        val time = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale).format(at)
        val day = when (val date = at.toLocalDate()) {
            today -> words.today
            today.plusDays(1) -> words.tomorrow
            today.minusDays(1) -> words.yesterday
            else -> DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE, d MMM" else "d MMM yyyy", locale).format(date)
        }
        return "$day, $time"
    }

    fun format(context: Context, at: LocalDateTime, today: LocalDate = LocalDate.now()): String = format(
        at,
        today,
        Words(
            yesterday = context.getString(R.string.reminder_yesterday),
            today = context.getString(R.string.reminder_today),
            tomorrow = context.getString(R.string.reminder_tomorrow),
        ),
        DateFormat.is24HourFormat(context),
        context.resources.configuration.locales[0] ?: Locale.getDefault(),
    )

    fun repeatLabel(repeat: ReminderRepeat): Int = when (repeat) {
        ReminderRepeat.NONE -> R.string.reminder_repeat_none
        ReminderRepeat.DAILY -> R.string.reminder_repeat_daily
        ReminderRepeat.WEEKLY -> R.string.reminder_repeat_weekly
        ReminderRepeat.MONTHLY -> R.string.reminder_repeat_monthly
        ReminderRepeat.YEARLY -> R.string.reminder_repeat_yearly
    }
}
