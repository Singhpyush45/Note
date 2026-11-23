package com.appname.notes.core.database

import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.database.entity.ReminderEntity
import com.appname.notes.core.domain.model.DueReminder
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.model.Reminder
import com.appname.notes.core.domain.model.ReminderId
import com.appname.notes.core.domain.model.ReminderRepeat
import com.appname.notes.core.domain.repository.ReminderRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomReminderRepository(
    db: NotesDatabase,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val logger: AppLogger,
) : ReminderRepository {

    private val dao = db.reminderDao()

    /** Two alarm deliveries at once must not both notify about the same reminder. */
    private val taking = Mutex()

    override fun observe(noteId: NoteId): Flow<List<Reminder>> = dao.observe(noteId.value).map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(noteId: NoteId, blockId: String?, at: LocalDateTime, repeat: ReminderRepeat, zone: ZoneId): AppResult<Reminder> {
        val now = clock.now().toEpochMilli()
        val row = ReminderEntity(
            id = ids.newId(),
            noteId = noteId.value,
            blockId = blockId,
            anchorLocal = at.withNano(0).toString(),
            triggerAt = at.atZone(zone).toInstant().toEpochMilli(),
            repeat = repeat.name,
            firedAt = null,
            createdAt = now,
            updatedAt = now,
        )
        return when (val r = storageCall(logger, "Add reminder") { dao.insert(row) }) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value) AppResult.Success(row.toDomain()) else AppResult.Failure(DomainError.NotFound("Note", noteId.value))
        }
    }

    override suspend fun update(id: ReminderId, at: LocalDateTime, repeat: ReminderRepeat, zone: ZoneId): AppResult<Reminder> =
        change(id, "Update reminder") {
            it.copy(
                anchorLocal = at.withNano(0).toString(),
                triggerAt = at.atZone(zone).toInstant().toEpochMilli(),
                repeat = repeat.name,
                firedAt = null,
            )
        }

    override suspend fun remove(id: ReminderId): AppResult<Reminder> {
        val row = dao.get(id.value) ?: return AppResult.Failure(DomainError.NotFound("Reminder", id.value))
        return when (val r = storageCall(logger, "Remove reminder") { dao.delete(id.value) }) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value > 0) AppResult.Success(row.toDomain()) else AppResult.Failure(DomainError.NotFound("Reminder", id.value))
        }
    }

    override suspend fun restore(reminder: Reminder): AppResult<Unit> =
        when (val r = storageCall(logger, "Restore reminder") { dao.insert(reminder.toEntity()) }) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value) AppResult.Success(Unit) else AppResult.Failure(DomainError.NotFound("Note", reminder.noteId.value))
        }

    override suspend fun snooze(id: ReminderId, until: Instant): AppResult<Unit> =
        when (val r = change(id, "Snooze reminder") { it.copy(triggerAt = until.toEpochMilli(), firedAt = null) }) {
            is AppResult.Failure -> r
            is AppResult.Success -> AppResult.Success(Unit)
        }

    override fun observeNextDue(): Flow<Instant?> = dao.observeNextDue().map { it?.let(Instant::ofEpochMilli) }

    override suspend fun nextDue(): AppResult<Instant?> = storageCall(logger, "Next reminder") { dao.nextDue()?.let(Instant::ofEpochMilli) }

    override suspend fun takeDue(now: Instant, zone: ZoneId): AppResult<List<DueReminder>> = taking.withLock {
        storageCall(logger, "Take due reminders") {
            val nowMs = now.toEpochMilli()
            val rows = dao.due(nowMs)
            val fired = ArrayList<String>()
            val advanced = HashMap<String, Long>()
            val notify = ArrayList<DueReminder>()
            for (row in rows) {
                val reminder = row.reminder().toDomain()
                val next = reminder.repeat.nextAfter(reminder.anchor, now, zone)
                if (next == null) fired += row.id else advanced[row.id] = next.toEpochMilli()

                val task = row.blockId?.let { blockId ->
                    decodeDocument(row.contentJson, row.noteId, logger).textBlocks.firstOrNull { it.id == blockId }
                }
                // A ticked task needs no reminder; a repeating one still comes back (it's about the next time).
                if (task != null && task.checked && reminder.repeat == ReminderRepeat.NONE) continue
                notify += DueReminder(
                    reminder = reminder,
                    noteTitle = row.noteTitle,
                    notePreview = row.notePreview,
                    noteType = NoteType.entries.firstOrNull { it.name == row.noteType } ?: NoteType.TEXT,
                    taskText = task?.text?.text?.takeIf { it.isNotBlank() },
                    dueAt = reminder.triggerAt,
                )
            }
            if (rows.isNotEmpty()) dao.settle(fired, advanced, nowMs)
            notify
        }
    }

    private suspend fun change(id: ReminderId, what: String, transform: (ReminderEntity) -> ReminderEntity): AppResult<Reminder> {
        val row = dao.get(id.value) ?: return AppResult.Failure(DomainError.NotFound("Reminder", id.value))
        val updated = transform(row).copy(updatedAt = clock.now().toEpochMilli())
        return storageCall(logger, what) {
            dao.upsert(updated)
            updated.toDomain()
        }
    }

    private fun ReminderEntity.toDomain(): Reminder {
        val trigger = Instant.ofEpochMilli(triggerAt)
        return Reminder(
            id = ReminderId(id),
            noteId = NoteId(noteId),
            blockId = blockId,
            anchor = try {
                LocalDateTime.parse(anchorLocal)
            } catch (e: DateTimeParseException) {
                LocalDateTime.ofInstant(trigger, ZoneId.systemDefault())
            },
            triggerAt = trigger,
            repeat = ReminderRepeat.fromName(repeat),
            firedAt = firedAt?.let(Instant::ofEpochMilli),
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }

    private fun Reminder.toEntity() = ReminderEntity(
        id = id.value,
        noteId = noteId.value,
        blockId = blockId,
        anchorLocal = anchor.toString(),
        triggerAt = triggerAt.toEpochMilli(),
        repeat = repeat.name,
        firedAt = firedAt?.toEpochMilli(),
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )
}
