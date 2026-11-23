package com.appname.notes.core.domain.model

import com.appname.notes.core.domain.model.ReminderPresets.Kind
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class RemindersTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val london = ZoneId.of("Europe/London")

    private fun at(text: String, zone: ZoneId): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test
    fun `one-off reminders don't come back`() {
        assertThat(ReminderRepeat.NONE.nextAfter(LocalDateTime.parse("2026-10-25T09:00"), at("2026-10-25T09:00", kolkata), kolkata)).isNull()
    }

    @Test
    fun `daily goes to the next day at the same time, skipping missed days`() {
        val anchor = LocalDateTime.parse("2026-10-01T09:00")
        // Fired on time: tomorrow 9:00.
        assertThat(ReminderRepeat.DAILY.nextAfter(anchor, at("2026-10-01T09:00", kolkata), kolkata)).isEqualTo(at("2026-10-02T09:00", kolkata))
        // Phone was off for days: the next 9:00 after now, not each missed day.
        assertThat(ReminderRepeat.DAILY.nextAfter(anchor, at("2026-10-20T13:30", kolkata), kolkata)).isEqualTo(at("2026-10-21T09:00", kolkata))
        // Before today's time: today.
        assertThat(ReminderRepeat.DAILY.nextAfter(anchor, at("2026-10-20T08:59", kolkata), kolkata)).isEqualTo(at("2026-10-20T09:00", kolkata))
    }

    @Test
    fun `daily keeps the wall-clock time across a daylight-saving change`() {
        // UK clocks go back on 25 Oct 2026: 9:00 is still 9:00 local, one hour later in UTC.
        val anchor = LocalDateTime.parse("2026-10-24T09:00")
        val next = ReminderRepeat.DAILY.nextAfter(anchor, at("2026-10-24T09:00", london), london)!!
        assertThat(next).isEqualTo(Instant.parse("2026-10-25T09:00:00Z"))
        assertThat(at("2026-10-24T09:00", london)).isEqualTo(Instant.parse("2026-10-24T08:00:00Z"))
    }

    @Test
    fun `a time skipped by the spring-forward jump moves forward by the gap`() {
        // 29 Mar 2026, 01:00-02:00 doesn't exist in London.
        val anchor = LocalDateTime.parse("2026-03-28T01:30")
        val next = ReminderRepeat.DAILY.nextAfter(anchor, at("2026-03-28T01:30", london), london)!!
        assertThat(next.atZone(london).toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-03-29T02:30"))
    }

    @Test
    fun `monthly on the 31st doesn't drift after short months`() {
        val anchor = LocalDateTime.parse("2026-01-31T10:00")
        val feb = ReminderRepeat.MONTHLY.nextAfter(anchor, at("2026-01-31T10:00", kolkata), kolkata)!!
        assertThat(feb).isEqualTo(at("2026-02-28T10:00", kolkata))
        val mar = ReminderRepeat.MONTHLY.nextAfter(anchor, feb, kolkata)!!
        assertThat(mar).isEqualTo(at("2026-03-31T10:00", kolkata)) // back to the 31st, not the 28th
    }

    @Test
    fun `weekly and yearly`() {
        val anchor = LocalDateTime.parse("2026-10-19T18:00") // a Monday
        assertThat(ReminderRepeat.WEEKLY.nextAfter(anchor, at("2026-10-22T12:00", kolkata), kolkata)).isEqualTo(at("2026-10-26T18:00", kolkata))
        val birthday = LocalDateTime.parse("2024-02-29T08:00")
        assertThat(ReminderRepeat.YEARLY.nextAfter(birthday, at("2026-01-01T00:00", kolkata), kolkata)).isEqualTo(at("2026-02-28T08:00", kolkata))
        assertThat(ReminderRepeat.YEARLY.nextAfter(birthday, at("2027-03-01T00:00", kolkata), kolkata)).isEqualTo(at("2028-02-29T08:00", kolkata))
    }

    @Test
    fun `unknown repeat names from a newer version read as one-off`() {
        assertThat(ReminderRepeat.fromName("HOURLY")).isEqualTo(ReminderRepeat.NONE)
        assertThat(ReminderRepeat.fromName("WEEKLY")).isEqualTo(ReminderRepeat.WEEKLY)
    }

    @Test
    fun `presets in the morning`() {
        // Thursday 22 Oct 2026, 10:20.
        val presets = ReminderPresets.at(ZonedDateTime.of(LocalDateTime.parse("2026-10-22T10:20:31"), kolkata))
        assertThat(presets.map { it.kind to it.at.toString() }).containsExactly(
            Kind.LATER_TODAY to "2026-10-22T12:00",
            Kind.THIS_EVENING to "2026-10-22T18:00",
            Kind.TOMORROW_MORNING to "2026-10-23T09:00",
            Kind.NEXT_WEEK to "2026-10-26T09:00",
        ).inOrder()
    }

    @Test
    fun `presets late in the day and on the hour`() {
        val evening = ReminderPresets.at(ZonedDateTime.of(LocalDateTime.parse("2026-10-22T17:00"), kolkata))
        assertThat(evening.map { it.kind to it.at.toString() }).containsExactly(
            Kind.LATER_TODAY to "2026-10-22T18:00",
            Kind.TOMORROW_MORNING to "2026-10-23T09:00",
            Kind.NEXT_WEEK to "2026-10-26T09:00",
        ).inOrder()
        val night = ReminderPresets.at(ZonedDateTime.of(LocalDateTime.parse("2026-10-22T22:15"), kolkata))
        assertThat(night.map { it.kind }).containsExactly(Kind.TOMORROW_MORNING, Kind.NEXT_WEEK).inOrder()
        // On a Sunday, "next week" is tomorrow; the duplicate of "tomorrow morning" is dropped.
        val sunday = ReminderPresets.at(ZonedDateTime.of(LocalDateTime.parse("2026-10-25T21:00"), kolkata))
        assertThat(sunday.map { it.kind to it.at.toString() }).containsExactly(Kind.TOMORROW_MORNING to "2026-10-26T09:00")
    }
}
