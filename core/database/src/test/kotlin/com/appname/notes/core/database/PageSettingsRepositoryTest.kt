package com.appname.notes.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.id.UuidV7Generator
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.time.FixedClock
import com.appname.notes.core.ink.edit.InkEdit
import com.appname.notes.core.ink.layout.PageArrangement
import com.appname.notes.core.ink.layout.PageMode
import com.appname.notes.core.ink.layout.ScrollAxis
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import com.appname.notes.core.ink.template.BuiltInTemplates
import com.appname.notes.core.ink.template.PageFormat
import com.appname.notes.core.ink.template.PaperStyle
import com.appname.notes.core.ink.template.Ruling
import com.appname.notes.core.ink.template.TemplateLayout
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Page settings per note (owner brief §5): arrangement, page size and page design are stored and survive copies. */
@RunWith(AndroidJUnit4::class)
class PageSettingsRepositoryTest {

    private val clock = FixedClock(Instant.parse("2026-10-25T10:00:00Z"))
    private val ids = UuidV7Generator(clock)
    private val db = NotesDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val ink = RoomInkRepository(db, clock, ids, NoOpLogger)
    private val notes = RoomNoteRepository(db, clock, ids, NoOpLogger)

    @After
    fun close() = db.close()

    private fun <T> AppResult<T>.ok(): T = (this as AppResult.Success).value

    private val infiniteAcross = PageArrangement(PageMode.INFINITE, ScrollAxis.HORIZONTAL)

    @Test
    fun `arrangement is stored per note and copied with it`() = runTest {
        val plain = ink.createInkNote(null, PageTemplate.LINED).ok()
        assertThat(ink.arrangement(plain).ok()).isEqualTo(PageArrangement.DEFAULT)

        val made = ink.createInkNote(null, PageTemplate.LINED, arrangement = infiniteAcross).ok()
        assertThat(ink.arrangement(made).ok()).isEqualTo(infiniteAcross)

        ink.setArrangement(plain, PageArrangement(PageMode.INFINITE)).ok()
        assertThat(ink.arrangement(plain).ok()).isEqualTo(PageArrangement(PageMode.INFINITE))

        val copy = notes.duplicate(made).ok()
        assertThat(ink.arrangement(copy).ok()).isEqualTo(infiniteAcross)
    }

    @Test
    fun `resizing never hides ink and stretches the design`() = runTest {
        val weekly = BuiltInTemplates.byId("planners.weekly")!!.layout
        val id = ink.createInkNote(null, PageTemplate.BLANK, weekly).ok()
        val page = ink.loadPages(id).ok().single().info
        // A stroke near the bottom of the A4 page (y = 1100).
        val points = StrokePoints(floatArrayOf(100f, 200f), floatArrayOf(1100f, 1100f), intArrayOf(0, 8))
        ink.commit(page.id, InkEdit.add(StrokeItem("s1", 1, InkBrush(InkTool.PEN, -16777216, 2f), points))).ok()

        val landscape = PageFormat.A4_LANDSCAPE
        val resized = ink.resizePages(id, landscape.width, landscape.height).ok().single()
        assertThat(resized.width).isEqualTo(1123f)
        assertThat(resized.height).isAtLeast(1101f) // grew to keep the stroke visible
        assertThat(resized.layout).isEqualTo(weekly.resized(resized.width, resized.height))
        assertThat(ink.loadPages(id).ok().single().items).hasSize(1) // the ink is untouched
    }

    @Test
    fun `page design and paper colour are saved per page`() = runTest {
        val id = ink.createInkNote(null, PageTemplate.BLANK).ok()
        val second = ink.addPage(id, 1, PageTemplate.BLANK).ok()
        val grid = TemplateLayout(paperColor = 0xFFFFF8E7.toInt(), paper = PaperStyle(Ruling.GRID, 16f))
        ink.setPageLayouts(id, mapOf(second.id to grid)).ok()
        val pages = ink.loadPages(id).ok()
        assertThat(pages[0].info.layout).isNull()
        assertThat(pages[1].info.layout).isEqualTo(grid)
        assertThat(pages[1].info.template).isEqualTo(PageTemplate.GRID)
    }

    @Test
    fun `settings of a deleted note report it`() = runTest {
        val id = ink.createInkNote(null, PageTemplate.BLANK).ok()
        notes.deletePermanently(setOf(id)).ok()
        assertThat(ink.setArrangement(id, infiniteAcross)).isInstanceOf(AppResult.Failure::class.java)
    }
}
