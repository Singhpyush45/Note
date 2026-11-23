package com.appname.notes.core.ink.model

import com.appname.notes.core.ink.TestInk
import com.appname.notes.core.ink.codec.DecodeResult
import com.appname.notes.core.ink.codec.InkCodec
import com.appname.notes.core.ink.geometry.StrokeOutline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pen, pencil and brush variants (owner brief §12): real differences, stored safely. */
class BrushStyleTest {

    @Test
    fun `every variant the owner listed exists`() {
        fun names(tool: InkTool) = BrushStyle.of(tool).map { it.name }
        assertEquals(listOf("BALLPOINT", "FINE_PEN", "GEL_PEN", "FOUNTAIN_PEN", "FINELINER", "TECHNICAL_PEN", "DOT_PEN"), names(InkTool.PEN))
        assertEquals(
            listOf("NATURAL_PENCIL", "HB_PENCIL", "PENCIL_2B", "PENCIL_4B", "MECHANICAL_PENCIL", "SKETCH_PENCIL", "GRAPHITE_PENCIL"),
            names(InkTool.PENCIL),
        )
        assertEquals(13, BrushStyle.of(InkTool.BRUSH).size) // basic … marker brush, incl. calligraphy, airbrush, oil, watercolour
        assertEquals(BrushStyle.entries.size, BrushStyle.entries.map { it.code }.toSet().size)
    }

    @Test
    fun `no two variants share a rendering recipe`() {
        // Jetpack Ink draws the three classic ones with its own brush families; every other recipe is unique.
        val custom = BrushStyle.entries.filterNot { it.jetpack }
        val specs = custom.map { it.spec }
        assertEquals(custom.map { it.name }.toString(), specs.size, specs.toSet().size)
    }

    @Test
    fun `classic ink is stored in the original format, variants in format 2, and both read back`() {
        val pts = TestInk.points(0 to 0, 40 to 10, 80 to 0)
        val classic = InkCodec.quantize(StrokeItem("a", 1, InkBrush(InkTool.PEN, -16777216, 2f, BrushStyle.BALLPOINT), pts))
        assertEquals(1, InkCodec.encode(classic)[0].toInt()) // older app versions can still read it
        assertEquals(null, classic.brush.style)

        for (style in BrushStyle.entries) {
            val brush = InkBrush(style.tool, 0xFF336699.toInt(), style.defaultSize, style, pressure = style.ordinal % 2 == 0)
            val item = InkCodec.quantize(StrokeItem("s${style.code}", 1, brush, pts))
            val back = (InkCodec.decode(item.id, 1, InkCodec.encode(item)) as DecodeResult.Ok).item
            assertEquals(style.name, item, back)
            assertEquals(style, back.brush.effectiveStyle)
        }
    }

    @Test
    fun `an unknown variant from a newer version opens read-only`() {
        val item = StrokeItem("p", 1, InkBrush(InkTool.PENCIL, -1, 2f, BrushStyle.HB_PENCIL), TestInk.points(0 to 0, 5 to 5))
        val bytes = InkCodec.encode(item)
        // version, type, tool, colour (4), size (4), then the style code.
        bytes[11] = 99.toByte()
        assertTrue(InkCodec.decode("p", 1, bytes) is DecodeResult.NewerVersion)
    }

    @Test
    fun `pressure makes pressure variants thicker, not constant ones`() {
        val light = TestInk.stroke(StrokePoints(floatArrayOf(0f, 10f, 20f), floatArrayOf(0f, 0f, 0f), intArrayOf(0, 8, 16), floatArrayOf(0.1f, 0.1f, 0.1f)))
        val hard = TestInk.stroke(StrokePoints(floatArrayOf(0f, 10f, 20f), floatArrayOf(0f, 0f, 0f), intArrayOf(0, 8, 16), floatArrayOf(0.9f, 0.9f, 0.9f)))
        val pencil = InkBrush(InkTool.PENCIL, -1, 4f, BrushStyle.NATURAL_PENCIL)
        assertTrue(StrokeOutline.widths(light.points, pencil)[1] < StrokeOutline.widths(hard.points, pencil)[1])
        val fineliner = InkBrush(InkTool.PEN, -1, 4f, BrushStyle.FINELINER)
        assertEquals(StrokeOutline.widths(light.points, fineliner)[1], StrokeOutline.widths(hard.points, fineliner)[1], 0f)
        // Pressure switched off: as if pressed evenly.
        val even = pencil.copy(pressure = false)
        assertEquals(StrokeOutline.widths(light.points, even)[1], StrokeOutline.widths(hard.points, even)[1], 0f)
    }

    @Test
    fun `a nib is wide across and thin along`() {
        val nib = InkBrush(InkTool.BRUSH, -1, 10f, BrushStyle.CALLIGRAPHY_PEN) // 45°
        val acrossNib = StrokeOutline.widths(TestInk.points(0 to 0, 10 to -10, 20 to -20).let { TestInk.stroke(it).points }, nib)[1] // 135° direction
        val alongNib = StrokeOutline.widths(TestInk.points(0 to 0, 10 to 10, 20 to 20).let { TestInk.stroke(it).points }, nib)[1] // 45°
        assertEquals(10f, acrossNib, 0.01f)
        assertEquals(2f, alongNib, 0.01f) // minWidth 0.2 × 10
    }

    @Test
    fun `tapered brushes are thin at both ends`() {
        val pts = TestInk.stroke(TestInk.points(*Array(21) { (it * 10) to 0 })).points
        val w = StrokeOutline.widths(pts, InkBrush(InkTool.BRUSH, -1, 10f, BrushStyle.ARTISTIC_BRUSH))
        assertTrue(w.first() < w[10] * 0.3f)
        assertTrue(w.last() < w[10] * 0.3f)
    }

    @Test
    fun `outline surrounds the stroke and dots are evenly spaced`() {
        val pts = TestInk.stroke(TestInk.points(0 to 0, 100 to 0)).points
        val o = StrokeOutline.outline(pts, floatArrayOf(4f, 4f))
        // Left side forwards (y +2 for a left-to-right stroke's normal), then the right side backwards.
        assertEquals(listOf(0f, 2f, 100f, 2f, 100f, -2f, 0f, -2f), o.toList().map { it + 0f })
        val d = StrokeOutline.dots(pts, 10f)
        assertEquals(11, d.size / 2)
        assertEquals(50f, d[10], 1e-3f)
        assertEquals(0, StrokeOutline.outline(TestInk.stroke(TestInk.points(0 to 0)).points, floatArrayOf(4f)).size)
    }
}
