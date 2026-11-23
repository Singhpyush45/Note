package com.appname.notes.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A date/time reminder on a note or one of its checklist items (schema v7, FR-010). */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("noteId"), Index(value = ["firedAt", "triggerAt"])],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    /** The checklist item (block id) for a task reminder; null for the whole note. */
    val blockId: String?,
    /** The picked wall-clock time, ISO-8601 local date-time ("2026-10-25T09:00"); repeats are counted from it. */
    val anchorLocal: String,
    /** Epoch millis of the next time it goes off. */
    val triggerAt: Long,
    /** ReminderRepeat name. */
    val repeat: String,
    /** Epoch millis when a one-off reminder went off; null while pending. */
    val firedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A due reminder with what its notification needs from the note. */
data class DueReminderRow(
    val id: String,
    val noteId: String,
    val blockId: String?,
    val anchorLocal: String,
    val triggerAt: Long,
    val repeat: String,
    val firedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val noteTitle: String,
    val notePreview: String,
    val noteType: String,
    val contentJson: String,
) {
    fun reminder() = ReminderEntity(id, noteId, blockId, anchorLocal, triggerAt, repeat, firedAt, createdAt, updatedAt)
}
