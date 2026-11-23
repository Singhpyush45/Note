package com.appname.notes.core.domain.repository

import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.CalendarEvent
import com.appname.notes.core.domain.model.CalendarEventId
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.TaskFilter
import com.appname.notes.core.domain.model.TaskId
import com.appname.notes.core.domain.model.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/**
 * Repository for To-do List tasks and Calendar events (Argon 4 §4–5).
 */
interface PlannerRepository {
    fun observeTasks(filter: TaskFilter = TaskFilter.ALL): Flow<List<TodoTask>>

    fun observeTasksForDate(date: LocalDate): Flow<List<TodoTask>>

    suspend fun getTask(id: TaskId): TodoTask?

    suspend fun createTask(
        title: String,
        description: String = "",
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        reminderAt: Instant? = null,
        sourceNoteId: NoteId? = null,
        sourceNoteTitle: String? = null,
    ): AppResult<TodoTask>

    suspend fun updateTask(
        id: TaskId,
        title: String,
        description: String = "",
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        reminderAt: Instant? = null,
        sourceNoteId: NoteId? = null,
        sourceNoteTitle: String? = null,
    ): AppResult<TodoTask>

    suspend fun setTaskCompleted(id: TaskId, completed: Boolean): AppResult<Unit>

    suspend fun deleteTask(id: TaskId): AppResult<Unit>

    fun observeEvents(): Flow<List<CalendarEvent>>

    fun observeEventsForMonth(yearMonth: YearMonth): Flow<List<CalendarEvent>>

    fun observeEventsForDate(date: LocalDate): Flow<List<CalendarEvent>>

    suspend fun getEvent(id: CalendarEventId): CalendarEvent?

    suspend fun createEvent(
        title: String,
        date: LocalDate,
        startTime: LocalTime? = null,
        endTime: LocalTime? = null,
        notes: String = "",
        linkedTaskId: TaskId? = null,
        sourceNoteId: NoteId? = null,
    ): AppResult<CalendarEvent>

    suspend fun updateEvent(
        id: CalendarEventId,
        title: String,
        date: LocalDate,
        startTime: LocalTime? = null,
        endTime: LocalTime? = null,
        notes: String = "",
        linkedTaskId: TaskId? = null,
        sourceNoteId: NoteId? = null,
    ): AppResult<CalendarEvent>

    suspend fun deleteEvent(id: CalendarEventId): AppResult<Unit>
}
