package com.appname.notes.core.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.domain.model.CalendarEvent
import com.appname.notes.core.domain.model.CalendarEventId
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.TaskFilter
import com.appname.notes.core.domain.model.TaskId
import com.appname.notes.core.domain.model.TodoTask
import com.appname.notes.core.domain.repository.PlannerRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Persists To-do List tasks and Calendar events inside the existing [NotesDatabase] (`notes.db`).
 *
 * Uses SQLite tables (`todo_tasks` and `calendar_events`) created idempotently on the underlying
 * [SupportSQLiteDatabase] so that tasks and calendar entries are stored in `notes.db` and included
 * automatically in backups/restores without altering Room's generated v10 schema hash.
 */
class RoomPlannerRepository(
    private val db: NotesDatabase,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val logger: AppLogger,
) : PlannerRepository {

    private val schemaReady = AtomicBoolean(false)
    private val changes = MutableStateFlow(0L)

    private fun writableDb(): SupportSQLiteDatabase {
        val sdb = db.openHelper.writableDatabase
        if (schemaReady.compareAndSet(false, true)) {
            ensureTables(sdb)
        }
        return sdb
    }

    private fun ensureTables(sdb: SupportSQLiteDatabase) {
        sdb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `todo_tasks` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL DEFAULT '',
                `isCompleted` INTEGER NOT NULL DEFAULT 0,
                `dueDate` TEXT,
                `dueTime` TEXT,
                `reminderAt` INTEGER,
                `sourceNoteId` TEXT,
                `sourceNoteTitle` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        sdb.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_tasks_dueDate` ON `todo_tasks` (`dueDate`)")
        sdb.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_tasks_isCompleted` ON `todo_tasks` (`isCompleted`)")
        sdb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calendar_events` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `title` TEXT NOT NULL,
                `notes` TEXT NOT NULL DEFAULT '',
                `date` TEXT NOT NULL,
                `startTime` TEXT,
                `endTime` TEXT,
                `linkedTaskId` TEXT,
                `sourceNoteId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        sdb.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_events_date` ON `calendar_events` (`date`)")
    }

    private fun notifyChanged() {
        changes.value = changes.value + 1
    }

    override fun observeTasks(filter: TaskFilter): Flow<List<TodoTask>> =
        changes.map {
            withContext(Dispatchers.IO) { queryTasks(filter) }
        }.distinctUntilChanged()

    override fun observeTasksForDate(date: LocalDate): Flow<List<TodoTask>> =
        changes.map {
            withContext(Dispatchers.IO) { queryTasksForDate(date) }
        }.distinctUntilChanged()

    override suspend fun getTask(id: TaskId): TodoTask? = withContext(Dispatchers.IO) {
        queryTaskById(id.value)
    }

    override suspend fun createTask(
        title: String,
        description: String,
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        reminderAt: Instant?,
        sourceNoteId: NoteId?,
        sourceNoteTitle: String?,
    ): AppResult<TodoTask> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return AppResult.Failure(DomainError.Validation("title", "Task title must not be empty"))
        }
        return storageCall(logger, "Create task") {
            val now = clock.now()
            val nowMs = now.toEpochMilli()
            val task = TodoTask(
                id = TaskId(ids.newId()),
                title = cleanTitle,
                description = description.trim(),
                isCompleted = false,
                dueDate = dueDate,
                dueTime = dueTime,
                reminderAt = reminderAt,
                sourceNoteId = sourceNoteId,
                sourceNoteTitle = sourceNoteTitle?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now,
            )
            val values = ContentValues().apply {
                put("id", task.id.value)
                put("title", task.title)
                put("description", task.description)
                put("isCompleted", 0)
                put("dueDate", task.dueDate?.toString())
                put("dueTime", task.dueTime?.toString())
                val rem = task.reminderAt
                if (rem != null) put("reminderAt", rem.toEpochMilli()) else putNull("reminderAt")
                put("sourceNoteId", task.sourceNoteId?.value)
                put("sourceNoteTitle", task.sourceNoteTitle)
                put("createdAt", nowMs)
                put("updatedAt", nowMs)
            }
            writableDb().insert("todo_tasks", SQLiteDatabase.CONFLICT_REPLACE, values)
            notifyChanged()
            task
        }
    }

    override suspend fun updateTask(
        id: TaskId,
        title: String,
        description: String,
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        reminderAt: Instant?,
        sourceNoteId: NoteId?,
        sourceNoteTitle: String?,
    ): AppResult<TodoTask> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return AppResult.Failure(DomainError.Validation("title", "Task title must not be empty"))
        }
        return when (val r = storageCall(logger, "Update task") {
            val existing = queryTaskById(id.value) ?: return@storageCall null
            val now = clock.now()
            val updated = existing.copy(
                title = cleanTitle,
                description = description.trim(),
                dueDate = dueDate,
                dueTime = dueTime,
                reminderAt = reminderAt,
                sourceNoteId = sourceNoteId,
                sourceNoteTitle = sourceNoteTitle?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = now,
            )
            val values = ContentValues().apply {
                put("title", updated.title)
                put("description", updated.description)
                put("dueDate", updated.dueDate?.toString())
                put("dueTime", updated.dueTime?.toString())
                val rem = updated.reminderAt
                if (rem != null) put("reminderAt", rem.toEpochMilli()) else putNull("reminderAt")
                put("sourceNoteId", updated.sourceNoteId?.value)
                put("sourceNoteTitle", updated.sourceNoteTitle)
                put("updatedAt", now.toEpochMilli())
            }
            writableDb().update("todo_tasks", SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf(id.value))
            notifyChanged()
            updated
        }) {
            is AppResult.Failure -> r
            is AppResult.Success -> r.value?.let { AppResult.Success(it) } ?: AppResult.Failure(DomainError.NotFound("Task", id.value))
        }
    }

    override suspend fun setTaskCompleted(id: TaskId, completed: Boolean): AppResult<Unit> =
        when (val r = storageCall(logger, "Toggle task") {
            val values = ContentValues().apply {
                put("isCompleted", if (completed) 1 else 0)
                put("updatedAt", clock.now().toEpochMilli())
            }
            val rows = writableDb().update("todo_tasks", SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf(id.value))
            if (rows > 0) notifyChanged()
            rows
        }) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value > 0) AppResult.Success(Unit) else AppResult.Failure(DomainError.NotFound("Task", id.value))
        }

    override suspend fun deleteTask(id: TaskId): AppResult<Unit> =
        when (val r = storageCall(logger, "Delete task") {
            val rows = writableDb().delete("todo_tasks", "id = ?", arrayOf(id.value))
            if (rows > 0) notifyChanged()
            rows
        }) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value > 0) AppResult.Success(Unit) else AppResult.Failure(DomainError.NotFound("Task", id.value))
        }

    override fun observeEvents(): Flow<List<CalendarEvent>> =
        changes.map {
            withContext(Dispatchers.IO) { queryAllEvents() }
        }.distinctUntilChanged()

    override fun observeEventsForMonth(yearMonth: YearMonth): Flow<List<CalendarEvent>> =
        changes.map {
            withContext(Dispatchers.IO) {
                val start = yearMonth.atDay(1).toString()
                val end = yearMonth.atEndOfMonth().toString()
                queryEventsInRange(start, end)
            }
        }.distinctUntilChanged()

    override fun observeEventsForDate(date: LocalDate): Flow<List<CalendarEvent>> =
        changes.map {
            withContext(Dispatchers.IO) {
                val day = date.toString()
                queryEventsInRange(day, day)
            }
        }.distinctUntilChanged()

    override suspend fun getEvent(id: CalendarEventId): CalendarEvent? = withContext(Dispatchers.IO) {
        queryEventById(id.value)
    }

    override suspend fun createEvent(
        title: String,
        date: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        notes: String,
        linkedTaskId: TaskId?,
        sourceNoteId: NoteId?,
    ): AppResult<CalendarEvent> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return AppResult.Failure(DomainError.Validation("title", "Event title must not be empty"))
        }
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            return AppResult.Failure(DomainError.Validation("endTime", "End time must not be before start time"))
        }
        return storageCall(logger, "Create calendar event") {
            val now = clock.now()
            val nowMs = now.toEpochMilli()
            val event = CalendarEvent(
                id = CalendarEventId(ids.newId()),
                title = cleanTitle,
                notes = notes.trim(),
                date = date,
                startTime = startTime,
                endTime = endTime,
                linkedTaskId = linkedTaskId,
                sourceNoteId = sourceNoteId,
                createdAt = now,
                updatedAt = now,
            )
            val values = ContentValues().apply {
                put("id", event.id.value)
                put("title", event.title)
                put("notes", event.notes)
                put("date", event.date.toString())
                put("startTime", event.startTime?.toString())
                put("endTime", event.endTime?.toString())
                put("linkedTaskId", event.linkedTaskId?.value)
                put("sourceNoteId", event.sourceNoteId?.value)
                put("createdAt", nowMs)
                put("updatedAt", nowMs)
            }
            writableDb().insert("calendar_events", SQLiteDatabase.CONFLICT_REPLACE, values)
            notifyChanged()
            event
        }
    }

    override suspend fun updateEvent(
        id: CalendarEventId,
        title: String,
        date: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        notes: String,
        linkedTaskId: TaskId?,
        sourceNoteId: NoteId?,
    ): AppResult<CalendarEvent> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return AppResult.Failure(DomainError.Validation("title", "Event title must not be empty"))
        }
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            return AppResult.Failure(DomainError.Validation("endTime", "End time must not be before start time"))
        }
        return when (val r = storageCall(logger, "Update calendar event") {
            val existing = queryEventById(id.value) ?: return@storageCall null
            val now = clock.now()
            val updated = existing.copy(
                title = cleanTitle,
                notes = notes.trim(),
                date = date,
                startTime = startTime,
                endTime = endTime,
                linkedTaskId = linkedTaskId,
                sourceNoteId = sourceNoteId,
                updatedAt = now,
            )
            val values = ContentValues().apply {
                put("title", updated.title)
                put("notes", updated.notes)
                put("date", updated.date.toString())
                put("startTime", updated.startTime?.toString())
                put("endTime", updated.endTime?.toString())
                put("linkedTaskId", updated.linkedTaskId?.value)
                put("sourceNoteId", updated.sourceNoteId?.value)
                put("updatedAt", now.toEpochMilli())
            }
            writableDb().update("calendar_events", SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf(id.value))
            notifyChanged()
            updated
        }) {
            is AppResult.Failure -> r
            is AppResult.Success -> r.value?.let { AppResult.Success(it) } ?: AppResult.Failure(DomainError.NotFound("CalendarEvent", id.value))
        }
    }

    override suspend fun deleteEvent(id: CalendarEventId): AppResult<Unit> =
        when (val r = storageCall(logger, "Delete calendar event") {
            val rows = writableDb().delete("calendar_events", "id = ?", arrayOf(id.value))
            if (rows > 0) notifyChanged()
            rows
        }) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value > 0) AppResult.Success(Unit) else AppResult.Failure(DomainError.NotFound("CalendarEvent", id.value))
        }

    private fun queryTasks(filter: TaskFilter): List<TodoTask> {
        val where = when (filter) {
            TaskFilter.ALL -> ""
            TaskFilter.PENDING -> "WHERE isCompleted = 0"
            TaskFilter.COMPLETED -> "WHERE isCompleted = 1"
        }
        val sql = """
            SELECT id, title, description, isCompleted, dueDate, dueTime, reminderAt, sourceNoteId, sourceNoteTitle, createdAt, updatedAt
            FROM todo_tasks
            $where
            ORDER BY isCompleted ASC,
                     CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END ASC,
                     dueDate ASC,
                     CASE WHEN dueTime IS NULL THEN 1 ELSE 0 END ASC,
                     dueTime ASC,
                     updatedAt DESC,
                     id ASC
        """.trimIndent()
        return writableDb().query(sql).use { c ->
            buildList {
                while (c.moveToNext()) add(readTask(c))
            }
        }
    }

    private fun queryTasksForDate(date: LocalDate): List<TodoTask> {
        val sql = """
            SELECT id, title, description, isCompleted, dueDate, dueTime, reminderAt, sourceNoteId, sourceNoteTitle, createdAt, updatedAt
            FROM todo_tasks
            WHERE dueDate = ?
            ORDER BY isCompleted ASC,
                     CASE WHEN dueTime IS NULL THEN 1 ELSE 0 END ASC,
                     dueTime ASC,
                     updatedAt DESC
        """.trimIndent()
        return writableDb().query(sql, arrayOf(date.toString())).use { c ->
            buildList {
                while (c.moveToNext()) add(readTask(c))
            }
        }
    }

    private fun queryTaskById(id: String): TodoTask? {
        val sql = """
            SELECT id, title, description, isCompleted, dueDate, dueTime, reminderAt, sourceNoteId, sourceNoteTitle, createdAt, updatedAt
            FROM todo_tasks
            WHERE id = ?
        """.trimIndent()
        return writableDb().query(sql, arrayOf(id)).use { c ->
            if (c.moveToFirst()) readTask(c) else null
        }
    }

    private fun readTask(c: android.database.Cursor): TodoTask = TodoTask(
        id = TaskId(c.getString(0)),
        title = c.getString(1),
        description = c.getString(2).orEmpty(),
        isCompleted = c.getInt(3) != 0,
        dueDate = if (c.isNull(4)) null else runCatching { LocalDate.parse(c.getString(4)) }.getOrNull(),
        dueTime = if (c.isNull(5)) null else runCatching { LocalTime.parse(c.getString(5)) }.getOrNull(),
        reminderAt = if (c.isNull(6)) null else Instant.ofEpochMilli(c.getLong(6)),
        sourceNoteId = if (c.isNull(7)) null else NoteId(c.getString(7)),
        sourceNoteTitle = if (c.isNull(8)) null else c.getString(8),
        createdAt = Instant.ofEpochMilli(c.getLong(9)),
        updatedAt = Instant.ofEpochMilli(c.getLong(10)),
    )

    private fun queryAllEvents(): List<CalendarEvent> {
        val sql = """
            SELECT id, title, notes, date, startTime, endTime, linkedTaskId, sourceNoteId, createdAt, updatedAt
            FROM calendar_events
            ORDER BY date ASC,
                     CASE WHEN startTime IS NULL THEN 1 ELSE 0 END ASC,
                     startTime ASC,
                     updatedAt DESC
        """.trimIndent()
        return writableDb().query(sql).use { c ->
            buildList {
                while (c.moveToNext()) add(readEvent(c))
            }
        }
    }

    private fun queryEventsInRange(startDate: String, endDate: String): List<CalendarEvent> {
        val sql = """
            SELECT id, title, notes, date, startTime, endTime, linkedTaskId, sourceNoteId, createdAt, updatedAt
            FROM calendar_events
            WHERE date >= ? AND date <= ?
            ORDER BY date ASC,
                     CASE WHEN startTime IS NULL THEN 1 ELSE 0 END ASC,
                     startTime ASC,
                     updatedAt DESC
        """.trimIndent()
        return writableDb().query(sql, arrayOf(startDate, endDate)).use { c ->
            buildList {
                while (c.moveToNext()) add(readEvent(c))
            }
        }
    }

    private fun queryEventById(id: String): CalendarEvent? {
        val sql = """
            SELECT id, title, notes, date, startTime, endTime, linkedTaskId, sourceNoteId, createdAt, updatedAt
            FROM calendar_events
            WHERE id = ?
        """.trimIndent()
        return writableDb().query(sql, arrayOf(id)).use { c ->
            if (c.moveToFirst()) readEvent(c) else null
        }
    }

    private fun readEvent(c: android.database.Cursor): CalendarEvent = CalendarEvent(
        id = CalendarEventId(c.getString(0)),
        title = c.getString(1),
        notes = c.getString(2).orEmpty(),
        date = LocalDate.parse(c.getString(3)),
        startTime = if (c.isNull(4)) null else runCatching { LocalTime.parse(c.getString(4)) }.getOrNull(),
        endTime = if (c.isNull(5)) null else runCatching { LocalTime.parse(c.getString(5)) }.getOrNull(),
        linkedTaskId = if (c.isNull(6)) null else TaskId(c.getString(6)),
        sourceNoteId = if (c.isNull(7)) null else NoteId(c.getString(7)),
        createdAt = Instant.ofEpochMilli(c.getLong(8)),
        updatedAt = Instant.ofEpochMilli(c.getLong(9)),
    )
}
