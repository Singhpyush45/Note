package com.appname.notes.feature.reminders

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Test

class ReminderDatesTest {
    private val today = LocalDate.parse("2026-10-22") // a Thursday
    private val words = ReminderDates.Words(yesterday = "Yesterday", today = "Today", tomorrow = "Tomorrow")

    private fun format(at: String, is24: Boolean = false) = ReminderDates.format(LocalDateTime.parse(at), today, words, is24, Locale.UK)

    @Test
    fun `near days use words, later ones the date, other years the year`() {
        assertThat(format("2026-10-22T18:00").lowercase()).isEqualTo("today, 6:00 pm")
        assertThat(format("2026-10-23T09:05").lowercase()).isEqualTo("tomorrow, 9:05 am")
        assertThat(format("2026-10-21T09:00").lowercase()).isEqualTo("yesterday, 9:00 am")
        assertThat(format("2026-10-26T09:00").lowercase()).isEqualTo("mon, 26 oct, 9:00 am")
        assertThat(format("2027-01-05T09:00").lowercase()).isEqualTo("5 jan 2027, 9:00 am")
    }

    @Test
    fun `24-hour phones get 24-hour times`() {
        assertThat(format("2026-10-22T18:00", is24 = true)).isEqualTo("Today, 18:00")
        assertThat(format("2026-10-23T07:30", is24 = true)).isEqualTo("Tomorrow, 07:30")
    }
}
