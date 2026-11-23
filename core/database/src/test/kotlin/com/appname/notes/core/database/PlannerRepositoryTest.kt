package com.appname.notes.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.id.UuidV7Generator
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.time.FixedClock
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.TaskFilter
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannerRepositoryTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = FixedClock(LocalDateTime.parse("2026-10-29T10:00").atZone(zone).toInstant())
    private val ids = UuidV7Generator(clock)
    private val db = NotesDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val planner = RoomPlannerRepository(db, clock, ids, NoOpLogger)

    @After
    fun close() = db.close()

    private fun <T> AppResult<T>.ok(): T = (this as AppResult.Success).value

    @Test
    fun `tasks support create, edit, completion toggle, filtering, and deletion`() = runTest {
        val due = LocalDate.of(2026, 10, 30)
        val t1 = planner.createTask(
            title = "Revise organic chemistry",
            description = "Chapters 4 and 5",
            dueDate = due,
            dueTime = LocalTime.of(18, 0),
            reminderAt = null,
            sourceNoteId = NoteId("note-1"),
            sourceNoteTitle = "Chemistry",
        ).ok()
        val t2 = planner.createTask(
            title = "Submit assignment",
            description = "",
            dueDate = null,
            dueTime = null,
            reminderAt = null,
            sourceNoteId = null,
            sourceNoteTitle = null,
        ).ok()

        assertThat(planner.observeTasks(TaskFilter.PENDING).first().map { it.id })
            .containsExactly(t1.id, t2.id)
        assertThat(planner.observeTasks(TaskFilter.COMPLETED).first()).isEmpty()
        assertThat(planner.observeTasksForDate(due).first().map { it.id }).containsExactly(t1.id)

        // Mark t1 completed
        planner.setTaskCompleted(t1.id, true).ok()
        assertThat(planner.observeTasks(TaskFilter.PENDING).first().map { it.id }).containsExactly(t2.id)
        assertThat(planner.observeTasks(TaskFilter.COMPLETED).first().map { it.id }).containsExactly(t1.id)
        assertThat(planner.observeTasks(TaskFilter.ALL).first()).hasSize(2)

        // Update t2 details
        val updated = planner.updateTask(
            id = t2.id,
            title = "Submit physics lab report",
            description = "Include graphs",
            dueDate = due,
            dueTime = LocalTime.of(9, 30),
            reminderAt = null,
            sourceNoteId = NoteId("note-2"),
            sourceNoteTitle = "Physics",
        ).ok()
        assertThat(updated.title).isEqualTo("Submit physics lab report")
        assertThat(updated.sourceNoteId).isEqualTo(NoteId("note-2"))

        // Delete t1
        planner.deleteTask(t1.id).ok()
        assertThat(planner.observeTasks(TaskFilter.ALL).first().map { it.id }).containsExactly(t2.id)
    }

    @Test
    fun `calendar events support create, month query, date query, update, and validation`() = runTest {
        val date = LocalDate.of(2026, 10, 29)
        val event = planner.createEvent(
            title = "Study group",
            date = date,
            startTime = LocalTime.of(14, 0),
            endTime = LocalTime.of(15, 30),
            notes = "Library room 3B",
            linkedTaskId = null,
            sourceNoteId = null,
        ).ok()

        assertThat(planner.observeEventsForDate(date).first()).containsExactly(event)
        assertThat(planner.observeEventsForMonth(YearMonth.of(2026, 10)).first()).containsExactly(event)

        // Blank title is rejected
        val blankTitle = planner.createEvent("", date, null, null, "", null, null)
        assertThat(blankTitle).isInstanceOf(AppResult.Failure::class.java)

        // End time before start time is rejected
        val badTime = planner.createEvent(
            title = "Invalid slot",
            date = date,
            startTime = LocalTime.of(16, 0),
            endTime = LocalTime.of(15, 0),
            notes = "",
            linkedTaskId = null,
            sourceNoteId = null,
        )
        assertThat(badTime).isInstanceOf(AppResult.Failure::class.java)

        // Update and delete
        val edited = planner.updateEvent(
            id = event.id,
            title = "Calculus study group",
            date = date.plusDays(1),
            startTime = LocalTime.of(16, 0),
            endTime = LocalTime.of(17, 0),
            notes = "Bring past papers",
            linkedTaskId = null,
            sourceNoteId = null,
        ).ok()
        assertThat(edited.title).isEqualTo("Calculus study group")
        assertThat(planner.observeEventsForDate(date).first()).isEmpty()
        assertThat(planner.observeEventsForDate(date.plusDays(1)).first()).containsExactly(edited)

        planner.deleteEvent(event.id).ok()
        assertThat(planner.observeEventsForDate(date.plusDays(1)).first()).isEmpty()
    }
}
