package com.appname.notes.core.ink.geometry

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A 2-D point or vector in page units, in double precision for calculations.
 * (Stored ink uses floats; see the model package.) Like a small `struct Vec { double x, y; }` with
 * operator overloads in C++.
 */
data class Vec(val x: Double, val y: Double) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec(x * k, y * k)
    fun dot(o: Vec) = x * o.x + y * o.y
    fun cross(o: Vec) = x * o.y - y * o.x
    val length: Double get() = hypot(x, y)
}

/** Axis-aligned rectangle. */
data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val diagonal: Double get() = hypot(width, height)
    val center: Vec get() = Vec((left + right) / 2, (top + bottom) / 2)

    fun intersects(o: Rect) = left <= o.right && o.left <= right && top <= o.bottom && o.top <= bottom
    fun inflate(d: Double) = Rect(left - d, top - d, right + d, bottom + d)
    fun union(o: Rect) = Rect(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))
    fun contains(p: Vec) = p.x in left..right && p.y in top..bottom
}

fun dist(a: Vec, b: Vec): Double = hypot(a.x - b.x, a.y - b.y)

fun pathLength(p: List<Vec>): Double {
    var s = 0.0
    for (i in 0 until p.size - 1) s += dist(p[i], p[i + 1])
    return s
}

fun bounds(p: List<Vec>): Rect {
    var x0 = Double.POSITIVE_INFINITY
    var y0 = Double.POSITIVE_INFINITY
    var x1 = Double.NEGATIVE_INFINITY
    var y1 = Double.NEGATIVE_INFINITY
    for (q in p) {
        x0 = min(x0, q.x); y0 = min(y0, q.y); x1 = max(x1, q.x); y1 = max(y1, q.y)
    }
    return Rect(x0, y0, x1, y1)
}

/** Points [n] evenly spaced along the path (by arc length). First and last point are kept. */
fun resample(p: List<Vec>, n: Int): List<Vec> {
    require(p.isNotEmpty() && n >= 2)
    val total = pathLength(p)
    if (total == 0.0) return List(n) { p[0] }
    val step = total / (n - 1)
    val out = ArrayList<Vec>(n)
    out.add(p[0])
    var acc = 0.0
    var i = 1
    var prev = p[0]
    while (i < p.size && out.size < n) {
        val d = dist(prev, p[i])
        if (acc + d >= step && d > 0) {
            val t = (step - acc) / d
            val q = Vec(prev.x + t * (p[i].x - prev.x), prev.y + t * (p[i].y - prev.y))
            out.add(q); prev = q; acc = 0.0
        } else {
            acc += d; prev = p[i]; i++
        }
    }
    while (out.size < n) out.add(p.last())
    return out
}

/** Distance from point [p] to segment [a]–[b]. */
fun segmentDistance(p: Vec, a: Vec, b: Vec): Double {
    val vx = b.x - a.x
    val vy = b.y - a.y
    val wx = p.x - a.x
    val wy = p.y - a.y
    val l2 = vx * vx + vy * vy
    if (l2 == 0.0) return dist(p, a)
    val t = ((wx * vx + wy * vy) / l2).coerceIn(0.0, 1.0)
    return hypot(wx - t * vx, wy - t * vy)
}

/** Shortest distance between segments a–b and c–d (0 when they cross). */
fun segmentSegmentDistance(a: Vec, b: Vec, c: Vec, d: Vec): Double {
    if (segmentsCross(a, b, c, d)) return 0.0
    return min(
        min(segmentDistance(a, c, d), segmentDistance(b, c, d)),
        min(segmentDistance(c, a, b), segmentDistance(d, a, b)),
    )
}

private fun segmentsCross(a: Vec, b: Vec, c: Vec, d: Vec): Boolean {
    val d1 = (b - a).cross(c - a)
    val d2 = (b - a).cross(d - a)
    val d3 = (d - c).cross(a - c)
    val d4 = (d - c).cross(b - c)
    return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
}

/** Distance from [p] to the closest edge of a polyline (closed = also the edge last→first). */
fun distanceToPolyline(p: Vec, poly: List<Vec>, closed: Boolean): Double {
    if (poly.size == 1) return dist(p, poly[0])
    var best = Double.POSITIVE_INFINITY
    val edges = if (closed) poly.size else poly.size - 1
    for (i in 0 until edges) best = min(best, segmentDistance(p, poly[i], poly[(i + 1) % poly.size]))
    return best
}

/** Douglas–Peucker simplification: keeps the points that matter for the outline within [eps]. */
fun douglasPeucker(p: List<Vec>, eps: Double): List<Vec> {
    if (p.size < 3) return p.toList()
    val keep = BooleanArray(p.size)
    keep[0] = true; keep[p.size - 1] = true
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to p.size - 1)
    while (stack.isNotEmpty()) {
        val (s, e) = stack.removeLast()
        var best = -1.0
        var bi = -1
        for (i in s + 1 until e) {
            val d = segmentDistance(p[i], p[s], p[e])
            if (d > best) { best = d; bi = i }
        }
        if (best > eps) {
            keep[bi] = true
            stack.addLast(s to bi); stack.addLast(bi to e)
        }
    }
    return p.filterIndexed { i, _ -> keep[i] }
}

/** Convex hull (Andrew's monotone chain), counter-clockwise in a y-up frame, no repeated first point. */
fun convexHull(points: List<Vec>): List<Vec> {
    val pts = points.distinct().sortedWith(compareBy<Vec>({ it.x }, { it.y }))
    if (pts.size < 3) return pts
    fun cross(o: Vec, a: Vec, b: Vec) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val lower = ArrayList<Vec>()
    for (q in pts) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], q) <= 0) lower.removeAt(lower.size - 1)
        lower.add(q)
    }
    val upper = ArrayList<Vec>()
    for (q in pts.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], q) <= 0) upper.removeAt(upper.size - 1)
        upper.add(q)
    }
    return lower.subList(0, lower.size - 1) + upper.subList(0, upper.size - 1)
}

/** Douglas–Peucker on a closed polygon: split at two far-apart vertices and simplify both halves. */
fun closedDouglasPeucker(poly: List<Vec>, eps: Double): List<Vec> {
    val n = poly.size
    if (n <= 3) return poly.toList()
    var j0 = 0
    var best = -1.0
    for (j in 0 until n) {
        val d = dist(poly[0], poly[j])
        if (d > best) { best = d; j0 = j }
    }
    var i0 = 0
    best = -1.0
    for (i in 0 until n) {
        val d = dist(poly[j0], poly[i])
        if (d > best) { best = d; i0 = i }
    }
    val seq = poly.subList(i0, n) + poly.subList(0, i0)
    val k = Math.floorMod(j0 - i0, n)
    val a = douglasPeucker(seq.subList(0, k + 1), eps)
    val b = douglasPeucker(seq.subList(k, n) + seq[0], eps)
    return a.dropLast(1) + b.dropLast(1)
}

/** Intersection of the infinite lines a–b and c–d, or null when (nearly) parallel. */
fun lineIntersection(a: Vec, b: Vec, c: Vec, d: Vec): Vec? {
    val den = (a.x - b.x) * (c.y - d.y) - (a.y - b.y) * (c.x - d.x)
    if (abs(den) < 1e-9) return null
    val t = ((a.x - c.x) * (c.y - d.y) - (a.y - c.y) * (c.x - d.x)) / den
    return Vec(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
}

/**
 * A rounded or cut corner shows up as two corners joined by a short edge: merge such pairs into one corner
 * (where the neighbouring edges meet), until no edge is shorter than [minLength] or 3 corners remain.
 */
fun mergeShortEdges(polygon: List<Vec>, minLength: Double): List<Vec> {
    val poly = polygon.toMutableList()
    while (poly.size > 3) {
        val k = poly.size
        var bestLen = Double.POSITIVE_INFINITY
        var bi = -1
        for (i in 0 until k) {
            val l = dist(poly[i], poly[(i + 1) % k])
            if (l < minLength && l < bestLen) { bestLen = l; bi = i }
        }
        if (bi < 0) break
        val j = (bi + 1) % k
        val mid = Vec((poly[bi].x + poly[j].x) / 2, (poly[bi].y + poly[j].y) / 2)
        var p = lineIntersection(poly[Math.floorMod(bi - 1, k)], poly[bi], poly[j], poly[(j + 1) % k])
        if (p == null || dist(p, mid) > 2 * minLength) p = mid
        poly[bi] = p
        poly.removeAt(j)
    }
    return poly
}

fun polygonArea(poly: List<Vec>): Double {
    var s = 0.0
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        s += a.x * b.y - b.x * a.y
    }
    return abs(s) / 2
}

/** Interior angle at every vertex, in degrees. */
fun interiorAngles(poly: List<Vec>): List<Double> {
    val k = poly.size
    return List(k) { i ->
        val a = poly[Math.floorMod(i - 1, k)]
        val b = poly[i]
        val c = poly[(i + 1) % k]
        val v1 = a - b
        val v2 = c - b
        val n1 = v1.length
        val n2 = v2.length
        if (n1 == 0.0 || n2 == 0.0) 0.0 else Math.toDegrees(acos((v1.dot(v2) / (n1 * n2)).coerceIn(-1.0, 1.0)))
    }
}

/**
 * Principal axis of a point cloud: direction of greatest spread (PCA), in radians, and the centroid.
 * Uses the closed form for 2×2 covariance: angle = ½·atan2(2·sxy, sxx − syy).
 */
fun principalAxis(p: List<Vec>): Pair<Double, Vec> {
    var cx = 0.0
    var cy = 0.0
    for (q in p) { cx += q.x; cy += q.y }
    cx /= p.size; cy /= p.size
    var sxx = 0.0
    var syy = 0.0
    var sxy = 0.0
    for (q in p) {
        val dx = q.x - cx
        val dy = q.y - cy
        sxx += dx * dx; syy += dy * dy; sxy += dx * dy
    }
    return 0.5 * atan2(2 * sxy, sxx - syy) to Vec(cx, cy)
}

/** Rotates [q] by [angle] radians around [c]. */
fun rotate(q: Vec, angle: Double, c: Vec = Vec(0.0, 0.0)): Vec {
    val s = sin(angle)
    val co = cos(angle)
    val x = q.x - c.x
    val y = q.y - c.y
    return Vec(c.x + x * co - y * s, c.y + x * s + y * co)
}

/**
 * Orientation of a roughly rectangular polygon, in radians within (−45°, 45°]: the length-weighted average
 * edge direction, computed on angles ×4 so that edges 90° apart count as the same direction.
 */
fun rectOrientation(poly: List<Vec>): Double {
    var sx = 0.0
    var sy = 0.0
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        val t = atan2(b.y - a.y, b.x - a.x) * 4
        val ln = dist(a, b)
        sx += ln * cos(t); sy += ln * sin(t)
    }
    return atan2(sy, sx) / 4
}

/** Even–odd rule point-in-polygon test. */
fun pointInPolygon(p: Vec, poly: List<Vec>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/** Points along an ellipse outline (for hit testing and lasso). */
fun ellipseOutline(center: Vec, rx: Double, ry: Double, rotationDeg: Double, n: Int = 64): List<Vec> {
    val rot = Math.toRadians(rotationDeg)
    return List(n) { i ->
        val t = 2 * Math.PI * i / n
        rotate(Vec(center.x + rx * cos(t), center.y + ry * sin(t)), rot, center)
    }
}

/** Normalises an angle in degrees to [0, 360). */
fun normalizeDegrees(a: Double): Double {
    val r = a % 360.0
    return if (r < 0) r + 360.0 else r
}
