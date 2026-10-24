package com.appname.notes.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.id.UuidV7Generator
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.time.FixedClock
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.ReminderRepeat
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderRepositoryTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = FixedClock(LocalDateTime.parse("2026-10-22T10:00").atZone(zone).toInstant())
    private val ids = UuidV7Generator(clock)
    private val db = NotesDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val reminders = RoomReminderRepository(db, clock, ids, NoOpLogger)
    private val notes = RoomNoteRepository(db, clock, ids, NoOpLogger)

    @After
    fun close() = db.close()

    private fun <T> AppResult<T>.ok(): T = (this as AppResult.Success).value

    private fun local(text: String) = LocalDateTime.parse(text)
    private fun instant(text: String): Instant = local(text).atZone(zone).toInstant()

    private suspend fun note(title: String = "Groceries", vararg tasks: Pair<String, Boolean>): NoteId {
        val id = notes.createNote(null).ok()
        val blocks = tasks.mapIndexed { i, (text, checked) -> TextBlock("t$i", BlockKind.CHECKLIST, RichText(text), checked) }
        notes.saveContent(id, title, NoteDocument(blocks = blocks.ifEmpty { listOf(TextBlock("b1", text = RichText("Milk and eggs"))) })).ok()
        return id
    }

    @Test
    fun `a reminder goes off once, shows on the card, and stays listed as past`() = runTest {
        val id = note()
        val r = reminders.add(id, null, local("2026-10-22T18:00"), ReminderRepeat.NONE, zone).ok()
        assertThat(r.triggerAt).isEqualTo(instant("2026-10-22T18:00"))
        assertThat(reminders.observeNextDue().first()).isEqualTo(r.triggerAt)
        assertThat(notes.observeNotes(NoteCollection.All, NoteSort.MODIFIED).first().single().nextReminderAt).isEqualTo(r.triggerAt)

        // Not yet due.
        assertThat(reminders.takeDue(instant("2026-10-22T17:59"), zone).ok()).isEmpty()
        val due = reminders.takeDue(instant("2026-10-22T18:00"), zone).ok().single()
        assertThat(due.noteTitle).isEqualTo("Groceries")
        assertThat(due.notePreview).contains("Milk")
        assertThat(due.taskText).isNull()
        // Taken once only.
        assertThat(reminders.takeDue(instant("2026-10-22T18:01"), zone).ok()).isEmpty()
        assertThat(reminders.nextDue().ok()).isNull()
        val listed = reminders.observe(id).first().single()
        assertThat(listed.isPending).isFalse()
        assertThat(notes.observeNotes(NoteCollection.All, NoteSort.MODIFIED).first().single().nextReminderAt).isNull()
    }

    @Test
    fun `a repeating reminder moves to its next time and missed repeats are not replayed`() = runTest {
        val id = note()
        reminders.add(id, null, local("2026-10-22T09:00"), ReminderRepeat.DAILY, zone).ok()
        // Phone off for three days.
        val due = reminders.takeDue(instant("2026-10-25T14:00"), zone).ok()
        assertThat(due).hasSize(1)
        assertThat(due.single().dueAt).isEqualTo(instant("2026-10-22T09:00"))
        assertThat(reminders.nextDue().ok()).isEqualTo(instant("2026-10-26T09:00"))
        assertThat(reminders.observe(id).first().single().isPending).isTrue()
    }

    @Test
    fun `snooze brings a gone-off reminder back, and editing re-arms it`() = runTest {
        val id = note()
        val r = reminders.add(id, null, local("2026-10-22T11:00"), ReminderRepeat.NONE, zone).ok()
        reminders.takeDue(instant("2026-10-22T11:00"), zone).ok()
        reminders.snooze(r.id, instant("2026-10-22T11:10")).ok()
        assertThat(reminders.nextDue().ok()).isEqualTo(instant("2026-10-22T11:10"))
        assertThat(reminders.takeDue(instant("2026-10-22T11:10"), zone).ok()).hasSize(1)

        val edited = reminders.update(r.id, local("2026-10-23T09:00"), ReminderRepeat.WEEKLY, zone).ok()
        assertThat(edited.isPending).isTrue()
        assertThat(edited.repeat).isEqualTo(ReminderRepeat.WEEKLY)
        assertThat(reminders.nextDue().ok()).isEqualTo(instant("2026-10-23T09:00"))
    }

    @Test
    fun `trashed notes don't remind, restoring brings the reminder back, deleting removes it`() = runTest {
        val id = note()
        reminders.add(id, null, local("2026-10-22T12:00"), ReminderRepeat.NONE, zone).ok()
        notes.moveToTrash(setOf(id)).ok()
        assertThat(reminders.nextDue().ok()).isNull()
        assertThat(reminders.takeDue(instant("2026-10-22T12:30"), zone).ok()).isEmpty()
        // No new reminders on a trashed note.
        assertThat(reminders.add(id, null, local("2026-10-23T09:00"), ReminderRepeat.NONE, zone)).isInstanceOf(AppResult.Failure::class.java)

        notes.restoreFromTrash(setOf(id)).ok()
        assertThat(reminders.takeDue(instant("2026-10-22T12:31"), zone).ok()).hasSize(1) // missed while trashed: goes off now

        reminders.add(id, null, local("2026-10-24T09:00"), ReminderRepeat.NONE, zone).ok()
        notes.moveToTrash(setOf(id)).ok()
        notes.deletePermanently(setOf(id)).ok()
        assertThat(reminders.observe(id).first()).isEmpty()
    }

    @Test
    fun `task reminders carry the item's text and a ticked task is not reminded`() = runTest {
        val id = note("Trip", "Book tickets" to false, "Pack charger" to true)
        reminders.add(id, "t0", local("2026-10-22T19:00"), ReminderRepeat.NONE, zone).ok()
        reminders.add(id, "t1", local("2026-10-22T19:00"), ReminderRepeat.NONE, zone).ok()
        reminders.add(id, "t1", local("2026-10-22T19:00"), ReminderRepeat.DAILY, zone).ok()
        reminders.add(id, "gone", local("2026-10-22T19:00"), ReminderRepeat.NONE, zone).ok()

        val due = reminders.takeDue(instant("2026-10-22T19:00"), zone).ok()
        // t0 → its text; t1 one-off → skipped (ticked); t1 daily → still reminds; deleted item → the note.
        assertThat(due.map { it.taskText }).containsExactly("Book tickets", "Pack charger", null)
        // The skipped one is settled too, not taken again.
        assertThat(reminders.takeDue(instant("2026-10-22T19:05"), zone).ok()).isEmpty()
    }

    @Test
    fun `remove and undo restore the same reminder`() = runTest {
        val id = note()
        val r = reminders.add(id, null, local("2026-10-22T20:00"), ReminderRepeat.MONTHLY, zone).ok()
        val removed = reminders.remove(r.id).ok()
        assertThat(reminders.observe(id).first()).isEmpty()
        reminders.restore(removed).ok()
        assertThat(reminders.observe(id).first().single()).isEqualTo(r)
        reminders.remove(r.id).ok()
        assertThat(reminders.remove(r.id)).isInstanceOf(AppResult.Failure::class.java)
    }

    @Test
    fun `an otherwise empty note with a reminder is kept when left`() = runTest {
        val id = notes.createNote(null).ok()
        reminders.add(id, null, local("2026-10-23T09:00"), ReminderRepeat.NONE, zone).ok()
        assertThat(notes.discardIfBlank(id)).isFalse()
        assertThat(reminders.observe(id).first()).hasSize(1)
    }

    @Test
    fun `duplicating a note doesn't copy its reminders`() = runTest {
        val id = note()
        reminders.add(id, null, local("2026-10-22T20:00"), ReminderRepeat.NONE, zone).ok()
        val copy = notes.duplicate(id).ok()
        assertThat(reminders.observe(copy).first()).isEmpty()
    }
}
