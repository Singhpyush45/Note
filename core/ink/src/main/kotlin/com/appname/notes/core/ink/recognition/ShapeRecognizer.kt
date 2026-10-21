package com.appname.notes.core.ink.recognition

import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.geometry.bounds
import com.appname.notes.core.ink.geometry.closedDouglasPeucker
import com.appname.notes.core.ink.geometry.convexHull
import com.appname.notes.core.ink.geometry.dist
import com.appname.notes.core.ink.geometry.distanceToPolyline
import com.appname.notes.core.ink.geometry.douglasPeucker
import com.appname.notes.core.ink.geometry.interiorAngles
import com.appname.notes.core.ink.geometry.lineIntersection
import com.appname.notes.core.ink.geometry.mergeShortEdges
import com.appname.notes.core.ink.geometry.pathLength
import com.appname.notes.core.ink.geometry.principalAxis
import com.appname.notes.core.ink.geometry.rectOrientation
import com.appname.notes.core.ink.geometry.resample
import com.appname.notes.core.ink.geometry.rotate
import com.appname.notes.core.ink.geometry.segmentDistance
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.model.StrokePoints
import com.appname.notes.core.ink.model.toPt
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A recognised shape: what it is, how sure we are (0..1) and its cleaned-up geometry. */
data class Recognition(val kind: ShapeKind, val confidence: Float, val geometry: ShapeGeometry)

/**
 * Draw-and-hold shape recognition (SRS 6.2). Pure geometry, no machine learning, so it is predictable and
 * fully unit-tested on a generated corpus of shapes and handwriting.
 *
 * Rule of thumb: when unsure, return null and keep the user's ink. Turning handwriting into a shape is far
 * worse than missing a shape (the user can simply redraw).
 *
 * Steps:
 * 1. Clean up: drop repeated points; ignore tiny strokes.
 * 2. Resample to 64 evenly spaced points so speed and sensor rate don't matter.
 * 3. Try, in order: line → arrow → closed shapes (triangle, rectangle/square, circle/ellipse).
 *
 * [unitsPerDp] converts the size limits from screen dp to page units (pass 1/zoom), so recognition behaves
 * the same at any zoom.
 */
object ShapeRecognizer {

    private const val SAMPLES = 64

    /** Strokes with a smaller bounding-box diagonal (dp) are never shapes (dots, commas, i-dots). */
    const val MIN_SIZE_DP = 12.0

    /** Line test samples at least this far apart (dp), so ~1 px of sensor noise can't look like bending. */
    const val LINE_SPACING_DP = 10.0

    fun recognize(points: StrokePoints, unitsPerDp: Double = 1.0): Recognition? =
        recognize(points.toVecs(), unitsPerDp)

    fun recognize(input: List<Vec>, unitsPerDp: Double = 1.0): Recognition? {
        if (input.isEmpty()) return null
        val pts = ArrayList<Vec>(input.size)
        pts.add(input[0])
        for (q in input.subList(1, input.size)) if (dist(q, pts.last()) > 1e-6) pts.add(q)
        if (pts.size < 4) return null
        val box = bounds(pts)
        val d = box.diagonal
        if (d < MIN_SIZE_DP * unitsPerDp) return null

        val r = resample(pts, SAMPLES)
        // Measured on the resampled path: sensor jitter between samples doesn't inflate it.
        val length = pathLength(r)
        val endGap = dist(r[0], r.last())

        line(pts, r, length, unitsPerDp)?.let { return it }
        arrow(pts, r, d)?.let { return it }
        if (endGap > 0.25 * d) return null
        return closedShape(r, d)
    }

    // ------------------------------------------------------------------ line

    private fun line(pts: List<Vec>, r: List<Vec>, length: Double, unitsPerDp: Double): Recognition? {
        val nl = (length / (LINE_SPACING_DP * unitsPerDp)).toInt().plus(1).coerceIn(12, SAMPLES)
        val rl = if (nl == SAMPLES) r else resample(pts, nl)
        // Ignore the ends: pens curl a little when landing and lifting.
        val m = max(1, (nl * 4.0 / SAMPLES).roundToInt())
        val core = rl.subList(m, rl.size - m)
        val (ang, c) = principalAxis(core)
        val dir = Vec(cos(ang), sin(ang))
        val devs = core.map { (it.x - c.x) * -dir.y + (it.y - c.y) * dir.x }
        val maxDev = devs.maxOf { abs(it) }
        val coreLength = pathLength(core)
        if (coreLength == 0.0) return null
        val straight = dist(core[0], core.last()) / coreLength
        // A zigzag also hugs its centre line, but keeps crossing it with real amplitude.
        var crossings = 0
        var side = 0
        for (dv in devs) {
            if (abs(dv) < 0.012 * length) continue
            val sd = if (dv > 0) 1 else -1
            if (side != 0 && sd != side) crossings++
            side = sd
        }
        if (!(straight > 0.88 && maxDev / length < 0.07 && crossings < 4)) return null
        val confidence = min(1.0, 1 - (maxDev / length) / 0.07) * min(1.0, (straight - 0.88) / 0.07)

        // Geometry: the fitted line, from the first to the last projection of the drawn points.
        val proj = rl.map { (it - c).dot(dir) }
        var start = c + dir * proj.min()
        var end = c + dir * proj.max()
        // Keep the drawing direction (start where the pen started).
        if ((rl.last() - rl.first()).dot(dir) < 0) start = end.also { end = start }
        val (s, e) = snapSegment(start, end, pivotAtStart = false)
        return Recognition(ShapeKind.LINE, confidence.toFloat(), ShapeGeometry.Segment(s.toPt(), e.toPt()))
    }

    /** Snaps a segment's angle to the nearest multiple of 45° when within [LINE_SNAP_DEG]. */
    private fun snapSegment(start: Vec, end: Vec, pivotAtStart: Boolean): Pair<Vec, Vec> {
        val v = end - start
        val angle = Math.toDegrees(atan2(v.y, v.x))
        val target = Math.round(angle / 45.0) * 45.0
        val delta = target - angle
        if (abs(delta) > LINE_SNAP_DEG) return start to end
        val rad = Math.toRadians(delta)
        return if (pivotAtStart) {
            start to rotate(end, rad, start)
        } else {
            val mid = Vec((start.x + end.x) / 2, (start.y + end.y) / 2)
            rotate(start, rad, mid) to rotate(end, rad, mid)
        }
    }

    const val LINE_SNAP_DEG = 4.0
    const val RECT_SNAP_DEG = 7.0
    const val ELLIPSE_SNAP_DEG = 8.0

    // ------------------------------------------------------------------ arrow (one stroke: shaft, then head)

    private fun arrow(pts: List<Vec>, r: List<Vec>, d: Double): Recognition? {
        val v = douglasPeucker(r, 0.05 * d)
        if (v.size < 3 || v.size > 6) return null
        val shaft = dist(v[0], v[1])
        val length = pathLength(r)
        if (shaft < 0.5 * length) return null
        val tip = v[1]
        val back = v[0] - tip
        val bl = back.length
        val barbSides = ArrayList<Double>()
        val barbEnds = ArrayList<Vec>()
        for (q in v.subList(2, v.size)) {
            val dq = dist(q, tip)
            if (dq < 0.08 * shaft) continue // came back to the tip
            if (dq > 0.55 * shaft) return null
            val vec = q - tip
            val a = Math.toDegrees(acos((vec.dot(back) / (dq * bl)).coerceIn(-1.0, 1.0)))
            if (a < 12 || a > 75) return null
            barbSides.add(back.cross(vec))
            barbEnds.add(q)
        }
        if (barbSides.isEmpty() || barbSides.size > 2) return null
        if (barbSides.size == 2 && barbSides[0] * barbSides[1] > 0) return null // both barbs on one side
        if (length - shaft > 1.6 * shaft) return null
        // The real tip is the drawn point furthest along the shaft (samples can fall just short of it).
        val dir = (tip - v[0]) * (1 / shaft)
        val exactTip = pts.maxBy { (it - v[0]).dot(dir) }
        val (s, e) = snapSegment(v[0], exactTip, pivotAtStart = false)
        // Head size: from the tip to the furthest drawn point near each sampled barb end.
        val head = barbEnds.map { q ->
            val dq = dist(q, tip)
            pts.filter { dist(it, q) < 0.3 * dq }.maxOfOrNull { dist(it, exactTip) } ?: dq
        }.average().coerceIn(0.08 * shaft, 0.5 * shaft)
        return Recognition(ShapeKind.ARROW, 0.8f, ShapeGeometry.Segment(s.toPt(), e.toPt(), head.toFloat()))
    }

    // ------------------------------------------------------------------ closed shapes

    private fun closedShape(r: List<Vec>, d: Double): Recognition? {
        val hull = convexHull(r)
        if (hull.size < 3) return null
        // Points far inside the hull (figure-eights, letters) mean it isn't a simple outline.
        var inner = 0
        for (q in r) if (distanceToPolyline(q, hull, closed = true) > 0.12 * d) inner++
        if (inner > 3) return null

        // Ellipse fit: principal axes, then extents in the rotated frame.
        val (ang, c) = principalAxis(r)
        val rot = r.map { rotate(it, -ang, c) }
        val rb = bounds(rot)
        val rx = rb.width / 2
        val ry = rb.height / 2
        if (rx < 1e-6 || ry < 1e-6) return null
        val ec = rb.center
        val ellErr = rot.sumOf { abs(hypot((it.x - ec.x) / rx, (it.y - ec.y) / ry) - 1) } / rot.size

        val rough = mergeShortEdges(closedDouglasPeucker(hull, 0.06 * d), 0.12 * d)
        val poly = if (rough.size in 3..4) refineCorners(rough, r) else rough
        val k = poly.size
        val polyErr = r.sumOf { distanceToPolyline(it, poly, closed = true) } / r.size / d

        if ((k == 3 || k == 4) && polyErr < 0.035 && polyErr < ellErr * 0.9) {
            val confidence = (1 - polyErr / 0.035).toFloat()
            val angles = interiorAngles(poly)
            if (k == 3) {
                if (angles.min() < 15) return null
                return Recognition(ShapeKind.TRIANGLE, confidence, ShapeGeometry.Polygon(poly.map { it.toPt() }))
            }
            if (angles.any { abs(it - 90) >= 22 }) return null
            return rectangle(poly, confidence)
        }
        if (ellErr < 0.075) {
            val confidence = (1 - ellErr / 0.075).toFloat()
            val center = rotate(ec, ang, c)
            val ratio = min(rx, ry) / max(rx, ry)
            if (ratio > 0.82) {
                val radius = r.sumOf { dist(it, center) } / r.size
                return Recognition(
                    ShapeKind.CIRCLE,
                    confidence,
                    ShapeGeometry.Ellipse(center.toPt(), radius.toFloat(), radius.toFloat(), 0f),
                )
            }
            // Normalise: rx is the long axis, rotation in [0, 180).
            var deg = Math.toDegrees(ang)
            var a = rx
            var b = ry
            if (b > a) { a = ry; b = rx; deg += 90.0 }
            deg = ((deg % 180.0) + 180.0) % 180.0
            deg = when {
                deg < ELLIPSE_SNAP_DEG || deg > 180 - ELLIPSE_SNAP_DEG -> 0.0
                abs(deg - 90) < ELLIPSE_SNAP_DEG -> 90.0
                else -> deg
            }
            return Recognition(
                ShapeKind.ELLIPSE,
                confidence,
                ShapeGeometry.Ellipse(center.toPt(), a.toFloat(), b.toFloat(), deg.toFloat()),
            )
        }
        return null
    }

    /**
     * Corners from the simplified outline sit on samples, which can fall a little before the real corner.
     * Fit a straight line through each side's points (ignoring the parts near its corners) and use the
     * intersections of neighbouring sides as the corners. Falls back to the rough corner when a side has
     * too few points or the fit moves it implausibly far.
     */
    private fun refineCorners(poly: List<Vec>, r: List<Vec>): List<Vec> {
        val k = poly.size
        val sides = List(k) { ArrayList<Vec>() }
        for (q in r) {
            var best = Double.POSITIVE_INFINITY
            var bi = 0
            for (i in 0 until k) {
                val dd = segmentDistance(q, poly[i], poly[(i + 1) % k])
                if (dd < best) { best = dd; bi = i }
            }
            val a = poly[bi]
            val b = poly[(bi + 1) % k]
            val margin = 0.15 * dist(a, b)
            if (dist(q, a) > margin && dist(q, b) > margin) sides[bi].add(q)
        }
        val lines = sides.mapIndexed { i, pts ->
            if (pts.size < 2) {
                poly[i] to poly[(i + 1) % k]
            } else {
                val (ang, c) = principalAxis(pts)
                c to Vec(c.x + cos(ang), c.y + sin(ang))
            }
        }
        return List(k) { i ->
            val prev = lines[Math.floorMod(i - 1, k)]
            val next = lines[i]
            val p = lineIntersection(prev.first, prev.second, next.first, next.second)
            val limit = 0.25 * min(dist(poly[i], poly[Math.floorMod(i - 1, k)]), dist(poly[i], poly[(i + 1) % k]))
            if (p == null || dist(p, poly[i]) > limit) poly[i] else p
        }
    }

    private fun rectangle(poly: List<Vec>, confidence: Float): Recognition {
        var theta = rectOrientation(poly) // (−45°, 45°]
        if (abs(Math.toDegrees(theta)) < RECT_SNAP_DEG) theta = 0.0
        val local = poly.map { rotate(it, -theta) }
        val b = bounds(local)
        val w = b.width
        val h = b.height
        val square = abs(w - h) / max(w, h) < 0.15
        val (hw, hh) = if (square) ((w + h) / 4).let { it to it } else (w / 2 to h / 2)
        val cc = b.center
        val corners = listOf(
            Vec(cc.x - hw, cc.y - hh),
            Vec(cc.x + hw, cc.y - hh),
            Vec(cc.x + hw, cc.y + hh),
            Vec(cc.x - hw, cc.y + hh),
        ).map { rotate(it, theta).toPt() }
        return Recognition(
            if (square) ShapeKind.SQUARE else ShapeKind.RECTANGLE,
            confidence,
            ShapeGeometry.Polygon(corners),
        )
    }
}
