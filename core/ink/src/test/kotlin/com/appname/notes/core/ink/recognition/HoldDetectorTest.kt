package com.appname.notes.core.ink.recognition

import com.appname.notes.core.ink.recognition.HoldDetector.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldDetectorTest {

    private fun detector() = HoldDetector(holdMillis = 500, tolerance = 6.0)

    @Test
    fun `fires once after the pen stays still for the hold time`() {
        val d = detector()
        d.start(0.0, 0.0, 0)
        // Drawing: moving 10 units every 10 ms.
        for (i in 1..30) assertNull(d.onPoint(i * 10.0, 0.0, i * 10L))
        // Pen stops at x = 300 (t = 300) with tiny sensor jitter.
        assertNull(d.onPoint(301.0, 1.0, 500))
        assertNull(d.check(799))
        assertEquals(Event.HOLD, d.check(800))
        assertTrue(d.held)
        assertNull(d.check(900)) // only once
        assertNull(d.onPoint(302.0, 0.0, 950))
    }

    @Test
    fun `samples while still can also trigger it`() {
        val d = detector()
        d.start(0.0, 0.0, 0)
        assertNull(d.onPoint(2.0, 0.0, 300))
        assertEquals(Event.HOLD, d.onPoint(3.0, 1.0, 500))
    }

    @Test
    fun `slow drawing never counts as holding`() {
        val d = detector()
        d.start(0.0, 0.0, 0)
        // 7 units per 400 ms: slow, but always beyond the 6-unit tolerance before 500 ms pass.
        for (i in 1..20) assertNull(d.onPoint(i * 7.0, 0.0, i * 400L))
        assertFalse(d.held)
    }

    @Test
    fun `moving after a hold releases it and can hold again`() {
        val d = detector()
        d.start(0.0, 0.0, 0)
        assertEquals(Event.HOLD, d.check(500))
        assertEquals(Event.RELEASED, d.onPoint(20.0, 0.0, 600))
        assertFalse(d.held)
        assertEquals(Event.HOLD, d.check(1100))
    }

    @Test
    fun `timer helper and reset`() {
        val d = detector()
        assertEquals(-1, d.millisUntilHold(0))
        d.start(0.0, 0.0, 100)
        assertEquals(400, d.millisUntilHold(200))
        assertEquals(0, d.millisUntilHold(2000))
        d.reset()
        assertNull(d.check(5000))
    }
}
