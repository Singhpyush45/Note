package com.appname.notes.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.id.UuidV7Generator
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.time.FixedClock
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.model.SearchFilters
import com.appname.notes.core.domain.repository.PdfDocumentInfo
import com.appname.notes.core.ink.edit.InkEdit
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.Pt
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfRepositoryTest {

    private val clock = FixedClock(Instant.parse("2026-10-22T10:00:00Z"))
    private val ids = UuidV7Generator(clock)
    private val db = NotesDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val pdf = RoomPdfRepository(db, clock, ids, NoOpLogger)
    private val ink = RoomInkRepository(db, clock, ids, NoOpLogger)
    private val notes = RoomNoteRepository(db, clock, ids, NoOpLogger)

    private val doc = PdfDocumentInfo("f1.pdf", "Lecture 5.pdf", "ab12", 12_345, 3)
    private val sizes = listOf(793.3f to 1122.7f, 1122.7f to 793.3f, 793.3f to 1122.7f)

    @After
    fun close() = db.close()

    private fun <T> AppResult<T>.ok(): T = (this as AppResult.Success).value

    private suspend fun create() = pdf.createPdfNote(null, "Lecture 5", doc, sizes).ok()

    @Test
    fun `a PDF note gets one page per PDF page, sized as displayed, and is searchable by title`() = runTest {
        val id = create()
        val pages = ink.loadPages(id).ok()
        assertThat(pages.map { it.info.pdfPageIndex }).containsExactly(0, 1, 2).inOrder()
        assertThat(pages.map { it.info.width to it.info.height }).isEqualTo(sizes)
        assertThat(pdf.document(id).ok()).isEqualTo(doc)
        val listed = notes.observeNotes(NoteCollection.All, NoteSort.MODIFIED).first().single()
        assertThat(listed.type).isEqualTo(NoteType.PDF)
        assertThat(notes.search("lecture", SearchFilters()).first().map { it.note.id }).containsExactly(id)
        // An imported document is never discarded as "blank".
        assertThat(notes.discardIfBlank(id)).isFalse()
    }

    @Test
    fun `page sizes must match the page count`() = runTest {
        assertThat(pdf.createPdfNote(null, "x", doc, sizes.take(2))).isInstanceOf(AppResult.Failure::class.java)
        assertThat(notes.observeNotes(NoteCollection.All, NoteSort.MODIFIED).first()).isEmpty()
    }

    @Test
    fun `duplicates share the file, which stays referenced until the last note is deleted`() = runTest {
        val id = create()
        val page = ink.loadPages(id).ok()[1].info.id
        val circle = ShapeItem("c", 1, InkBrush(InkTool.PEN, -16777216, 2f), ShapeKind.CIRCLE, ShapeGeometry.Ellipse(Pt(50f, 50f), 20f, 20f, 0f), null)
        ink.commit(page, InkEdit.add(circle)).ok()

        val copy = notes.duplicate(id).ok()
        assertThat(pdf.document(copy).ok()!!.fileName).isEqualTo("f1.pdf")
        val copied = ink.loadPages(copy).ok()
        assertThat(copied.map { it.info.pdfPageIndex }).containsExactly(0, 1, 2).inOrder()
        assertThat(copied[1].items.single().let { (it as ShapeItem).geometry }).isEqualTo(circle.geometry)

        assertThat(pdf.referencedFiles().ok()).containsExactly("f1.pdf")
        notes.deletePermanently(setOf(id)).ok()
        assertThat(pdf.referencedFiles().ok()).containsExactly("f1.pdf") // the copy still uses it
        assertThat(pdf.document(id).ok()).isNull()
        notes.deletePermanently(setOf(copy)).ok()
        assertThat(pdf.referencedFiles().ok()).isEmpty() // now the app may delete the file
        assertThat(ink.loadPages(NoteId(copy.value)).ok()).isEmpty()
    }
}
