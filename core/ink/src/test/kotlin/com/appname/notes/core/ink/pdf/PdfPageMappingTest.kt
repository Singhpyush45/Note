package com.appname.notes.core.ink.pdf

import com.appname.notes.core.ink.TestInk
import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.Pt
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.model.StrokeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PdfPageMappingTest {

    // A4 portrait with a crop box that doesn't start at 0,0 (common in scanned or trimmed PDFs).
    private fun box(rotation: Int) = PdfPageBox(left = 10.0, bottom = 20.0, right = 605.0, top = 862.0, rotation = rotation)

    /** Page sizes are stored as floats, so corners are exact to ~0.0001 pt; 0.001 pt is far below a pixel. */
    private fun assertNear(expected: Vec, actual: Vec, tolerance: Double = 1e-3) {
        assertTrue("expected $expected got $actual", hypot(expected.x - actual.x, expected.y - actual.y) < tolerance)
    }

    @Test
    fun `displayed size swaps for sideways pages and uses 96 units per inch`() {
        assertEquals(595f / 0.75f to 842f / 0.75f, PdfPageMapping.displayedSize(box(0)))
        assertEquals(842f / 0.75f to 595f / 0.75f, PdfPageMapping.displayedSize(box(90)))
        assertEquals(842f / 0.75f to 595f / 0.75f, PdfPageMapping.displayedSize(box(-90)))
        assertEquals(595f / 0.75f to 842f / 0.75f, PdfPageMapping.displayedSize(box(540)))
    }

    @Test
    fun `displayed corners land on the right crop-box corners for every rotation`() {
        // Our top-left (0,0) and bottom-right (w,h) in displayed page units.
        fun corners(r: Int): Pair<Vec, Vec> {
            val (w, h) = PdfPageMapping.displayedSize(box(r))
            return PdfPageMapping.toUser(box(r), Vec(0.0, 0.0)) to PdfPageMapping.toUser(box(r), Vec(w.toDouble(), h.toDouble()))
        }
        // 0°: displayed top-left = crop top-left.
        corners(0).let { (tl, br) -> assertNear(Vec(10.0, 862.0), tl); assertNear(Vec(605.0, 20.0), br) }
        // 90° clockwise: the page's left edge is shown on top, so displayed top-left = crop bottom-left.
        corners(90).let { (tl, br) -> assertNear(Vec(10.0, 20.0), tl); assertNear(Vec(605.0, 862.0), br) }
        // 180°: upside down.
        corners(180).let { (tl, br) -> assertNear(Vec(605.0, 20.0), tl); assertNear(Vec(10.0, 862.0), br) }
        // 270°: displayed top-left = crop top-right.
        corners(270).let { (tl, br) -> assertNear(Vec(605.0, 862.0), tl); assertNear(Vec(10.0, 20.0), br) }
    }

    @Test
    fun `mapping round-trips and preserves distances times three quarters`() {
        for (r in listOf(0, 90, 180, 270)) {
            val a = Vec(100.0, 200.0)
            val b = Vec(400.0, 600.0)
            val ua = PdfPageMapping.toUser(box(r), a)
            val ub = PdfPageMapping.toUser(box(r), b)
            assertNear(a, PdfPageMapping.toUnits(box(r), ua), 1e-9)
            assertEquals(hypot(300.0, 400.0) * 0.75, hypot(ua.x - ub.x, ua.y - ub.y), 1e-9)
        }
    }

    @Test
    fun `ink becomes drawing commands, highlighter first, pressure pens as segments`() {
        val pen = InkBrush(InkTool.PEN, 0xFF102030.toInt(), 2f)
        val highlighter = InkBrush(InkTool.HIGHLIGHTER, 0x66FFEB3B, 18f)
        val stroke = StrokeItem("s", 2, pen, TestInk.points(0 to 0, 10 to 0, 20 to 5, pressure = true))
        val hl = StrokeItem("h", 1, highlighter, TestInk.points(0 to 50, 100 to 50))
        val circle = ShapeItem("c", 3, pen.copy(colorArgb = 0xFF0000FF.toInt()), ShapeKind.CIRCLE, ShapeGeometry.Ellipse(Pt(50f, 50f), 20f, 20f, 0f), null)
        val ops = PdfInkOps.forItems(listOf(stroke, circle, hl))

        val styles = ops.filterIsInstance<PdfOp.Style>()
        // Highlighter first, translucent, square caps.
        assertEquals(0.4f, styles.first().alpha, 0.01f)
        assertEquals(false, styles.first().roundCaps)
        // Pen with pressure: two segments, width from pressure (0.5 → 0.35 + 0.45 = 0.8 × 2).
        val penStyles = styles.filter { it.r in 0.06f..0.07f }
        assertEquals(2, penStyles.size)
        assertEquals(1.6f, penStyles[0].width, 1e-4f)
        // Circle: four arcs that start and end on the circle.
        val curves = ops.filterIsInstance<PdfOp.CurveTo>()
        assertEquals(4, curves.size)
        for (c in curves) assertEquals(20.0, hypot(c.x3 - 50.0, c.y3 - 50.0), 1e-3)
        assertEquals(ops.count { it == PdfOp.Stroke }, styles.size)
    }
}
