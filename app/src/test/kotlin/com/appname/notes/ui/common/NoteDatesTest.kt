package com.appname.notes.ui.common

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

class NoteDatesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-10-21T09:00:00Z") // 14:30 IST

    @Test
    fun `today shows time, this year shows day and month, older shows the year`() {
        assertThat(NoteDates.format(Instant.parse("2026-10-21T04:00:00Z"), now, zone, Locale.UK)).isEqualTo("09:30")
        assertThat(NoteDates.format(Instant.parse("2026-03-05T04:00:00Z"), now, zone, Locale.UK)).isEqualTo("5 Mar")
        assertThat(NoteDates.format(Instant.parse("2025-12-31T04:00:00Z"), now, zone, Locale.UK)).isEqualTo("31 Dec 2025")
    }

    @Test
    fun `day boundaries use the local time zone`() {
        // 20 Oct 20:00 UTC is already 21 Oct 01:30 in India -> "today".
        assertThat(NoteDates.format(Instant.parse("2026-10-20T20:00:00Z"), now, zone, Locale.UK)).isEqualTo("01:30")
    }
}
