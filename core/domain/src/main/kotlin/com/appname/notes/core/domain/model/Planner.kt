package com.appname.notes.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@JvmInline
value class TaskId(val value: String)

enum class TaskFilter {
    ALL,
    PENDING,
    COMPLETED,
}

/**
 * A user task in the To-do List (Argon 4 §4).
 *
 * Persisted in the local database, optionally associated with a due date, reminder time, and source note.
 */
data class TodoTask(
    val id: TaskId,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val reminderAt: Instant? = null,
    val sourceNoteId: NoteId? = null,
    val sourceNoteTitle: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@JvmInline
value class CalendarEventId(val value: String)

/**
 * A scheduled calendar entry (Argon 4 §5).
 *
 * Supports a date, optional start/end times, optional notes, and optional links to a To-do task or note.
 */
data class CalendarEvent(
    val id: CalendarEventId,
    val title: String,
    val notes: String = "",
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val linkedTaskId: TaskId? = null,
    val sourceNoteId: NoteId? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
