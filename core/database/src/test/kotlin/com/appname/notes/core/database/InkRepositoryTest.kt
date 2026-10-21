package com.appname.notes.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.id.UuidV7Generator
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.common.time.FixedClock
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.repository.InkPageContent
import com.appname.notes.core.ink.codec.InkCodec
import com.appname.notes.core.ink.edit.Eraser
import com.appname.notes.core.ink.edit.InkEdit
import com.appname.notes.core.ink.edit.Lasso
import com.appname.notes.core.ink.edit.PageItems
import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.model.Pt
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.util.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkRepositoryTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T10:00:00Z"))
    private val ids = UuidV7Generator(clock)
    private val db = NotesDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val ink = RoomInkRepository(db, clock, ids, NoOpLogger)
    private val notes = RoomNoteRepository(db, clock, ids, NoOpLogger)
    private val pen = InkBrush(InkTool.PEN, -16777216, 2f)

    @After
    fun close() = db.close()

    private fun <T> AppResult<T>.ok(): T = (this as AppResult.Success).value

    private suspend fun pages(note: NoteId): List<InkPageContent> = ink.loadPages(note).ok()

    private fun stroke(rng: Random, z: Long): StrokeItem {
        val n = 2 + rng.nextInt(80)
        val b = StrokePoints.Builder(hasPressure = true, hasTilt = rng.nextBoolean(), hasOrientation = rng.nextBoolean())
        var x = rng.nextFloat() * 700
        var y = rng.nextFloat() * 1000
        repeat(n) { i ->
            x += rng.nextFloat() * 8 - 4; y += rng.nextFloat() * 8 - 4
            b.add(x, y, i * 8, rng.nextFloat(), rng.nextFloat(), rng.nextFloat() * 6f)
        }
        return InkCodec.quantize(StrokeItem(ids.newId(), z, pen.copy(size = 1f + rng.nextInt(8)), b.build())) as StrokeItem
    }

    private fun shape(z: Long) = ShapeItem(
        ids.newId(), z, pen, ShapeKind.CIRCLE, ShapeGeometry.Ellipse(Pt(100f, 100f), 40f, 40f, 0f), null,
    )

    @Test
    fun `new handwritten note has one blank page and shows in the list`() = runTest {
        val id = ink.createInkNote(null, PageTemplate.GRID).ok()
        val page = pages(id).single()
        assertThat(page.info.template).isEqualTo(PageTemplate.GRID)
        assertThat(page.info.index).isEqualTo(0)
        assertThat(page.items).isEmpty()
        assertThat(page.readOnly).isFalse()
        val listed = notes.observeNotes(NoteCollection.All, NoteSort.MODIFIED).first().single()
        assertThat(listed.type).isEqualTo(NoteType.INK)
    }

    @Test
    fun `random edits saved one by one always load back exactly`() = runTest {
        val rng = Random(5)
        val id = ink.createInkNote(null, PageTemplate.BLANK).ok()
        val pageId = pages(id).single().info.id
        var memory = PageItems.EMPTY
        repeat(120) { step ->
            val items = memory.items
            val edit = when {
                items.isEmpty() || rng.nextInt(3) == 0 -> InkEdit.add(if (rng.nextInt(5) == 0) shape(memory.nextZ) else stroke(rng, memory.nextZ))
                rng.nextInt(4) == 0 -> InkEdit.remove(listOf(items[rng.nextInt(items.size)]))
                rng.nextInt(3) == 0 -> Lasso.move(items.take(1 + rng.nextInt(items.size)), 4.0, -2.0).let { e ->
                    // Moving creates unquantised floats: quantise like the editor does before saving.
                    InkEdit(e.removed, e.added.map { InkCodec.quantize(it) })
                }
                else -> {
                    val target = items[rng.nextInt(items.size)]
                    val at = if (target is StrokeItem) target.points.point(target.points.size / 2) else Vec(140.0, 100.0)
                    val e = Eraser.erasePartially(items, listOf(at), 4.0, ids::newId) ?: InkEdit.add(stroke(rng, memory.nextZ))
                    InkEdit(e.removed, e.added.map { InkCodec.quantize(it) })
                }
            }
            memory = memory.apply(edit)!!
            clock.advanceMillis(10)
            assertThat(ink.commit(pageId, edit)).isEqualTo(AppResult.Success(Unit))
            if (step % 10 == 0 || step == 119) assertThat(pages(id).single().items).isEqualTo(memory.items)
        }
        // The note's modified time follows the ink.
        assertThat(notes.getNote(id)!!.updatedAt).isEqualTo(clock.now())
    }

    @Test
    fun `pages can be inserted anywhere, retemplated and deleted except the last`() = runTest {
        val id = ink.createInkNote(null, PageTemplate.BLANK).ok()
        val first = pages(id).single().info
        val end = ink.addPage(id, index = 99, template = PageTemplate.LINED).ok()
        val front = ink.addPage(id, index = 0, template = PageTemplate.DOTTED).ok()
        assertThat(pages(id).map { it.info.id }).containsExactly(front.id, first.id, end.id).inOrder()
        assertThat(pages(id).map { it.info.index }).containsExactly(0, 1, 2).inOrder()

        assertThat(ink.setTemplate(first.id, PageTemplate.GRID)).isEqualTo(AppResult.Success(Unit))
        assertThat(pages(id)[1].info.template).isEqualTo(PageTemplate.GRID)

        ink.commit(end.id, InkEdit.add(shape(1))).ok()
        ink.deletePage(end.id).ok()
        ink.deletePage(front.id).ok()
        assertThat(pages(id).map { it.info.id to it.info.index }).containsExactly(first.id to 0)
        assertThat(ink.deletePage(first.id)).isInstanceOf(AppResult.Failure::class.java)
        assertThat(ink.setTemplate("missing", PageTemplate.GRID)).isEqualTo(AppResult.Failure(DomainError.NotFound("Page", "missing")))
    }

    @Test
    fun `items this version can't read make the page read-only and are kept`() = runTest {
        val id = ink.createInkNote(null, PageTemplate.BLANK).ok()
        val pageId = pages(id).single().info.id
        val good = stroke(Random(1), 1)
        ink.commit(pageId, InkEdit.add(good)).ok()
        val newer = InkCodec.encode(good).also { it[0] = 9 }
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO ink_items (id, pageId, z, data) VALUES (?, ?, ?, ?)", arrayOf<Any>("future", pageId, 2L, newer))
        sql.execSQL("INSERT INTO ink_items (id, pageId, z, data) VALUES (?, ?, ?, ?)", arrayOf<Any>("broken", pageId, 3L, byteArrayOf(1, 1)))
        val page = pages(id).single()
        assertThat(page.items).containsExactly(good)
        assertThat(page.unreadableItems).isEqualTo(2)
        assertThat(page.readOnly).isTrue()

        // Duplicating copies even the unreadable bytes, untouched.
        val copy = notes.duplicate(id).ok()
        val copied = pages(copy).single()
        assertThat(copied.unreadableItems).isEqualTo(2)
        assertThat(copied.items.map { (it as StrokeItem).points }).containsExactly(good.points)
        assertThat(copied.items.single().id).isNotEqualTo(good.id)
        assertThat(copied.info.id).isNotEqualTo(pageId)
    }

    @Test
    fun `only a handwritten note without any ink is discarded as blank`() = runTest {
        val empty = ink.createInkNote(null, PageTemplate.BLANK).ok()
        assertThat(notes.discardIfBlank(empty)).isTrue()
        assertThat(notes.getNote(empty)).isNull()
        assertThat(countRows("ink_pages")).isEqualTo(0)

        val drawn = ink.createInkNote(null, PageTemplate.BLANK).ok()
        ink.commit(pages(drawn).single().info.id, InkEdit.add(shape(1))).ok()
        assertThat(notes.discardIfBlank(drawn)).isFalse()
        assertThat(notes.getNote(drawn)).isNotNull()
    }

    @Test
    fun `permanent delete removes all handwriting and later saves are refused`() = runTest {
        val id = ink.createInkNote(null, PageTemplate.BLANK).ok()
        val pageId = pages(id).single().info.id
        ink.addPage(id, 1, PageTemplate.BLANK).ok()
        ink.commit(pageId, InkEdit.add(stroke(Random(2), 1), shape(2))).ok()
        assertThat(countRows("ink_items")).isEqualTo(2)

        notes.moveToTrash(setOf(id)).ok()
        notes.deletePermanently(setOf(id)).ok()
        assertThat(countRows("ink_items")).isEqualTo(0)
        assertThat(countRows("ink_pages")).isEqualTo(0)
        // E.g. an editor still open on another screen: its save must not resurrect ink.
        assertThat(ink.commit(pageId, InkEdit.add(shape(3)))).isInstanceOf(AppResult.Failure::class.java)
        assertThat(countRows("ink_items")).isEqualTo(0)
    }

    @Test
    fun `big erase or lasso edits with many items save in one go`() = runTest {
        val id = ink.createInkNote(null, PageTemplate.BLANK).ok()
        val pageId = pages(id).single().info.id
        val rng = Random(3)
        val many: List<InkItem> = List(1200) { stroke(rng, it.toLong() + 1) }
        ink.commit(pageId, InkEdit(emptyList(), many)).ok()
        assertThat(pages(id).single().items).hasSize(1200)
        ink.commit(pageId, InkEdit.remove(many)).ok()
        assertThat(pages(id).single().items).isEmpty()
    }

    @Test
    fun `renaming a handwritten note changes only its title`() = runTest {
        val id = ink.createInkNote(null, PageTemplate.BLANK).ok()
        ink.commit(pages(id).single().info.id, InkEdit.add(shape(1))).ok()
        clock.advanceMillis(1_000)
        notes.setTitle(id, "  Lecture 4  ").ok()
        val note = notes.getNote(id)!!
        assertThat(note.title).isEqualTo("Lecture 4")
        assertThat(note.updatedAt).isEqualTo(clock.now())
        assertThat(pages(id).single().items).hasSize(1)
        // Same title again: no change, modified time stays.
        clock.advanceMillis(1_000)
        notes.setTitle(id, "Lecture 4").ok()
        assertThat(notes.getNote(id)!!.updatedAt).isEqualTo(clock.now().minusMillis(1_000))
        assertThat(notes.setTitle(NoteId("missing"), "x")).isInstanceOf(AppResult.Failure::class.java)
        // Title search works for handwritten notes.
        assertThat(notes.search("lecture", com.appname.notes.core.domain.model.SearchFilters()).first().map { it.note.id }).containsExactly(id)
    }

    private fun countRows(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
}
