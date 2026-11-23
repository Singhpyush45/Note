package com.appname.notes.core.ink.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The laser pointer's trail (owner brief §17): fades, disappears, and flows through the rainbow. */
class LaserTrailTest {

    @Test
    fun `older points are fainter and everything is gone a tail after lifting`() {
        val t = LaserTrail(tailMillis = 600)
        t.start(0f, 0f, 1000, density = 1f)
        t.move(10f, 0f, 1100, 1f)
        t.move(20f, 0f, 1200, 1f)
        val p = t.points(1200)
        assertEquals(3, p.size)
        assertTrue(p[0].alpha < p[1].alpha && p[1].alpha < p[2].alpha)
        assertEquals(1f, p[2].alpha, 1e-6f)
        t.end()
        assertTrue(t.active(1700))
        assertFalse(t.active(1800)) // 600 ms after the last point
        assertTrue(t.points(1800).isEmpty())
    }

    @Test
    fun `the tip stays while the pointer is held still`() {
        val t = LaserTrail(tailMillis = 300)
        t.start(5f, 5f, 0, 1f)
        assertTrue(t.active(10_000))
        assertTrue(t.down)
        assertEquals(5f, t.headX, 0f)
    }

    @Test
    fun `separate lifts are separate lines`() {
        val t = LaserTrail()
        t.start(0f, 0f, 0, 1f); t.move(10f, 0f, 10, 1f); t.end()
        t.start(50f, 50f, 20, 1f); t.move(60f, 50f, 30, 1f)
        val strokes = t.points(30).map { it.stroke }
        assertEquals(2, strokes.distinct().size)
        assertEquals(strokes[0], strokes[1])
        assertEquals(strokes[2], strokes[3])
    }

    @Test
    fun `rainbow hue changes along the trail and with time`() {
        val t = LaserTrail()
        t.start(0f, 0f, 0, 1f)
        t.move(40f, 0f, 10, 1f) // 40 dp further: 60° further round
        val p = t.points(10)
        val along = ((p[1].hue - p[0].hue) + 360f) % 360f
        assertEquals(LaserTrail.DEGREES_PER_DP * 40f, along, 0.01f)
        val later = t.points(10 + LaserTrail.RAINBOW_PERIOD_MILLIS / 4)
        assertTrue(kotlin.math.abs(later[0].hue - p[0].hue) > 1f) // the colours flow
        // Density: the same finger movement in pixels on a denser screen is fewer dp.
        val dense = LaserTrail()
        dense.start(0f, 0f, 0, 2f)
        dense.move(40f, 0f, 10, 2f)
        val q = dense.points(10)
        assertEquals(LaserTrail.DEGREES_PER_DP * 20f, ((q[1].hue - q[0].hue) + 360f) % 360f, 0.01f)
    }

    @Test
    fun `tiny jitter does not add points`() {
        val t = LaserTrail()
        t.start(0f, 0f, 0, 1f)
        t.move(0.3f, 0.2f, 5, 1f)
        assertEquals(1, t.points(5).size)
    }
}
