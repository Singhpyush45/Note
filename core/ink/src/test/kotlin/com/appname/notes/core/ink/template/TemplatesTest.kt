package com.appname.notes.core.ink.template

import com.appname.notes.core.ink.model.PageTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {

    @Test
    fun `every template the owner listed exists in its category`() {
        fun names(c: TemplateCategory) = BuiltInTemplates.inCategory(c).map { it.name }
        assertEquals(
            listOf("Blank page", "Lined", "Lined (narrow)", "Lined (wide)", "Grid", "Grid (narrow)", "Grid (wide)", "Dot", "Dot (narrow)"),
            names(TemplateCategory.BASIC),
        )
        assertEquals(listOf("Notebook", "Planner 1", "Planner 2", "Planner 3", "Planner 4", "School schedule"), names(TemplateCategory.ACADEMIC))
        assertEquals(
            listOf("Checklist", "Weekly planner", "Monthly plan", "Monthly planner", "Work planner", "Health planner", "Travel plan", "Monthly budget"),
            names(TemplateCategory.PLANNERS),
        )
        assertEquals(listOf("Daily journal", "Movie/book review", "Pet care log"), names(TemplateCategory.JOURNALS))
        assertEquals(listOf("Music", "Recipes and meal plan"), names(TemplateCategory.CREATIVE))
        assertTrue(names(TemplateCategory.CUSTOM).isEmpty()) // custom ones are the user's
        val ids = BuiltInTemplates.all.map { it.id }
        assertEquals("ids are unique", ids.size, ids.toSet().size)
    }

    @Test
    fun `every built-in survives storage unchanged`() {
        for (t in BuiltInTemplates.all) {
            val decoded = TemplateJson.decodeOrNull(TemplateJson.encode(t.layout))
            assertEquals(t.id, t.layout, decoded)
        }
    }

    @Test
    fun `everything printed stays on the page`() {
        for (t in BuiltInTemplates.all) {
            val l = t.layout
            for (c in l.components) {
                val (left, top, right, bottom) = when (c) {
                    is Box -> listOf(c.x, c.y, c.x + c.w, c.y + c.h)
                    is LinedArea -> listOf(c.x, c.y, c.x + c.w, c.y + c.h)
                    is DotArea -> listOf(c.x, c.y, c.x + c.w, c.y + c.h)
                    is Checklist -> listOf(c.x, c.y, c.x + c.w, c.y + c.h)
                    is Table -> listOf(c.x, c.y, c.x + c.w, c.y + c.h)
                    is Staves -> listOf(c.x, c.y, c.x + c.w, c.y + c.h)
                    is Rule -> listOf(minOf(c.x1, c.x2), minOf(c.y1, c.y2), maxOf(c.x1, c.x2), maxOf(c.y1, c.y2))
                    is TextLabel -> listOf(c.x, c.y - c.size, c.x, c.y)
                    is CircleRow -> listOf(c.x, c.y, c.x + c.count * (c.diameter + c.gap), c.y + c.diameter)
                }
                val inside = left >= 0f && top >= 0f && right <= l.width + 0.5f && bottom <= l.height + 0.5f
                assertTrue("${t.name}: $c is off the page", inside)
            }
        }
    }

    @Test
    fun `tables label every row they were given labels for`() {
        for (t in BuiltInTemplates.all) {
            t.layout.components.filterIsInstance<Table>().forEach { table ->
                assertTrue("${t.name}: more row labels than rows", table.rowLabels.size <= table.rows)
                if (table.hasHeader) assertEquals("${t.name}: one header per column", table.columns.size, table.headers.size)
            }
        }
    }

    @Test
    fun `layouts written by a newer version still open`() {
        val newer = """
            {"width":794,"height":1123,"version":3,"glitter":true,
             "components":[
               {"type":"hologram","x":1},
               {"type":"text","x":10,"y":20,"text":"Hi","sparkle":5},
               {"type":"box","x":1,"y":2,"w":3,"h":4}
             ]}
        """.trimIndent()
        val layout = TemplateJson.decodeOrNull(newer)
        assertNotNull(layout)
        assertEquals(listOf(TextLabel(10f, 20f, "Hi"), Box(1f, 2f, 3f, 4f)), layout!!.components)
    }

    @Test
    fun `damaged layouts are reported as missing, not crashes`() {
        assertNull(TemplateJson.decodeOrNull("{not json"))
        assertNull(TemplateJson.decodeOrNull(""))
        assertNull(TemplateJson.decodeOrNull(null))
        assertNull(TemplateJson.decodeOrNull("""{"width":0,"height":10}"""))
        assertNull(TemplateJson.decodeOrNull("""{"components":[{"type":"table","x":0,"y":0,"w":1,"h":1,"columns":[],"rows":1}]}"""))
    }

    @Test
    fun `resizing moves and stretches everything with the page`() {
        val l = TemplateLayout(
            width = 100f,
            height = 200f,
            paper = PaperStyle(Ruling.LINES, 30f, marginX = 10f, top = 20f),
            components = listOf(Box(10f, 20f, 30f, 40f), TextLabel(50f, 100f, "x", size = 10f)),
        )
        val r = l.resized(200f, 100f)
        assertEquals(Box(20f, 10f, 60f, 20f), r.components[0])
        assertEquals(TextLabel(100f, 50f, "x", size = 5f), r.components[1])
        assertEquals(20f, r.paper.marginX)
        assertEquals(10f, r.paper.top)
        assertEquals(l, l.resized(100f, 200f))
    }

    @Test
    fun `table edges split the width by weight and rows evenly below the header`() {
        val t = Table(0f, 0f, 300f, 128f, listOf(1f, 2f), rows = 4, headers = listOf("a", "b"), headerHeight = 28f)
        assertEquals(listOf(0f, 100f, 300f), t.columnEdges())
        assertEquals(listOf(28f, 53f, 78f, 103f, 128f), t.rowEdges())
    }

    @Test
    fun `staves only include whole staffs`() {
        val s = Staves(0f, 0f, 100f, 100f, lineGap = 10f, staffGap = 20f)
        assertEquals(listOf(0f, 60f), s.staffTops()) // 0..40, 60..100
    }

    @Test
    fun `simple backgrounds map both ways`() {
        PageTemplate.entries.forEach { assertEquals(it, TemplateLayout.basic(it).basicTemplate) }
        assertEquals(PageFormat.A4_LANDSCAPE, PageFormat.closest(1123f, 794f))
        assertEquals(PageFormat.LONG_20_9, PageFormat.closest(794f, 1760f))
        assertFalse(BuiltInTemplates.all.any { it.isCustom })
    }
}
