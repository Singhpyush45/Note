package com.appname.notes.core.ink.codec

import com.appname.notes.core.ink.TestInk
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.StrokeItem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

class InkCodecTest {

    private fun roundTrip(item: InkItem): InkItem {
        val r = InkCodec.decode(item.id, item.z, InkCodec.encode(item))
        assertTrue("decode failed: $r", r is DecodeResult.Ok)
        return (r as DecodeResult.Ok).item
    }

    @Test
    fun `quantised strokes and shapes round-trip exactly`() {
        val rng = Random(1)
        repeat(500) { i ->
            val item = if (i % 2 == 0) TestInk.randomStroke(rng, z = i.toLong()) else TestInk.randomShape(rng, z = i.toLong())
            val q = InkCodec.quantize(item)
            assertEquals(q, roundTrip(q))
            // Encoding is deterministic: same item, same bytes.
            assertArrayEquals(InkCodec.encode(q), InkCodec.encode(roundTrip(q)))
        }
    }

    @Test
    fun `quantisation error is tiny`() {
        val rng = Random(2)
        repeat(100) {
            val raw = TestInk.randomStroke(rng, 1).points
            // Perturb so values are not already on the grid.
            val p = raw.x.map { it + 0.0123f }
            val item = TestInk.stroke(com.appname.notes.core.ink.model.StrokePoints(p.toFloatArray(), raw.y, raw.t, raw.pressure, raw.tilt, raw.orientation))
            val back = roundTrip(item) as StrokeItem
            for (k in p.indices) assertTrue(abs(back.points.x[k] - p[k]) <= 1f / 32 + 1e-4f) // half of 1/16 unit
            raw.pressure?.let { pr -> for (k in pr.indices) assertTrue(abs(back.points.pressure!![k] - pr[k]) < 1e-4f) }
        }
    }

    @Test
    fun `is compact`() {
        // A typical 200-point stylus stroke with all channels: well under 10 bytes per point.
        val rng = Random(3)
        val item = generateSequence { TestInk.randomStroke(rng, 1) }.first { it.points.size > 150 && it.points.tilt != null && it.points.pressure != null && it.points.orientation != null }
        val bytes = InkCodec.encode(item).size
        assertTrue("$bytes bytes for ${item.points.size} points", bytes < item.points.size * 10)
    }

    @Test
    fun `newer format is reported, not misread`() {
        val bytes = InkCodec.encode(TestInk.stroke(TestInk.points(0 to 0, 5 to 5)))
        bytes[0] = (InkCodec.FORMAT_VERSION + 1).toByte()
        assertEquals(DecodeResult.NewerVersion(InkCodec.FORMAT_VERSION + 1), InkCodec.decode("a", 1, bytes))
    }

    @Test
    fun `damaged bytes never crash`() {
        val rng = Random(4)
        val good = List(50) { InkCodec.encode(if (it % 2 == 0) TestInk.randomStroke(rng, 1) else TestInk.randomShape(rng, 1)) }
        assertTrue(InkCodec.decode("a", 1, ByteArray(0)) is DecodeResult.Corrupt)
        for (bytes in good) {
            // Every truncation.
            for (len in 0 until bytes.size) {
                val r = InkCodec.decode("a", 1, bytes.copyOf(len))
                assertTrue("len $len gave $r", r !is DecodeResult.Ok)
            }
            // Random corruption: may decode to something or fail, but must never throw.
            repeat(50) {
                val bad = bytes.copyOf()
                bad[rng.nextInt(bad.size)] = rng.nextInt(256).toByte()
                InkCodec.decode("a", 1, bad)
            }
            // Trailing garbage.
            assertTrue(InkCodec.decode("a", 1, bytes + 7) is DecodeResult.Corrupt)
        }
    }

    @Test
    fun `finger strokes without pressure stay without pressure`() {
        val item = TestInk.stroke(TestInk.points(0 to 0, 3 to 4, 6 to 8))
        val back = roundTrip(item) as StrokeItem
        assertEquals(null, back.points.pressure)
        assertEquals(item, back)
    }
}
