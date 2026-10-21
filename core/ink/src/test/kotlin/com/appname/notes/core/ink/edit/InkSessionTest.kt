package com.appname.notes.core.ink.edit

import com.appname.notes.core.ink.TestInk
import com.appname.notes.core.ink.model.PageTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InkSessionTest {

    private fun page(id: String) = InkSession.Page(id, PageTemplate.BLANK, 794f, 1123f, PageItems.EMPTY)

    @Test
    fun `gesture steps show live but save and undo as one change`() {
        val a = TestInk.stroke(TestInk.points(0 to 0, 9 to 9), z = 1)
        val b = TestInk.stroke(TestInk.points(0 to 0, 9 to 9), z = 2)
        val session = InkSession(listOf(page("p")))
        session.apply("p", InkEdit.add(a, b))
        val saved = mutableListOf<PageEdit>()
        session.onEdit = { saved.add(it) }

        assertTrue(session.beginGesture("p"))
        assertFalse(session.canUndo) // no undo in the middle of a gesture
        session.applyInGesture(InkEdit.remove(listOf(a)))
        session.applyInGesture(InkEdit.remove(listOf(b)))
        assertTrue(session.items("p").isEmpty())
        assertTrue(saved.isEmpty())
        val net = session.endGesture()!!
        assertEquals(setOf(a, b), net.edit.removed.toSet())
        assertEquals(listOf(net), saved)
        assertTrue(session.undo())
        assertEquals(listOf(a, b), session.items("p"))
    }

    @Test
    fun `a gesture that changes nothing saves nothing, cancel restores`() {
        val a = TestInk.stroke(TestInk.points(0 to 0), z = 1)
        val session = InkSession(listOf(page("p")))
        session.apply("p", InkEdit.add(a))
        session.beginGesture("p")
        assertNull(session.endGesture())
        session.beginGesture("p")
        session.applyInGesture(InkEdit.remove(listOf(a)))
        session.cancelGesture()
        assertEquals(listOf(a), session.items("p"))
    }

    @Test
    fun `pages can be inserted, retemplated and removed`() {
        val session = InkSession(listOf(page("a")))
        var changes = 0
        session.onChanged = { changes++ }
        session.insertPage(0, page("front"))
        session.insertPage(99, page("end"))
        assertEquals(listOf("front", "a", "end"), session.pages.map { it.id })
        session.setTemplate("a", PageTemplate.GRID)
        assertEquals(PageTemplate.GRID, session.page("a")!!.template)
        session.apply("end", InkEdit.add(TestInk.stroke(TestInk.points(1 to 1))))
        session.removePage("end")
        assertFalse(session.canUndo) // its history can't apply any more
        assertEquals(5, changes)
        assertFalse(session.apply("end", InkEdit.add(TestInk.stroke(TestInk.points(1 to 1)))))
    }
}
