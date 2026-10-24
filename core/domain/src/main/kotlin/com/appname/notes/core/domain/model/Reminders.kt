package com.appname.notes.core.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@JvmInline value class ReminderId(val value: String)

/** How a reminder comes back (FR-010). Stored by name: never rename. Unknown names read as [NONE]. */
enum class ReminderRepeat {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    ;

    /**
     * The [n]th occurrence counted from [anchor] (n = 0 is the anchor itself). Always counted from the anchor,
     * so "monthly on the 31st" comes back on the 31st after a short month instead of drifting to the 30th.
     */
    fun occurrence(anchor: LocalDateTime, n: Long): LocalDateTime = when (this) {
        NONE -> anchor
        DAILY -> anchor.plusDays(n)
        WEEKLY -> anchor.plusWeeks(n)
        MONTHLY -> anchor.plusMonths(n)
        YEARLY -> anchor.plusYears(n)
    }

    /**
     * The first occurrence strictly after [after], as a moment in [zone]; null for [NONE].
     * The wall-clock time is kept across daylight-saving changes and time zones ("every day at 9:00" stays
     * 9:00 after travelling); a time skipped by a DST jump moves forward by the gap.
     */
    fun nextAfter(anchor: LocalDateTime, after: Instant, zone: ZoneId): Instant? {
        val unit = when (this) {
            NONE -> return null
            DAILY -> ChronoUnit.DAYS
            WEEKLY -> ChronoUnit.WEEKS
            MONTHLY -> ChronoUnit.MONTHS
            YEARLY -> ChronoUnit.YEARS
        }
        val local = LocalDateTime.ofInstant(after, zone)
        var n = maxOf(0L, unit.between(anchor, local) - 1)
        while (true) {
            val at = occurrence(anchor, n).atZone(zone).toInstant()
            if (at.isAfter(after)) return at
            n++
        }
    }

    companion object {
        fun fromName(name: String): ReminderRepeat = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/**
 * A date/time reminder on a note, or on one checklist item of it ([blockId], FR-010 "notes or tasks").
 *
 * [anchor] is the wall-clock time the user picked; [triggerAt] the next moment it goes off. A one-off
 * reminder that has gone off keeps [firedAt] so it still shows (as past) until the user removes or re-sets it.
 */
data class Reminder(
    val id: ReminderId,
    val noteId: NoteId,
    val blockId: String?,
    val anchor: LocalDateTime,
    val triggerAt: Instant,
    val repeat: ReminderRepeat,
    val firedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isPending: Boolean get() = firedAt == null
}

/** A reminder that has just come due, with what its notification shows. */
data class DueReminder(
    val reminder: Reminder,
    val noteTitle: String,
    val notePreview: String,
    val noteType: NoteType,
    /** The checklist item's text for a task reminder (null for a note reminder, or if the item was deleted). */
    val taskText: String?,
    /** When it was due; much earlier than now means it was missed (phone off, app force-stopped). */
    val dueAt: Instant,
)

/** The quick choices in the reminder dialog, like the clock and calendar apps offer. */
object ReminderPresets {
    val MORNING: LocalTime = LocalTime.of(9, 0)
    val EVENING: LocalTime = LocalTime.of(18, 0)

    enum class Kind { LATER_TODAY, THIS_EVENING, TOMORROW_MORNING, NEXT_WEEK }

    data class Preset(val kind: Kind, val at: LocalDateTime)

    /**
     * - "Later today": the next full hour at least an hour away, offered only until 20:00;
     * - "This evening" 18:00 while it's still before 17:00;
     * - "Tomorrow morning" 9:00;
     * - "Next week": Monday 9:00.
     */
    fun at(now: ZonedDateTime): List<Preset> {
        val local = now.toLocalDateTime().withSecond(0).withNano(0)
        val today = local.toLocalDate()
        return buildList {
            if (local.toLocalTime() < LocalTime.of(20, 0)) {
                val later = local.truncatedTo(ChronoUnit.HOURS).plusHours(if (local.minute == 0) 1 else 2)
                if (later.toLocalDate() == today) add(Preset(Kind.LATER_TODAY, later))
            }
            if (local.toLocalTime() < LocalTime.of(17, 0)) add(Preset(Kind.THIS_EVENING, today.atTime(EVENING)))
            add(Preset(Kind.TOMORROW_MORNING, today.plusDays(1).atTime(MORNING)))
            add(Preset(Kind.NEXT_WEEK, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(MORNING)))
        }.distinctBy { it.at }
    }
}
