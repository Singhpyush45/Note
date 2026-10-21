package com.appname.notes.core.ink.edit

import com.appname.notes.core.ink.TestInk
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.model.StrokeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class InkEditTest {

    @Test
    fun `items stay in paint order`() {
        val a = TestInk.stroke(TestInk.points(0 to 0, 1 to 1), z = 3, id = "a")
        val b = TestInk.stroke(TestInk.points(0 to 0, 1 to 1), z = 1, id = "b")
        val c = TestInk.stroke(TestInk.points(0 to 0, 1 to 1), z = 3, id = "0c") // same z: id decides
        val page = PageItems.of(listOf(a, b, c))
        assertEquals(listOf("b", "0c", "a"), page.items.map { it.id })
        assertEquals(4L, page.nextZ)
        assertEquals(1L, PageItems.EMPTY.nextZ)
    }

    @Test
    fun `edits that don't fit are rejected and change nothing`() {
        val a = TestInk.stroke(TestInk.points(0 to 0, 1 to 1), id = "a")
        val page = PageItems.of(listOf(a))
        assertNull(page.apply(InkEdit.add(a))) // duplicate id
        assertNull(page.apply(InkEdit.remove(listOf(TestInk.stroke(TestInk.points(0 to 0), id = "zz")))))
        // Removing a stale copy (content differs) is refused too.
        assertNull(page.apply(InkEdit.remove(listOf(a.copy(z = 9)))))
        assertEquals(listOf(a), page.items)
    }

    @Test
    fun `shape correction is two undo steps - undo brings the stroke back`() {
        val history = InkHistory()
        var page = PageItems.EMPTY
        val stroke = TestInk.stroke(TestInk.points(0 to 0, 100 to 0), z = page.nextZ)
        fun commit(e: InkEdit) { page = page.apply(e)!!; history.record(e) }
        commit(InkEdit.add(stroke))
        val shape = ShapeItem(TestInk.newId(), stroke.z, stroke.brush, ShapeKind.LINE, ShapeGeometry.Segment(com.appname.notes.core.ink.model.Pt(0f, 0f), com.appname.notes.core.ink.model.Pt(100f, 0f)), stroke.points)
        commit(InkEdit.replace(stroke, shape))
        assertEquals(listOf<InkItem>(shape), page.items)
        page = page.apply(history.undo()!!)!!
        assertEquals(listOf<InkItem>(stroke), page.items)
        page = page.apply(history.redo()!!)!!
        assertEquals(listOf<InkItem>(shape), page.items)
    }

    @Test
    fun `random edits undo back to the start and redo forward again`() {
        val rng = Random(9)
        repeat(20) {
            val history = InkHistory(limit = 1000)
            var page = PageItems.EMPTY
            val states = mutableListOf(page.items)
            repeat(150) {
                val items = page.items
                val edit: InkEdit = when {
                    items.isEmpty() || rng.nextInt(3) == 0 ->
                        InkEdit.add(if (rng.nextBoolean()) TestInk.randomStroke(rng, page.nextZ) else TestInk.randomShape(rng, page.nextZ))
                    rng.nextInt(4) == 0 -> InkEdit.remove(items.shuffled(rng).take(1 + rng.nextInt(minOf(3, items.size))))
                    rng.nextInt(3) == 0 -> Lasso.move(items.shuffled(rng).take(1 + rng.nextInt(items.size)), rng.nextDouble() * 50, -rng.nextDouble() * 50)
                    rng.nextInt(2) == 0 -> Lasso.recolor(items.take(1 + rng.nextInt(items.size)), rng.nextInt())
                    else -> {
                        val strokes = items.filterIsInstance<StrokeItem>()
                        if (strokes.isEmpty()) {
                            InkEdit.add(TestInk.randomStroke(rng, page.nextZ))
                        } else {
                            val s = strokes[rng.nextInt(strokes.size)]
                            Eraser.erasePartially(items, listOf(s.points.point(s.points.size / 2)), 3.0, TestInk::newId)
                                ?: InkEdit.add(TestInk.randomStroke(rng, page.nextZ))
                        }
                    }
                }
                page = page.apply(edit) ?: error("edit didn't apply")
                history.record(edit)
                states.add(page.items)
            }
            for (i in states.size - 2 downTo 0) {
                page = page.apply(history.undo()!!)!!
                assertEquals(states[i], page.items)
            }
            assertFalse(history.canUndo)
            for (i in 1 until states.size) {
                page = page.apply(history.redo()!!)!!
                assertEquals(states[i], page.items)
            }
        }
    }

    @Test
    fun `history limit forgets oldest, new change clears redo`() {
        val h = InkHistory(limit = 3)
        val edits = List(5) { InkEdit.add(TestInk.stroke(TestInk.points(0 to 0))) }
        edits.forEach(h::record)
        repeat(3) { assertTrue(h.undo() != null) }
        assertNull(h.undo())
        assertTrue(h.canRedo)
        h.record(InkEdit.add(TestInk.stroke(TestInk.points(0 to 0))))
        assertFalse(h.canRedo)
        h.record(InkEdit(emptyList(), emptyList())) // empty edits aren't recorded
        assertNull(h.redo())
    }
}
