package com.appname.notes.core.ink.template

import com.appname.notes.core.ink.model.PageTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageDesignTest {

    @Test
    fun `backgrounds match the Basic templates`() {
        fun basic(id: String) = BuiltInTemplates.byId(id)!!.layout
        val blank = TemplateLayout()
        assertEquals(basic("basic.lined"), PageDesign.withBackground(blank, Ruling.LINES, SpacingPreset.NORMAL))
        assertEquals(basic("basic.lined_narrow"), PageDesign.withBackground(blank, Ruling.LINES, SpacingPreset.NARROW))
        assertEquals(basic("basic.lined_wide"), PageDesign.withBackground(blank, Ruling.LINES, SpacingPreset.WIDE))
        assertEquals(basic("basic.grid_narrow"), PageDesign.withBackground(blank, Ruling.GRID, SpacingPreset.NARROW))
        assertEquals(basic("basic.grid_wide"), PageDesign.withBackground(blank, Ruling.GRID, SpacingPreset.WIDE))
        assertEquals(basic("basic.dot"), PageDesign.withBackground(blank, Ruling.DOTS, SpacingPreset.NORMAL))
        assertEquals(basic("basic.blank"), PageDesign.withBackground(basic("basic.grid"), Ruling.NONE, SpacingPreset.NORMAL))
    }

    @Test
    fun `a new background keeps the paper colour and page size but drops a template design`() {
        val weekly = BuiltInTemplates.byId("planners.weekly")!!.layout.copy(paperColor = TemplateColors.PAPERS[2]).resized(1123f, 794f)
        assertTrue(PageDesign.hasDesign(weekly))
        val grid = PageDesign.withBackground(weekly, Ruling.GRID, SpacingPreset.NORMAL)
        assertFalse(PageDesign.hasDesign(grid))
        assertEquals(TemplateColors.PAPERS[2], grid.paperColor)
        assertEquals(1123f, grid.width)
        assertEquals(794f, grid.height)
    }

    @Test
    fun `paper colour keeps the design`() {
        val weekly = BuiltInTemplates.byId("planners.weekly")!!.layout
        val cream = PageDesign.withPaperColor(weekly, 0x00FFF8E7) // alpha forced opaque
        assertEquals(0xFFFFF8E7.toInt(), cream.paperColor)
        assertEquals(weekly.components, cream.components)
        val plain = PageDesign.current(null, PageTemplate.GRID, 794f, 1123f)
        assertEquals(TemplateLayout.basic(PageTemplate.GRID), plain)
    }

    @Test
    fun `spacing presets round trip`() {
        for (r in Ruling.entries) for (p in SpacingPreset.entries) assertEquals(p, PageDesign.preset(r, PageDesign.spacing(r, p)))
        assertEquals(SpacingPreset.NORMAL, PageDesign.preset(Ruling.LINES, 31f))
    }

    @Test
    fun `custom paper is always light`() {
        for (hue in 0 until 360 step 30) {
            for (tint in listOf(0f, 0.5f, 1f)) {
                val c = PageDesign.customPaper(hue.toFloat(), tint)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                assertEquals(0xFF, (c ushr 24))
                assertTrue("hue $hue tint $tint is too dark", minOf(r, g, b) >= 180)
            }
        }
        assertEquals(0xFFF7F7F7.toInt(), PageDesign.customPaper(200f, 0f)) // no tint = near white
        PageDesign.PALETTES.forEach { assertEquals(6, it.colors.size) }
    }
}
