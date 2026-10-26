package com.appname.notes.core.ink.input

import com.appname.notes.core.ink.TestInk
import com.appname.notes.core.ink.edit.InkSession
import com.appname.notes.core.ink.edit.PageEdit
import com.appname.notes.core.ink.edit.PageItems
import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.model.StrokeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InkInputControllerTest {

    private val saved = mutableListOf<PageEdit>()
    private val session = InkSession(
        listOf(
            InkSession.Page("p1", PageTemplate.BLANK, 794f, 1123f, PageItems.EMPTY),
            InkSession.Page("p2", PageTemplate.LINED, 794f, 1123f, PageItems.EMPTY),
        ),
    ).also { it.onEdit = { e -> saved.add(e) } }
    private val c = InkInputController(session, TestInk::newId)

    private var clock = 1_000L

    /** Draws a polyline on [page] with samples every 8 ms, like a 120 Hz pen. */
    private fun draw(
        points: List<Vec>,
        page: String = "p1",
        source: PointerSource = PointerSource.STYLUS,
        holdMillisAtEnd: Long = 0,
    ): Response {
        val s = points.map { sample(it) }
        c.down(page, s.first(), source, unitsPerDp = 1.0)
        c.move(s.drop(1))
        if (holdMillisAtEnd > 0) {
            clock += holdMillisAtEnd
            c.tick(clock)
        }
        return c.up(null)
    }

    private fun sample(p: Vec): Sample {
        clock += 8
        return Sample(p.x, p.y, clock, pressure = 0.5f, tilt = 0.2f, orientation = 1f)
    }

    private fun line(a: Vec, b: Vec, n: Int = 30) = List(n) { i ->
        val t = i.toDouble() / (n - 1)
        Vec(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    private fun rectangle(x0: Double, y0: Double, x1: Double, y1: Double): List<Vec> {
        val c = listOf(Vec(x0, y0), Vec(x1, y0), Vec(x1, y1), Vec(x0, y1), Vec(x0, y0))
        return (0 until 4).flatMap { line(c[it], c[it + 1], 25).dropLast(1) } + c[0]
    }

    private fun items(page: String = "p1") = session.items(page)

    @Test
    fun `a pen stroke is saved in page coordinates with pressure and one undo removes it`() {
        val r = draw(line(Vec(10.0, 10.0), Vec(60.0, 80.0), n = 5))
        assertEquals(LiveStroke.FINISH, r.live)
        val s = items().single() as StrokeItem
        assertEquals(5, s.points.size)
        assertEquals(10f, s.points.x[0], 0.07f)
        assertEquals(0.5f, s.points.pressure!![0], 1e-4f)
        assertEquals(listOf(0, 8, 16, 24, 32), s.points.t.toList())
        assertEquals(1, saved.size)
        assertTrue(session.undo())
        assertTrue(items().isEmpty())
        assertEquals(2, saved.size) // the undo is saved too
    }

    @Test
    fun `finger strokes have no pressure and a tap leaves a dot`() {
        draw(line(Vec(0.0, 0.0), Vec(30.0, 0.0), 4), source = PointerSource.FINGER)
        assertNull((items().single() as StrokeItem).points.pressure)
        draw(listOf(Vec(100.0, 100.0)))
        assertEquals(1, (items().last() as StrokeItem).points.size)
    }

    @Test
    fun `draw and hold turns a rough rectangle into a rectangle and undo brings the ink back`() {
        val r = run {
            val s = rectangle(100.0, 100.0, 400.0, 250.0).map { sample(it) }
            c.down("p1", s.first(), PointerSource.STYLUS, 1.0)
            c.move(s.drop(1))
            clock += 300
            assertEquals(Response.NONE, c.tick(clock)) // not yet: 500 ms hold
            clock += 250
            val held = c.tick(clock)
            assertEquals(LiveStroke.CANCEL, held.live)
            assertTrue(held.haptic)
            assertEquals(ShapeKind.RECTANGLE, c.shapePreview?.kind)
            c.up(null)
        }
        assertEquals(LiveStroke.NONE, r.live) // the live stroke was already cancelled
        val shape = items().single() as ShapeItem
        assertEquals(ShapeKind.RECTANGLE, shape.kind)
        assertNotNull(shape.source) // original ink kept
        assertNull(c.shapePreview)
        assertTrue(session.undo())
        assertTrue(items().single() is StrokeItem)
        assertTrue(session.undo())
        assertTrue(items().isEmpty())
    }

    @Test
    fun `shape correction can be turned off`() {
        c.tools = c.tools.copy(shapeCorrection = false)
        draw(rectangle(100.0, 100.0, 400.0, 250.0), holdMillisAtEnd = 800)
        assertTrue(items().single() is StrokeItem)
        assertEquals(-1, c.millisUntilHold(clock))
    }

    @Test
    fun `holding after handwriting keeps the ink`() {
        val zigzag = (0..8).map { Vec(it * 20.0, if (it % 2 == 0) 0.0 else 40.0) }
        val r = draw(zigzag.flatMapIndexed { i, p -> if (i == 0) listOf(p) else line(zigzag[i - 1], p, 6).drop(1) }, holdMillisAtEnd = 800)
        assertEquals(LiveStroke.FINISH, r.live)
        assertTrue(items().single() is StrokeItem)
    }

    @Test
    fun `after snapping a line, dragging moves its end and snaps to 45 degrees`() {
        val s = line(Vec(100.0, 100.0), Vec(300.0, 100.0)).map { sample(it) }
        c.down("p1", s.first(), PointerSource.STYLUS, 1.0)
        c.move(s.drop(1))
        clock += 600
        c.tick(clock)
        assertEquals(ShapeKind.LINE, c.shapePreview?.kind)
        c.move(listOf(sample(Vec(400.0, 105.0)))) // 2.9°: snaps to horizontal
        val g = c.shapePreview!!.geometry as ShapeGeometry.Segment
        assertEquals(100f, g.start.x, 0.5f)
        assertEquals(400f, g.end.x, 1f)
        assertEquals(100f, g.end.y, 0.01f)
        c.up(null)
        assertEquals(ShapeKind.LINE, (items().single() as ShapeItem).kind)
    }

    @Test
    fun `stroke eraser removes touched ink in one undo step`() {
        draw(line(Vec(0.0, 100.0), Vec(300.0, 100.0)))
        draw(line(Vec(0.0, 200.0), Vec(300.0, 200.0)))
        draw(line(Vec(0.0, 300.0), Vec(300.0, 300.0)))
        saved.clear()
        c.tools = c.tools.copy(tool = EditorTool.ERASER, eraserMode = EraserMode.STROKE)
        draw(line(Vec(150.0, 50.0), Vec(150.0, 250.0), 20))
        assertEquals(1, items().size)
        assertEquals(1, saved.size) // one save for the whole gesture
        assertEquals(2, saved.single().edit.removed.size)
        assertTrue(session.undo())
        assertEquals(3, items().size)
    }

    @Test
    fun `area eraser cuts strokes, and the stylus eraser end erases with any tool`() {
        draw(line(Vec(0.0, 100.0), Vec(300.0, 100.0)))
        c.tools = c.tools.copy(eraserMode = EraserMode.AREA)
        draw(listOf(Vec(150.0, 100.0)), source = PointerSource.STYLUS_ERASER)
        assertEquals(EditorTool.PEN, c.tools.tool)
        assertEquals(2, items().size)
        assertTrue(items().all { it is StrokeItem })
    }

    @Test
    fun `cancelling restores everything`() {
        draw(line(Vec(0.0, 100.0), Vec(300.0, 100.0)))
        val before = items()
        // Half-drawn stroke cancelled (e.g. a second finger started a pinch).
        val s = line(Vec(0.0, 0.0), Vec(50.0, 50.0)).map { sample(it) }
        c.down("p1", s.first(), PointerSource.FINGER, 1.0)
        c.move(s.drop(1))
        assertEquals(LiveStroke.CANCEL, c.cancel().live)
        // Half-done erase cancelled.
        c.tools = c.tools.copy(tool = EditorTool.ERASER)
        c.down("p1", sample(Vec(150.0, 100.0)), PointerSource.STYLUS, 1.0)
        assertTrue(items().isEmpty())
        c.cancel()
        assertEquals(before, items())
        assertFalse(c.gestureActive)
    }

    @Test
    fun `lasso selects, moves, recolours, duplicates and deletes`() {
        draw(line(Vec(100.0, 100.0), Vec(200.0, 100.0)))
        draw(line(Vec(100.0, 500.0), Vec(200.0, 500.0)))
        c.tools = c.tools.copy(tool = EditorTool.LASSO)
        draw(listOf(Vec(80.0, 80.0), Vec(220.0, 80.0), Vec(220.0, 120.0), Vec(80.0, 120.0), Vec(80.0, 82.0)))
        val sel = c.selection!!
        assertEquals(1, sel.ids.size)

        // Drag from inside the selection.
        draw(listOf(Vec(150.0, 100.0), Vec(160.0, 140.0), Vec(170.0, 180.0)))
        val moved = c.selectedItems().single() as StrokeItem
        assertEquals(120f, moved.points.x[0], 0.1f)
        assertEquals(180f, moved.points.y[0], 0.1f)

        assertTrue(c.recolorSelection(0xFFE53935.toInt()))
        assertEquals(0xFFE53935.toInt(), c.selectedItems().single().brush.colorArgb)

        assertTrue(c.duplicateSelection())
        assertEquals(3, items().size)
        assertTrue(c.selectedItems().single().id != moved.id)

        assertTrue(c.deleteSelection())
        assertEquals(2, items().size)
        assertNull(c.selection)

        // Tap outside clears; undo history has: draw, draw, move, recolour, duplicate, delete.
        repeat(4) { assertTrue(session.undo()) }
        assertEquals(100f, (items().first() as StrokeItem).points.y[0], 0.1f)
    }

    @Test
    fun `dragging the corner handle resizes the selection, line width too, in one undo step`() {
        draw(line(Vec(100.0, 100.0), Vec(200.0, 100.0)))
        c.tools = c.tools.copy(tool = EditorTool.LASSO)
        draw(listOf(Vec(80.0, 80.0), Vec(220.0, 80.0), Vec(220.0, 120.0), Vec(80.0, 120.0), Vec(80.0, 82.0)))
        val b = c.selection!!.bounds
        val handle = c.resizeHandle!!
        assertEquals(Vec(b.right, b.bottom), handle)
        // Drag the handle so the selection becomes twice as big around its top-left corner.
        val anchor = Vec(b.left, b.top)
        val target = Vec(anchor.x + (handle.x - anchor.x) * 2, anchor.y + (handle.y - anchor.y) * 2)
        val s0 = items().single() as StrokeItem
        c.down("p1", sample(handle), PointerSource.STYLUS, 1.0)
        c.move(listOf(sample(Vec((handle.x + target.x) / 2, (handle.y + target.y) / 2))))
        assertEquals(1.5, c.selectionTransform!!.scale, 1e-6) // live preview
        c.move(listOf(sample(target)))
        c.up(null)
        assertNull(c.selectionTransform)
        val s1 = items().single() as StrokeItem
        val len0 = s0.points.x.last() - s0.points.x.first()
        val len1 = s1.points.x.last() - s1.points.x.first()
        assertEquals(2.0 * len0, len1.toDouble(), 0.2)
        assertEquals(s0.brush.size * 2, s1.brush.size, 1e-3f)
        assertEquals(s0.id, s1.id) // same item, transformed
        assertTrue(session.undo())
        assertEquals(s0, items().single())
    }

    @Test
    fun `highlighter recolour keeps translucency, selection survives undo cleanly`() {
        c.tools = c.tools.copy(tool = EditorTool.HIGHLIGHTER)
        draw(line(Vec(100.0, 100.0), Vec(200.0, 100.0)))
        assertEquals(InkTool.HIGHLIGHTER, items().single().brush.tool)
        c.tools = c.tools.copy(tool = EditorTool.LASSO)
        draw(listOf(Vec(80.0, 70.0), Vec(220.0, 70.0), Vec(220.0, 130.0), Vec(80.0, 130.0), Vec(80.0, 72.0)))
        c.recolorSelection(0xFF00FF00.toInt())
        assertEquals(0x6600FF00, items().single().brush.colorArgb)
        session.undo()
        session.undo() // removes the stroke entirely
        c.refreshSelection()
        assertNull(c.selection)
    }

    @Test
    fun `read-only notes ignore input and other pages are separate`() {
        c.readOnly = true
        assertEquals(Response.NONE, c.down("p1", sample(Vec(1.0, 1.0)), PointerSource.STYLUS, 1.0))
        c.readOnly = false
        draw(line(Vec(0.0, 0.0), Vec(40.0, 40.0)), page = "p2")
        assertTrue(items("p1").isEmpty())
        assertEquals(1, items("p2").size)
        assertEquals(Response.NONE, c.down("missing", sample(Vec(1.0, 1.0)), PointerSource.STYLUS, 1.0))
    }

    @Test
    fun `shape tool drags out a filled rectangle, stored with its fill`() {
        c.tools = c.tools.copy(tool = EditorTool.SHAPE, shapeKind = com.appname.notes.core.ink.model.ShapeKind.RECTANGLE, shapeFill = 0x401A73E8)
        draw(line(Vec(100.0, 100.0), Vec(300.0, 200.0)))
        val shape = items().single() as com.appname.notes.core.ink.model.ShapeItem
        assertEquals(com.appname.notes.core.ink.model.ShapeKind.RECTANGLE, shape.kind)
        assertEquals(0x401A73E8, shape.fill)
        val box = shape.bounds()
        assertEquals(100.0, box.left + shape.brush.size / 2.0, 0.1)
        assertEquals(300.0, box.right - shape.brush.size / 2.0, 0.1)
        // The fill survives storage (format 2), and a plain shape stays in format 1.
        val back = com.appname.notes.core.ink.codec.InkCodec.decode(shape.id, shape.z, com.appname.notes.core.ink.codec.InkCodec.encode(shape))
        assertEquals(shape, (back as com.appname.notes.core.ink.codec.DecodeResult.Ok).item)
        assertEquals(1, com.appname.notes.core.ink.codec.InkCodec.encode(shape.copy(fill = null))[0].toInt())
        // A gradient fill keeps its second colour too.
        val g = shape.copy(fillEnd = 0x40FF0000)
        assertEquals(g, (com.appname.notes.core.ink.codec.InkCodec.decode(g.id, g.z, com.appname.notes.core.ink.codec.InkCodec.encode(g)) as com.appname.notes.core.ink.codec.DecodeResult.Ok).item)
        // Lines can't be filled; a tap adds nothing.
        c.tools = c.tools.copy(shapeKind = com.appname.notes.core.ink.model.ShapeKind.LINE)
        draw(line(Vec(100.0, 400.0), Vec(300.0, 400.0)))
        assertEquals(null, (items().last() as com.appname.notes.core.ink.model.ShapeItem).fill)
        draw(listOf(Vec(500.0, 500.0), Vec(501.0, 501.0)))
        assertEquals(2, items().size)
    }

    @Test
    fun `selection goes to front and back, and rotates`() {
        draw(line(Vec(100.0, 100.0), Vec(200.0, 100.0)))
        draw(line(Vec(100.0, 300.0), Vec(200.0, 300.0)))
        val first = items().minBy { it.z }
        c.tools = c.tools.copy(tool = EditorTool.LASSO)
        draw(listOf(Vec(80.0, 80.0), Vec(220.0, 80.0), Vec(220.0, 120.0), Vec(80.0, 120.0), Vec(80.0, 82.0)))
        assertTrue(c.bringSelectionToFront())
        assertEquals(first.id, items().maxBy { it.z }.id)
        assertTrue(c.sendSelectionToBack())
        assertEquals(first.id, items().minBy { it.z }.id)
        // Flip left-right: a line rising to the right would fall to the right; here it stays in its box.
        val before = items().first { it.id == first.id }.bounds()
        assertTrue(c.flipSelection(horizontal = true))
        val after = items().first { it.id == first.id }.bounds()
        assertEquals(before.left, after.left, 0.1)
        assertEquals(before.right, after.right, 0.1)
        assertTrue(c.rotateSelection(90.0))
        val box = items().first { it.id == first.id }.bounds()
        assertTrue(box.bottom - box.top > box.right - box.left) // the horizontal line is now upright
    }

    @Test
    fun `box lasso selects what is inside the box`() {
        draw(line(Vec(100.0, 100.0), Vec(200.0, 100.0)))
        draw(line(Vec(100.0, 400.0), Vec(200.0, 400.0)))
        c.tools = c.tools.copy(tool = EditorTool.LASSO, lassoBox = true)
        draw(line(Vec(80.0, 80.0), Vec(220.0, 130.0)))
        assertEquals(1, c.selectedItems().size)
        assertEquals(100.0, c.selectedItems().single().bounds().center.y, 2.0)
    }

    @Test
    fun `press and hold finds the item under the finger and select picks it`() {
        draw(line(Vec(100.0, 100.0), Vec(300.0, 100.0), n = 20))
        val stroke = items().single()
        val photo = com.appname.notes.core.ink.model.ImageItem("img", 50, "a.jpg", com.appname.notes.core.ink.model.Placement(500f, 500f, 200f, 100f))
        session.apply("p1", com.appname.notes.core.ink.edit.InkEdit.add(photo))
        assertEquals(stroke.id, c.itemAt("p1", Vec(200.0, 103.0), 8.0)?.id)
        assertEquals("img", c.itemAt("p1", Vec(560.0, 520.0), 8.0)?.id)
        assertEquals(null, c.itemAt("p1", Vec(200.0, 300.0), 8.0))
        c.tools = c.tools.copy(tool = EditorTool.LASSO)
        assertTrue(c.select("p1", setOf("img")))
        assertEquals(setOf("img"), c.selection?.ids)
    }

    @Test
    fun `selection dragged onto the next page moves there and one undo brings it back`() {
        draw(line(Vec(100.0, 1000.0), Vec(300.0, 1000.0), n = 20))
        val id = items().single().id
        c.tools = c.tools.copy(tool = EditorTool.LASSO)
        assertTrue(c.select("p1", setOf(id)))
        // Page 2 starts 1123 + gap below page 1: an offset of (0, -1123 - 16) maps page-1 points onto page 2.
        assertTrue(c.moveSelectionToPage("p2", 0.0, -1000.0))
        assertTrue(items("p1").isEmpty())
        assertEquals(1, items("p2").size)
        assertEquals(0.0, items("p2").single().bounds().top, 2.0)
        assertEquals("p2", c.selection?.pageId)
        assertTrue(session.undo())
        assertEquals(1, items("p1").size)
        assertTrue(items("p2").isEmpty())
        assertTrue(session.redo())
        assertEquals(1, items("p2").size)
    }
}
