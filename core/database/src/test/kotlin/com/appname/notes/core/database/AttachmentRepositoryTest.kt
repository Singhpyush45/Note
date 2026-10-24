package com.appname.notes.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.id.UuidV7Generator
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.time.FixedClock
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.core.domain.model.NewAttachment
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.ink.model.PageTemplate
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentRepositoryTest {

    private val clock = FixedClock(Instant.parse("2026-10-22T10:00:00Z"))
    private val ids = UuidV7Generator(clock)
    private val db = NotesDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val attachments = RoomAttachmentRepository(db, clock, ids, NoOpLogger)
    private val notes = RoomNoteRepository(db, clock, ids, NoOpLogger)
    private val ink = RoomInkRepository(db, clock, ids, NoOpLogger)

    @After
    fun close() = db.close()

    private fun <T> AppResult<T>.ok(): T = (this as AppResult.Success).value

    private fun image(name: String) = NewAttachment(AttachmentKind.IMAGE, "$name.jpg", "$name.jpg", "image/jpeg", 1000, 400, 300)
    private fun file(name: String) = NewAttachment(AttachmentKind.FILE, "$name.bin", "$name.zip", "application/zip", 5000)

    private suspend fun listed() = notes.observeNotes(NoteCollection.All, NoteSort.MODIFIED).first()

    @Test
    fun `attachments keep their order and show on the library card`() = runTest {
        val id = notes.createNote(null).ok()
        attachments.add(id, listOf(file("a"), image("b"))).ok()
        attachments.add(id, listOf(image("c"))).ok()
        assertThat(attachments.observe(id).first().map { it.displayName }).containsExactly("a.zip", "b.jpg", "c.jpg").inOrder()
        assertThat(attachments.list(id).ok().map { it.position }).containsExactly(0, 1, 2).inOrder()
        val card = listed().single()
        assertThat(card.attachmentCount).isEqualTo(3)
        assertThat(card.coverImage).isEqualTo("b.jpg") // first image, not the first file
    }

    @Test
    fun `remove closes the gap and undo puts it back in place`() = runTest {
        val id = notes.createNote(null).ok()
        attachments.add(id, listOf(image("a"), image("b"), image("c"))).ok()
        val middle = attachments.list(id).ok()[1]
        val removed = attachments.remove(middle.id).ok()
        assertThat(attachments.list(id).ok().map { it.displayName to it.position }).containsExactly("a.jpg" to 0, "c.jpg" to 1).inOrder()
        attachments.restore(removed).ok()
        assertThat(attachments.list(id).ok()[1]).isEqualTo(middle) // same id, position and metadata
        assertThat(attachments.list(id).ok().map { it.displayName }).containsExactly("a.jpg", "b.jpg", "c.jpg").inOrder()
        // Removing twice is reported, not a crash.
        attachments.remove(middle.id).ok()
        assertThat(attachments.remove(middle.id)).isInstanceOf(AppResult.Failure::class.java)
    }

    @Test
    fun `a note with only attachments is not discarded as blank`() = runTest {
        val id = notes.createNote(null).ok()
        attachments.add(id, listOf(image("a"))).ok()
        assertThat(notes.discardIfBlank(id)).isFalse()
        val empty = notes.createNote(null).ok()
        assertThat(notes.discardIfBlank(empty)).isTrue()
    }

    @Test
    fun `duplicates share files, permanent delete removes rows, files stay referenced until the last one goes`() = runTest {
        val id = notes.createNote(null).ok()
        attachments.add(id, listOf(image("a"))).ok()
        val copy = notes.duplicate(id).ok()
        assertThat(attachments.list(copy).ok().single().fileName).isEqualTo("a.jpg")
        assertThat(attachments.list(copy).ok().single().id).isNotEqualTo(attachments.list(id).ok().single().id)

        notes.deletePermanently(setOf(id)).ok()
        assertThat(attachments.list(id).ok()).isEmpty()
        assertThat(attachments.referencedFiles().ok()).containsExactly("a.jpg")
        notes.deletePermanently(setOf(copy)).ok()
        assertThat(attachments.referencedFiles().ok()).isEmpty()
    }

    @Test
    fun `trashed notes keep their files referenced, so restoring them works`() = runTest {
        val id = notes.createNote(null).ok()
        attachments.add(id, listOf(image("a"))).ok()
        notes.moveToTrash(setOf(id)).ok()
        assertThat(attachments.referencedFiles().ok()).containsExactly("a.jpg")
    }

    @Test
    fun `shared photos become a new note in one step`() = runTest {
        val id = attachments.createNoteWith(null, "  ", listOf(image("x"), image("y"))).ok()
        assertThat(attachments.list(id).ok().map { it.displayName }).containsExactly("x.jpg", "y.jpg").inOrder()
        assertThat(notes.getNote(id)!!.title).isEmpty()
        assertThat(listed().single().coverImage).isEqualTo("x.jpg")
    }

    @Test
    fun `handwritten notes can have attachments too and copies keep them`() = runTest {
        val id = ink.createInkNote(null, PageTemplate.BLANK).ok()
        attachments.add(id, listOf(file("f"))).ok()
        val copy = notes.duplicate(id).ok()
        assertThat(attachments.list(copy).ok().map { it.displayName }).containsExactly("f.zip")
    }

    @Test
    fun `attaching to a note that is gone fails cleanly`() = runTest {
        assertThat(attachments.add(NoteId("missing"), listOf(image("a")))).isInstanceOf(AppResult.Failure::class.java)
        assertThat(attachments.referencedFiles().ok()).isEmpty()
    }

    @Test
    fun `unknown kinds from a newer version are read as plain files`() {
        assertThat(AttachmentKind.fromName("HOLOGRAM")).isEqualTo(AttachmentKind.FILE)
    }
}
