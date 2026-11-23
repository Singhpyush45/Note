package com.appname.notes.core.ink.recognition

import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.geometry.dist
import com.appname.notes.core.ink.geometry.rotate
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ShapeRecognizerTest {

    private val perType = 400

    /** Recognition rate per shape kind, and false positives per handwriting kind, for one corpus. */
    private fun measure(seed: Long, noise: Double): Pair<Map<ShapeKind, Double>, Map<String, Int>> {
        val corpus = ShapeCorpus(seed)
        val hits = corpus.shapes.mapValues { (kind, gen) ->
            val ok = (1..perType).count {
                val pts = gen().let { p -> if (noise > 0) corpus.noisy(p, noise) else p }
                ShapeRecognizer.recognize(pts)?.kind == kind
            }
            ok.toDouble() / perType
        }
        val falsePositives = corpus.handwriting.mapValues { (_, gen) ->
            (1..perType).count {
                val pts = gen().let { p -> if (noise > 0) corpus.noisy(p, noise) else p }
                ShapeRecognizer.recognize(pts) != null
            }
        }
        return hits to falsePositives
    }

    private fun assertCorpus(seed: Long, noise: Double, minRate: Double) {
        val (hits, fp) = measure(seed, noise)
        val report = "seed=$seed noise=$noise hits=$hits falsePositives=$fp"
        hits.forEach { (kind, rate) -> assertTrue("$kind recognised ${rate * 100}% < ${minRate * 100}%: $report", rate >= minRate) }
        // Hard rule: handwriting is never turned into a shape.
        fp.forEach { (kind, n) -> assertEquals("handwriting '$kind' converted: $report", 0, n) }
    }

    @Test
    fun `recognises hand-drawn shapes and never converts handwriting`() = assertCorpus(seed = 42, noise = 0.0, minRate = 0.97)

    @Test
    fun `same on a second independent corpus`() = assertCorpus(seed = 2026, noise = 0.0, minRate = 0.97)

    @Test
    fun `robust to one dp of extra sensor noise`() = assertCorpus(seed = 7, noise = 1.0, minRate = 0.95)

    @Test
    fun `tiny strokes, dots and taps stay ink`() {
        assertNull(ShapeRecognizer.recognize(emptyList()))
        assertNull(ShapeRecognizer.recognize(listOf(Vec(5.0, 5.0))))
        assertNull(ShapeRecognizer.recognize(List(20) { Vec(5.0, 5.0) }))
        // A 6 dp dash (a hyphen or i-dot) is below the size limit.
        assertNull(ShapeRecognizer.recognize(List(10) { Vec(it * 0.6, 0.0) }))
    }

    @Test
    fun `size limit follows zoom`() {
        val dash = List(20) { Vec(it * 1.0, 0.0) } // 19 units long
        assertEquals(ShapeKind.LINE, ShapeRecognizer.recognize(dash, unitsPerDp = 1.0)?.kind)
        // Zoomed out 2×: 19 units are only 9.5 dp on screen, below the 12 dp limit.
        assertNull(ShapeRecognizer.recognize(dash, unitsPerDp = 2.0))
    }

    // ------------------------------------------------------------------ cleaned-up geometry

    private fun straight(a: Vec, b: Vec, n: Int = 40) = List(n) { i ->
        val t = i.toDouble() / (n - 1)
        Vec(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    private fun segment(points: List<Vec>): ShapeGeometry.Segment =
        ShapeRecognizer.recognize(points)!!.geometry as ShapeGeometry.Segment

    private fun angleDeg(g: ShapeGeometry.Segment) =
        Math.toDegrees(atan2((g.end.y - g.start.y).toDouble(), (g.end.x - g.start.x).toDouble()))

    @Test
    fun `nearly horizontal and diagonal lines snap, others keep their angle`() {
        val c = Vec(200.0, 200.0)
        fun lineAt(deg: Double): ShapeGeometry.Segment {
            val r = Math.toRadians(deg)
            return segment(straight(Vec(c.x - 100 * cos(r), c.y - 100 * sin(r)), Vec(c.x + 100 * cos(r), c.y + 100 * sin(r))))
        }
        assertEquals(0.0, angleDeg(lineAt(3.0)), 0.01)
        assertEquals(45.0, angleDeg(lineAt(42.0)), 0.01)
        assertEquals(-90.0, angleDeg(lineAt(-87.0)), 0.01)
        assertEquals(20.0, angleDeg(lineAt(20.0)), 0.01)
        // Length is kept when snapping.
        val g = lineAt(3.0)
        assertEquals(200.0, dist(g.start.toVec(), g.end.toVec()), 0.5)
    }

    @Test
    fun `line keeps drawing direction`() {
        val g = segment(straight(Vec(300.0, 100.0), Vec(100.0, 100.0)))
        assertEquals(300f, g.start.x, 0.5f)
        assertEquals(100f, g.end.x, 0.5f)
    }

    @Test
    fun `slightly tilted rectangle is squared up, clearly tilted one keeps its tilt`() {
        fun rectAt(deg: Double): List<Vec> {
            val c = Vec(300.0, 300.0)
            val corners = listOf(Vec(150.0, 220.0), Vec(450.0, 220.0), Vec(450.0, 380.0), Vec(150.0, 380.0), Vec(150.0, 220.0))
            return (0 until 4).flatMap { straight(corners[it], corners[it + 1], 30).dropLast(1) }
                .plus(corners[0]).map { rotate(it, Math.toRadians(deg), c) }
        }
        val snapped = ShapeRecognizer.recognize(rectAt(5.0))!!
        assertEquals(ShapeKind.RECTANGLE, snapped.kind)
        val v = (snapped.geometry as ShapeGeometry.Polygon).vertices
        // Axis-aligned: every edge horizontal or vertical.
        for (i in 0 until 4) {
            val a = v[i]
            val b = v[(i + 1) % 4]
            assertTrue("edge $i not axis-aligned", kotlin.math.abs(a.x - b.x) < 0.01f || kotlin.math.abs(a.y - b.y) < 0.01f)
        }
        val tilted = ShapeRecognizer.recognize(rectAt(20.0))!!.geometry as ShapeGeometry.Polygon
        val e = tilted.vertices
        val edgeDeg = Math.toDegrees(atan2((e[1].y - e[0].y).toDouble(), (e[1].x - e[0].x).toDouble()))
        val mod90 = ((edgeDeg % 90) + 90) % 90
        assertEquals(20.0, mod90, 1.0)
    }

    @Test
    fun `square gets equal sides and right angles`() {
        val corners = listOf(Vec(100.0, 100.0), Vec(306.0, 100.0), Vec(306.0, 300.0), Vec(100.0, 300.0), Vec(100.0, 100.0))
        val pts = (0 until 4).flatMap { straight(corners[it], corners[it + 1], 30).dropLast(1) } + corners[0]
        val r = ShapeRecognizer.recognize(pts)!!
        assertEquals(ShapeKind.SQUARE, r.kind)
        val v = (r.geometry as ShapeGeometry.Polygon).vertices.map { it.toVec() }
        val sides = (0 until 4).map { dist(v[it], v[(it + 1) % 4]) }
        sides.forEach { assertEquals(203.0, it, 0.5) }
    }

    @Test
    fun `circle gets the mean radius and its centre`() {
        val c = Vec(250.0, 180.0)
        val pts = List(120) { i ->
            val t = 2 * PI * i / 110 // a little overshoot past the start
            Vec(c.x + 80 * cos(t) * (1 + 0.02 * sin(5 * t)), c.y + 80 * sin(t) * (1 + 0.02 * sin(5 * t)))
        }
        val r = ShapeRecognizer.recognize(pts)!!
        assertEquals(ShapeKind.CIRCLE, r.kind)
        val g = r.geometry as ShapeGeometry.Ellipse
        assertEquals(80f, g.rx, 2.4f) // within 3 %
        assertEquals(g.rx, g.ry, 0f)
        assertEquals(c.x, g.center.x.toDouble(), 2.0)
        assertEquals(c.y, g.center.y.toDouble(), 2.0)
    }

    @Test
    fun `ellipse rotation snaps to horizontal when close, rx is the long axis`() {
        fun ellipseAt(deg: Double): ShapeGeometry.Ellipse {
            val c = Vec(300.0, 300.0)
            val pts = List(100) { i ->
                val t = 2 * PI * i / 99
                rotate(Vec(c.x + 150 * cos(t), c.y + 70 * sin(t)), Math.toRadians(deg), c)
            }
            val r = ShapeRecognizer.recognize(pts)!!
            assertEquals(ShapeKind.ELLIPSE, r.kind)
            return r.geometry as ShapeGeometry.Ellipse
        }
        val near = ellipseAt(5.0)
        assertEquals(0f, near.rotationDeg, 0f)
        assertTrue(near.rx > near.ry)
        assertEquals(150f, near.rx, 3f)
        assertEquals(70f, near.ry, 3f)
        assertEquals(30f, ellipseAt(30.0).rotationDeg, 1f)
        assertEquals(90f, ellipseAt(93.0).rotationDeg, 0f)
    }

    @Test
    fun `arrow points at the end of the shaft and has a head`() {
        val tail = Vec(100.0, 300.0)
        val tip = Vec(400.0, 300.0)
        val barb1 = Vec(tip.x - 60 * cos(0.5), tip.y - 60 * sin(0.5))
        val barb2 = Vec(tip.x - 60 * cos(0.5), tip.y + 60 * sin(0.5))
        val pts = straight(tail, tip, 50) + straight(tip, barb1, 10).drop(1) + straight(barb1, tip, 10).drop(1) +
            straight(tip, barb2, 10).drop(1)
        val r = ShapeRecognizer.recognize(pts)!!
        assertEquals(ShapeKind.ARROW, r.kind)
        val g = r.geometry as ShapeGeometry.Segment
        assertEquals(tip.x, g.end.x.toDouble(), 2.0)
        assertEquals(tail.x, g.start.x.toDouble(), 2.0)
        assertEquals(60f, g.arrowHead, 3f)
    }

    @Test
    fun `triangle keeps its corners`() {
        val a = Vec(100.0, 400.0)
        val b = Vec(300.0, 80.0)
        val c = Vec(480.0, 390.0)
        val pts = straight(a, b) + straight(b, c).drop(1) + straight(c, a).drop(1)
        val r = ShapeRecognizer.recognize(pts)!!
        assertEquals(ShapeKind.TRIANGLE, r.kind)
        val v = (r.geometry as ShapeGeometry.Polygon).vertices.map { it.toVec() }
        for (corner in listOf(a, b, c)) {
            val nearest = v.minOf { hypot(it.x - corner.x, it.y - corner.y) }
            assertTrue("corner $corner moved by $nearest", nearest < 3.0)
        }
    }

    @Test
    fun `confidence is between 0 and 1 and geometry matches kind`() {
        val corpus = ShapeCorpus(11)
        for ((_, gen) in corpus.shapes) repeat(50) {
            val r = ShapeRecognizer.recognize(gen()) ?: return@repeat
            assertTrue(r.confidence in 0f..1f)
            assertTrue(r.kind.accepts(r.geometry))
        }
    }
}
