package com.appname.notes.core.ink.input

import com.appname.notes.core.ink.geometry.Vec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ruler (owner brief §16): strokes along its edges come out straight. */
class RulerTest {

    private val flat = Ruler(500.0, 500.0, 0.0, 800.0, 60.0)

    @Test
    fun `points next to an edge go onto it`() {
        assertEquals(1, flat.edgeNear(Vec(300.0, 545.0), 24.0)) // 15 px below the lower edge
        assertEquals(-1, flat.edgeNear(Vec(300.0, 460.0), 24.0)) // 10 px above the upper edge
        assertNull(flat.edgeNear(Vec(300.0, 600.0), 24.0)) // too far
        assertNull(flat.edgeNear(Vec(300.0, 500.0), 24.0)) // on the body: that moves the ruler
        val p = flat.project(Vec(320.0, 551.0), 1)
        assertEquals(530.0, p.y, 1e-9)
        assertEquals(320.0, p.x, 1e-9)
    }

    @Test
    fun `a turned ruler keeps lines at its angle`() {
        val r = flat.turnedTowards(Vec(500.0 + 100.0, 500.0 + 100.0)) // 45°
        assertEquals(45.0, r.angleDeg, 1e-9)
        val a = r.project(Vec(400.0, 640.0), 1)
        val b = r.project(Vec(600.0, 820.0), 1)
        assertEquals(1.0, (b.y - a.y) / (b.x - a.x), 1e-9)
        // Near a multiple of 15° it snaps.
        assertEquals(30.0, flat.turnedTowards(Vec(500.0 + 100.0, 500.0 + 59.0)).angleDeg, 1e-9)
    }

    @Test
    fun `body and knob`() {
        assertTrue(flat.onBody(Vec(500.0, 500.0)))
        assertFalse(flat.onBody(Vec(500.0, 540.0)))
        assertTrue(flat.onKnob(Vec(870.0, 500.0), 26.0))
        val moved = flat.moved(10.0, -5.0)
        assertEquals(510.0, moved.cx, 1e-9)
        assertEquals(495.0, moved.cy, 1e-9)
    }
}
