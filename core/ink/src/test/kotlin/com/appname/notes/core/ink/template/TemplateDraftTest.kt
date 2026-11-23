package com.appname.notes.core.ink.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateDraftTest {

    @Test
    fun `opening any built-in in the editor and saving changes nothing`() {
        for (t in BuiltInTemplates.all) {
            assertEquals(t.name, t.layout, TemplateDraft.from(t.layout).toLayout())
        }
    }

    @Test
    fun `a header is recognised again after saving`() {
        for (style in HeaderStyle.entries) {
            val layout = TemplateDraft(header = style, ruling = Ruling.GRID, spacing = 20f).toLayout()
            val again = TemplateDraft.from(layout)
            assertEquals(style, again.header)
            assertTrue(again.extras.isEmpty())
            assertEquals(layout, again.toLayout())
        }
    }

    @Test
    fun `ruling starts below the header`() {
        val plain = TemplateDraft(ruling = Ruling.LINES, spacing = 30f).toLayout()
        assertEquals(90f, plain.paper.top)
        val headed = TemplateDraft(ruling = Ruling.LINES, spacing = 30f, header = HeaderStyle.TITLE).toLayout()
        assertEquals(120f, headed.paper.top)
    }

    @Test
    fun `the margin line only exists on lined paper`() {
        assertNull(TemplateDraft(ruling = Ruling.GRID, margin = true).toLayout().paper.marginX)
        assertEquals(64f, TemplateDraft(ruling = Ruling.LINES, margin = true).toLayout().paper.marginX)
        assertNull(TemplateDraft(ruling = Ruling.LINES, margin = false).toLayout().paper.marginX)
    }

    @Test
    fun `a copied planner keeps its parts when the page turns landscape`() {
        val weekly = BuiltInTemplates.byId("planners.weekly")!!.layout
        val draft = TemplateDraft.from(weekly).copy(format = PageFormat.A4_LANDSCAPE, paperColor = TemplateColors.PAPERS[1])
        val landscape = draft.toLayout()
        assertEquals(1123f, landscape.width)
        assertEquals(weekly.components.size, landscape.components.size)
        assertEquals(weekly.resized(1123f, 794f).components, landscape.components)
        assertEquals(TemplateColors.PAPERS[1], landscape.paperColor)
        // And back to portrait: the first box is where it started (within float rounding).
        val back = TemplateDraft.from(landscape).copy(format = PageFormat.A4_PORTRAIT).toLayout()
        val before = weekly.components.filterIsInstance<Box>().first()
        val after = back.components.filterIsInstance<Box>().first()
        assertEquals(before.x, after.x, 0.01f)
        assertEquals(before.w, after.w, 0.01f)
        assertEquals(before.h, after.h, 0.01f)
    }

    @Test
    fun `spacing stays within limits`() {
        assertEquals(TemplateDraft.MIN_SPACING, TemplateDraft(spacing = 2f).toLayout().paper.spacing)
        assertEquals(TemplateDraft.MAX_SPACING, TemplateDraft(spacing = 500f).toLayout().paper.spacing)
    }
}
