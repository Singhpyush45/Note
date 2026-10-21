package com.appname.notes.core.ink.edit

import com.appname.notes.core.ink.TestInk
import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.geometry.distanceToPolyline
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.Pt
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.model.StrokeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EraserLassoTest {

    private val horizontal = TestInk.stroke(TestInk.line(Vec(0.0, 100.0), Vec(300.0, 100.0), n = 4), z = 5)
    private val far = TestInk.stroke(TestInk.line(Vec(0.0, 500.0), Vec(300.0, 500.0)), z = 6)
    private val circle = ShapeItem("c", 7, TestInk.PEN, ShapeKind.CIRCLE, ShapeGeometry.Ellipse(Pt(600f, 100f), 50f, 50f, 0f), null)
    private val all = listOf<InkItem>(horizontal, far, circle)

    @Test
    fun `whole-object eraser removes only what it touches, including at the line edge`() {
        assertEquals(listOf(horizontal), Eraser.touched(all, listOf(Vec(150.0, 108.0)), radius = 8.0))
        // 8 + 1 (half of 2-unit width) = 9 reach: 109.5 is just outside.
        assertEquals(emptyList<InkItem>(), Eraser.touched(all, listOf(Vec(150.0, 109.5)), radius = 8.0))
        // A swept path crossing between two samples still hits (segment test, not point test).
        assertEquals(listOf(horizontal), Eraser.touched(all, listOf(Vec(140.0, 50.0), Vec(160.0, 150.0)), radius = 1.0))
        // Shapes are hit on their outline, not inside.
        assertEquals(listOf<InkItem>(circle), Eraser.touched(all, listOf(Vec(650.0, 100.0)), radius = 2.0))
        assertEquals(emptyList<InkItem>(), Eraser.touched(all, listOf(Vec(600.0, 100.0)), radius = 2.0))
    }

    @Test
    fun `partial eraser splits a stroke into two pieces outside the eraser`() {
        val edit = Eraser.erasePartially(all, listOf(Vec(150.0, 100.0)), radius = 10.0, newId = TestInk::newId)!!
        assertEquals(listOf<InkItem>(horizontal), edit.removed)
        assertEquals(2, edit.added.size)
        val pieces = edit.added.map { it as StrokeItem }
        for (p in pieces) {
            assertEquals(horizontal.z, p.z) // same depth
            assertEquals(horizontal.brush, p.brush)
            for (i in 0 until p.points.size) {
                val v = p.points.point(i)
                assertTrue("point $v left inside the eraser", distanceToPolyline(v, listOf(Vec(150.0, 100.0)), false) > 11.0)
                assertEquals(100f, p.points.y[i], 1e-4f) // on the original line
            }
        }
        // Cut follows the eraser edge closely even though the stroke had samples 100 units apart.
        val leftEnd = pieces.minBy { it.points.x[0] }.points.let { it.x[it.size - 1] }
        assertEquals(139.0f, leftEnd, 4f)
        // Pressure etc. kept on the pieces.
        assertTrue(pieces.all { it.points.pressure != null && it.points.tilt != null && it.points.orientation != null })
    }

    @Test
    fun `partial eraser removes shapes whole and ignores empty areas`() {
        val edit = Eraser.erasePartially(all, listOf(Vec(650.0, 100.0)), radius = 3.0, newId = TestInk::newId)!!
        assertEquals(listOf<InkItem>(circle), edit.removed)
        assertTrue(edit.added.isEmpty())
        assertNull(Eraser.erasePartially(all, listOf(Vec(400.0, 300.0)), radius = 3.0, newId = TestInk::newId))
    }

    @Test
    fun `erasing a whole stroke leaves nothing`() {
        val edit = Eraser.erasePartially(listOf(horizontal), listOf(Vec(0.0, 100.0), Vec(300.0, 100.0)), 5.0, TestInk::newId)!!
        assertTrue(edit.added.isEmpty())
    }

    @Test
    fun `lasso selects items mostly inside`() {
        val box = listOf(Vec(-10.0, 50.0), Vec(310.0, 50.0), Vec(310.0, 150.0), Vec(-10.0, 150.0))
        assertEquals(listOf(horizontal), Lasso.select(all, box))
        // Only half of the stroke inside: below the 60 % rule.
        val half = listOf(Vec(-10.0, 50.0), Vec(150.0, 50.0), Vec(150.0, 150.0), Vec(-10.0, 150.0))
        assertEquals(emptyList<InkItem>(), Lasso.select(listOf(TestInk.stroke(TestInk.line(Vec(0.0, 100.0), Vec(300.0, 100.0), 41))), half))
        assertEquals(emptyList<InkItem>(), Lasso.select(all, listOf(Vec(0.0, 0.0), Vec(1.0, 1.0))))
    }

    @Test
    fun `move, recolour, duplicate keep what they should`() {
        val moved = Lasso.move(listOf(horizontal, circle), 10.0, -20.0)
        val m = moved.added.first { it.id == horizontal.id } as StrokeItem
        assertEquals(10f, m.points.x[0], 1e-4f)
        assertEquals(80f, m.points.y[0], 1e-4f)
        assertEquals(horizontal.z, m.z)
        val mc = moved.added.first { it.id == "c" } as ShapeItem
        assertEquals(Pt(610f, 80f), (mc.geometry as ShapeGeometry.Ellipse).center)

        val red = 0xFFFF0000.toInt()
        assertTrue(Lasso.recolor(all, red).added.all { it.brush.colorArgb == red })

        val dup = Lasso.duplicate(listOf(far, horizontal), 5.0, 5.0, firstZ = 100, newId = TestInk::newId)
        assertTrue(dup.removed.isEmpty())
        assertEquals(listOf(100L, 101L), dup.added.map { it.z }) // relative order kept: horizontal (z5) below far (z6)
        assertTrue(dup.added.none { it.id == far.id || it.id == horizontal.id })
        val page = PageItems.of(all).apply(dup)!!
        assertEquals(5, page.size)
    }

    @Test
    fun `scale and rotate keep shapes the same kind`() {
        val t = Similarity(scale = 2.0, rotationDeg = 90.0, pivot = Vec(600.0, 100.0))
        val e = (circle.transformed(t) as ShapeItem).geometry as ShapeGeometry.Ellipse
        assertEquals(100f, e.rx, 1e-3f)
        assertEquals(Pt(600f, 100f), e.center)
        val s = horizontal.transformed(t) as StrokeItem
        assertEquals(4f, s.brush.size, 1e-4f)
        // (0,100) rotated 90° around (600,100) and doubled: (600, 100 − 1200).
        assertEquals(600f, s.points.x[0], 1e-3f)
        assertEquals(-1100f, s.points.y[0], 1e-3f)
    }
}
