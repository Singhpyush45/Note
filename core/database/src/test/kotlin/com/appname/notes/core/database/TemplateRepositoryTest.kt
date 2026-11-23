package com.appname.notes.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.id.UuidV7Generator
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.common.time.FixedClock
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.template.BuiltInTemplates
import com.appname.notes.core.ink.template.PaperStyle
import com.appname.notes.core.ink.template.Ruling
import com.appname.notes.core.ink.template.TemplateCategory
import com.appname.notes.core.ink.template.TemplateLayout
import com.appname.notes.core.ink.template.TextLabel
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Custom templates (owner brief §4) and notes made from templates. */
@RunWith(AndroidJUnit4::class)
class TemplateRepositoryTest {

    private val clock = FixedClock(Instant.parse("2026-10-25T10:00:00Z"))
    private val ids = UuidV7Generator(clock)
    private val db = NotesDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val templates = RoomTemplateRepository(db, clock, ids, NoOpLogger)
    private val ink = RoomInkRepository(db, clock, ids, NoOpLogger)
    private val notes = RoomNoteRepository(db, clock, ids, NoOpLogger)

    @After
    fun close() = db.close()

    private fun <T> AppResult<T>.ok(): T = (this as AppResult.Success).value

    private val pastelLines = TemplateLayout(
        paperColor = 0xFFFFF8E7.toInt(),
        paper = PaperStyle(Ruling.LINES, 26f, top = 120f),
        components = listOf(TextLabel(48f, 76f, "Ideas", size = 26f, bold = true)),
    )

    @Test
    fun `create, edit, rename, duplicate and delete a custom template`() = runTest {
        val id = templates.create("  Ideas page  ", pastelLines).ok()
        var mine = templates.observeCustom().first().single()
        assertThat(mine.name).isEqualTo("Ideas page")
        assertThat(mine.category).isEqualTo(TemplateCategory.CUSTOM)
        assertThat(mine.layout).isEqualTo(pastelLines)

        val wider = pastelLines.copy(paper = pastelLines.paper.copy(spacing = 34f))
        templates.update(id, "Ideas", wider).ok()
        templates.rename(id, "Brainstorm").ok()
        mine = templates.get(id)!!
        assertThat(mine.name).isEqualTo("Brainstorm")
        assertThat(mine.layout.paper.spacing).isEqualTo(34f)

        val copy = templates.duplicate(id).ok()
        assertThat(templates.observeCustom().first().map { it.name }).containsExactly("Brainstorm", "Brainstorm copy").inOrder()
        assertThat(templates.get(copy)!!.layout).isEqualTo(wider)

        templates.delete(id).ok()
        assertThat(templates.observeCustom().first().map { it.id }).containsExactly(copy)
        assertThat(templates.get(id)).isNull()
    }

    @Test
    fun `built-ins can be copied but not changed`() = runTest {
        val weekly = BuiltInTemplates.byId("planners.weekly")!!
        assertThat(templates.get(weekly.id)).isEqualTo(weekly)
        val copy = templates.duplicate(weekly.id).ok()
        assertThat(templates.get(copy)!!.layout).isEqualTo(weekly.layout)
        assertThat(templates.get(copy)!!.name).isEqualTo("Weekly planner copy")
        assertThat(templates.rename(weekly.id, "Mine")).isInstanceOf(AppResult.Failure::class.java)
        assertThat(templates.delete(weekly.id)).isInstanceOf(AppResult.Failure::class.java)
    }

    @Test
    fun `blank names are refused`() = runTest {
        assertThat(templates.create("   ", pastelLines)).isEqualTo(AppResult.Failure(DomainError.Validation("name", "blank")))
        val id = templates.create("Ok", pastelLines).ok()
        assertThat(templates.rename(id, "")).isInstanceOf(AppResult.Failure::class.java)
        assertThat(templates.get(id)!!.name).isEqualTo("Ok")
    }

    @Test
    fun `a note made from a template keeps its own copy of the design`() = runTest {
        val budget = BuiltInTemplates.byId("planners.monthly_plan")!! // landscape
        val noteId = ink.createInkNote(null, PageTemplate.BLANK, budget.layout).ok()
        var page = ink.loadPages(noteId).ok().single().info
        assertThat(page.layout).isEqualTo(budget.layout)
        assertThat(page.width).isEqualTo(1123f)
        assertThat(page.height).isEqualTo(794f)

        // New pages repeat the design; a plain background replaces it.
        val second = ink.addPage(noteId, 1, PageTemplate.BLANK, page.layout).ok()
        assertThat(second.layout).isEqualTo(budget.layout)
        ink.setTemplate(second.id, PageTemplate.GRID).ok()
        page = ink.loadPages(noteId).ok()[1].info
        assertThat(page.layout).isNull()
        assertThat(page.template).isEqualTo(PageTemplate.GRID)

        // Duplicating the note copies the design; deleting the custom template it came from changes nothing.
        val customId = templates.create("Mine", pastelLines).ok()
        val fromCustom = ink.createInkNote(null, PageTemplate.BLANK, templates.get(customId)!!.layout).ok()
        templates.delete(customId).ok()
        val copy = notes.duplicate(fromCustom).ok()
        assertThat(ink.loadPages(copy).ok().single().info.layout).isEqualTo(pastelLines)
        assertThat(ink.loadPages(fromCustom).ok().single().info.template).isEqualTo(PageTemplate.LINED)
    }

    @Test
    fun `a damaged design opens as a plain page`() = runTest {
        val noteId = ink.createInkNote(null, PageTemplate.LINED, pastelLines).ok()
        db.openHelper.writableDatabase.execSQL("UPDATE ink_pages SET layoutJson = '{broken'")
        val page = ink.loadPages(noteId).ok().single().info
        assertThat(page.layout).isNull()
        assertThat(page.template).isEqualTo(PageTemplate.LINED)
    }
}
