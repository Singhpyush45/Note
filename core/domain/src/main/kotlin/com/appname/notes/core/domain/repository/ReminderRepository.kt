package com.appname.notes.core.domain.repository

import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.DueReminder
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.Reminder
import com.appname.notes.core.domain.model.ReminderId
import com.appname.notes.core.domain.model.ReminderRepeat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Date/time reminders on notes and checklist items (FR-010). Reminders of trashed notes stay stored but
 * don't go off; restoring the note brings them back (a time that passed meanwhile goes off at once).
 */
interface ReminderRepository {
    /** The note's reminders, soonest first; updates live. */
    fun observe(noteId: NoteId): Flow<List<Reminder>>

    /** A new reminder at [at] (wall-clock time in [zone]); [blockId] makes it a task reminder. */
    suspend fun add(noteId: NoteId, blockId: String?, at: LocalDateTime, repeat: ReminderRepeat, zone: ZoneId): AppResult<Reminder>

    /** Re-sets a reminder (also one that already went off: it becomes pending again). */
    suspend fun update(id: ReminderId, at: LocalDateTime, repeat: ReminderRepeat, zone: ZoneId): AppResult<Reminder>

    /** Removes a reminder and returns it for undo ([restore]). */
    suspend fun remove(id: ReminderId): AppResult<Reminder>

    suspend fun restore(reminder: Reminder): AppResult<Unit>

    /** Moves a reminder that went off to [until] ("Snooze"). A repeating one goes off once more, then repeats as before. */
    suspend fun snooze(id: ReminderId, until: Instant): AppResult<Unit>

    /** When the next reminder of a note that isn't trashed goes off; null if none. Updates live. */
    fun observeNextDue(): Flow<Instant?>

    suspend fun nextDue(): AppResult<Instant?>

    /**
     * Takes every reminder due at [now]: one-off reminders are marked as gone off, repeating ones move to their
     * next time after [now] (missed repeats are not replayed one by one). Returns the ones to notify about.
     * A one-off reminder on a checklist item that is already ticked is taken silently.
     */
    suspend fun takeDue(now: Instant, zone: ZoneId): AppResult<List<DueReminder>>
}
