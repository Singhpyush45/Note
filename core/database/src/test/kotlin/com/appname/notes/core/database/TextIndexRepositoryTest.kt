package com.appname.notes.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.id.UuidV7Generator
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.time.FixedClock
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.core.domain.model.ExtractionMethod
import com.appname.notes.core.domain.model.IndexStatus
import com.appname.notes.core.domain.model.NewAttachment
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.PageText
import com.appname.notes.core.domain.model.SearchFilters
import com.appname.notes.core.domain.model.SearchHit
import com.appname.notes.core.domain.model.TextSource
import com.appname.notes.core.domain.repository.PdfDocumentInfo
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextIndexRepositoryTest {

    private val clock = FixedClock(Instant.parse("2026-10-24T10:00:00Z"))
    private val ids = UuidV7Generator(clock)
    private val db = NotesDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val index = RoomTextIndexRepository(db, clock, NoOpLogger)
    private val notes = RoomNoteRepository(db, clock, ids, NoOpLogger)
    private val attachments = RoomAttachmentRepository(db, clock, ids, NoOpLogger)
    private val pdf = RoomPdfRepository(db, clock, ids, NoOpLogger)

    @After
    fun close() = db.close()

    private fun <T> AppResult<T>.ok(): T = (this as AppResult.Success).value

    private val later = Instant.parse("2026-10-25T00:00:00Z")

    private suspend fun pending() = index.pending(50, later).ok()

    private suspend fun search(q: String): List<SearchHit> = notes.search(q, SearchFilters()).first()

    private suspend fun pdfNote(title: String = "Biology"): NoteId =
        pdf.createPdfNote(null, title, PdfDocumentInfo("$title.pdf", "$title.pdf", "ab", 10, 2), listOf(794f to 1123f, 794f to 1123f)).ok()

    private suspend fun noteWith(vararg items: NewAttachment): NoteId {
        val id = notes.createNote(null).ok()
        attachments.add(id, items.toList()).ok()
        return id
    }

    private fun photo(name: String) = NewAttachment(AttachmentKind.IMAGE, "$name.jpg", "$name.jpg", "image/jpeg", 10, 100, 100)

    @Test
    fun `PDF notes and attached photos and PDFs are pending, other files are not`() = runTest {
        val pdfId = pdfNote()
        val noteId = noteWith(photo("board"), NewAttachment(AttachmentKind.FILE, "z.zip", "z.zip", "application/zip", 5))
        val keys = pending().map { it.key }
        assertThat(keys).contains("p:${pdfId.value}")
        assertThat(pending().filterIsInstance<TextSource.AttachmentSource>().map { it.fileName }).containsExactly("board.jpg")
        assertThat(pending().all { it.noteId == pdfId || it.noteId == noteId }).isTrue()
    }

    @Test
    fun `saved text is searchable with its page, and the source is no longer pending`() = runTest {
        val id = pdfNote()
        val source = pending().single()
        index.save(source, listOf(PageText(0, "cell membrane", ExtractionMethod.PDF_TEXT), PageText(1, "Mitochondria produce ATP", ExtractionMethod.OCR)), IndexStatus.DONE).ok()
        assertThat(pending()).isEmpty()
        val hit = search("mitochondria").single()
        assertThat(hit.note.id).isEqualTo(id)
        assertThat(hit.location!!.page).isEqualTo(1)
        assertThat(hit.location!!.attachmentName).isNull()
        assertThat(hit.snippet).contains("${SearchHit.HIGHLIGHT_START}Mitochondria${SearchHit.HIGHLIGHT_END}")
        assertThat(index.progress().ok()).isEqualTo(1 to 0)
    }

    @Test
    fun `a photo's text names the photo and has no page number`() = runTest {
        noteWith(photo("board"))
        index.save(pending().single(), listOf(PageText(0, "Exam on Friday", ExtractionMethod.OCR)), IndexStatus.DONE).ok()
        val hit = search("friday").single()
        assertThat(hit.location!!.attachmentName).isEqualTo("board.jpg")
        assertThat(hit.location!!.page).isNull()
    }

    @Test
    fun `Hindi text in Devanagari is found by a Hindi word`() = runTest {
        noteWith(photo("hindi"))
        index.save(pending().single(), listOf(PageText(0, "प्रकाश संश्लेषण पौधों में होता है", ExtractionMethod.OCR)), IndexStatus.DONE).ok()
        assertThat(search("संश्लेषण")).hasSize(1)
        assertThat(search("पौधों")).hasSize(1)
    }

    @Test
    fun `failures are retried later, at most a few times`() = runTest {
        noteWith(photo("a"))
        val source = pending().single()
        repeat(3) { attempt ->
            index.save(source, emptyList(), IndexStatus.FAILED).ok()
            // Not again right away (retryBefore = before the failure)...
            assertThat(index.pending(50, clock.now()).ok()).isEmpty()
            // ...but later, until the attempts are used up.
            assertThat(pending().size).isEqualTo(if (attempt < 2) 1 else 0)
        }
    }

    @Test
    fun `removing the attachment or deleting the note removes its text`() = runTest {
        val id = noteWith(photo("a"), photo("b"))
        pending().forEach { index.save(it, listOf(PageText(0, "quantum ${it.key}", ExtractionMethod.OCR)), IndexStatus.DONE).ok() }
        assertThat(search("quantum")).hasSize(2)

        val first = attachments.list(id).ok().first()
        attachments.remove(first.id).ok()
        assertThat(search("quantum")).hasSize(1)

        notes.deletePermanently(setOf(id)).ok()
        assertThat(search("quantum")).isEmpty()
        assertThat(pending()).isEmpty()
    }

    @Test
    fun `trashed notes are not found, restored ones are`() = runTest {
        val id = pdfNote()
        index.save(pending().single(), listOf(PageText(0, "osmosis", ExtractionMethod.PDF_TEXT)), IndexStatus.DONE).ok()
        notes.moveToTrash(setOf(id)).ok()
        assertThat(search("osmosis")).isEmpty()
        notes.restoreFromTrash(setOf(id)).ok()
        assertThat(search("osmosis")).hasSize(1)
    }

    @Test
    fun `a result for an attachment removed meanwhile is dropped, not resurrected`() = runTest {
        val id = noteWith(photo("a"))
        val source = pending().single()
        attachments.remove(attachments.list(id).ok().single().id).ok()
        index.save(source, listOf(PageText(0, "ghost", ExtractionMethod.OCR)), IndexStatus.DONE).ok()
        assertThat(search("ghost")).isEmpty()
    }

    @Test
    fun `note text matches come first, then document matches`() = runTest {
        val textNote = notes.createNote(null).ok()
        notes.setTitle(textNote, "Photosynthesis summary").ok()
        val pdfId = pdfNote("Plants")
        index.save(pending().single { it.noteId == pdfId }, listOf(PageText(0, "photosynthesis in leaves", ExtractionMethod.PDF_TEXT)), IndexStatus.DONE).ok()
        val hits = search("photosynthesis")
        assertThat(hits.map { it.note.id }).containsExactly(textNote, pdfId).inOrder()
        assertThat(hits[0].location).isNull()
        assertThat(hits[1].location).isNotNull()
    }

    @Test
    fun `clear forgets everything and makes all sources pending again`() = runTest {
        pdfNote()
        index.save(pending().single(), listOf(PageText(0, "enzyme", ExtractionMethod.PDF_TEXT)), IndexStatus.DONE).ok()
        index.clear().ok()
        assertThat(search("enzyme")).isEmpty()
        assertThat(pending()).hasSize(1)
    }
}
