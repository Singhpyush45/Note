package com.appname.notes.core.ink.layout

import com.appname.notes.core.ink.geometry.Rect
import com.appname.notes.core.ink.geometry.Vec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageStackTest {

    private val stack = PageStack(listOf(794f to 1123f, 794f to 1123f, 500f to 700f), gap = 24.0)
    private val actual = 160.0 / 96 * 2.75 // real size on a 2.75× density phone
    private val rules = ViewportRules(actualSize = actual, margin = 16.0)

    @Test
    fun `pages stack with gaps and narrow pages are centred`() {
        assertEquals(0.0, stack.top(0), 0.0)
        assertEquals(1147.0, stack.top(1), 0.0)
        assertEquals(2294.0, stack.top(2), 0.0)
        assertEquals(2994.0, stack.height, 0.0)
        assertEquals(147.0, stack.left(2), 0.0)
        assertEquals(Vec(10.0, 20.0), stack.toPage(1, stack.toWorld(1, Vec(10.0, 20.0))))
    }

    @Test
    fun `page lookup, gaps go to the nearer page, visible range`() {
        assertEquals(0, stack.pageAt(-50.0))
        assertEquals(0, stack.pageAt(1130.0)) // in the gap, closer to page 0
        assertEquals(1, stack.pageAt(1140.0))
        assertEquals(2, stack.pageAt(99999.0))
        assertEquals(0..1, stack.visible(Rect(0.0, 1000.0, 794.0, 1200.0)))
        assertEquals(2..2, stack.visible(Rect(0.0, 2400.0, 794.0, 2500.0)))
        assertTrue(PageStack(emptyList()).visible(Rect(0.0, 0.0, 1.0, 1.0)).isEmpty())
    }

    @Test
    fun `phone fits the page width, tablet stops at real size`() {
        assertEquals((1080.0 - 32) / 794, rules.fitScale(1080.0, stack), 1e-9)
        assertEquals(actual, rules.fitScale(5000.0, stack), 1e-9)
        // Narrow content is centred horizontally.
        val v = rules.initial(5000.0, 2000.0, stack)
        assertEquals((5000 - 794 * actual) / 2, v.dx, 1e-6)
    }

    @Test
    fun `zoom keeps the point under the fingers and respects limits`() {
        val v0 = rules.initial(1080.0, 2000.0, stack)
        val focus = Vec(500.0, 900.0)
        val before = v0.toWorld(focus)
        val after = rules.zoom(v0, 2.0, focus.x, focus.y, 1080.0, 2000.0, stack).toWorld(focus)
        assertEquals(before.x, after.x, 1e-6)
        assertEquals(before.y, after.y, 1e-6)
        assertEquals(rules.minScale(1080.0, stack), rules.zoom(v0, 1e-4, 0.0, 0.0, 1080.0, 2000.0, stack).scale, 1e-9)
        assertEquals(rules.maxScale(), rules.zoom(v0, 1e6, 0.0, 0.0, 1080.0, 2000.0, stack).scale, 1e-9)
    }

    @Test
    fun `scrolling is clamped to the content`() {
        val v0 = rules.initial(1080.0, 2000.0, stack)
        assertEquals(16.0, v0.dy, 1e-9) // starts at the top margin
        assertEquals(16.0, rules.pan(v0, 0.0, 5000.0, 1080.0, 2000.0, stack).dy, 1e-9)
        val down = rules.pan(v0, 0.0, -1e9, 1080.0, 2000.0, stack)
        assertEquals(2000.0 - 16 - stack.height * v0.scale, down.dy, 1e-6)
        assertEquals(2, rules.currentPage(down, 2000.0, stack))
        assertEquals(1, rules.currentPage(rules.scrollTo(v0, 1, 1080.0, 2000.0, stack), 2000.0, stack))
    }
}
