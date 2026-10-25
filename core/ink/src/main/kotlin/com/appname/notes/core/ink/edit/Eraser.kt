package com.appname.notes.core.ink.edit

import com.appname.notes.core.ink.model.TextItem
import com.appname.notes.core.ink.model.ImageItem
import com.appname.notes.core.ink.geometry.Rect
import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.geometry.bounds
import com.appname.notes.core.ink.geometry.dist
import com.appname.notes.core.ink.geometry.distanceToPolyline
import com.appname.notes.core.ink.geometry.segmentSegmentDistance
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import kotlin.math.ceil
import kotlin.math.max

/**
 * Erasing (SRS 6.2). The eraser is a circle of [radius] page units swept along [path] (the pen's samples
 * since the last update), i.e. a chain of capsules.
 *
 * Ink counts as touched when the eraser reaches its painted edge (centre line + half the line width).
 */
object Eraser {

    /** Whole-object eraser: every item the swept eraser touches. */
    fun touched(items: List<InkItem>, path: List<Vec>, radius: Double): List<InkItem> {
        if (path.isEmpty()) return emptyList()
        val area = bounds(path).inflate(radius)
        return items.filter { it.bounds().intersects(area) && touches(it, path, radius) }
    }

    fun touches(item: InkItem, path: List<Vec>, radius: Double): Boolean {
        val reach = radius + item.brush.size / 2.0
        val outline = when (item) {
            is StrokeItem -> item.points.toVecs()
            is ShapeItem -> item.geometry.outline()
            // Photos and text boxes aren't ink: the eraser leaves them (remove them with the lasso).
            is ImageItem, is TextItem -> return false
        }
        return polylinesWithin(outline, path, reach)
    }

    /**
     * Partial (stroke) eraser: cuts away the parts of strokes under the eraser, keeping the rest as new
     * strokes with the same brush and depth. Shapes are vector objects and are erased whole.
     * Returns the edit to apply, or null when nothing is touched.
     */
    fun erasePartially(
        items: List<InkItem>,
        path: List<Vec>,
        radius: Double,
        newId: () -> String,
    ): InkEdit? {
        val removed = ArrayList<InkItem>()
        val added = ArrayList<InkItem>()
        for (item in touched(items, path, radius)) {
            removed.add(item)
            if (item is StrokeItem) {
                val reach = radius + item.brush.size / 2.0
                for (piece in cut(item.points, path, reach, step = max(radius / 3, 0.5))) {
                    added.add(StrokeItem(newId(), item.z, item.brush, piece))
                }
            }
        }
        return if (removed.isEmpty()) null else InkEdit(removed, added)
    }

    /**
     * Splits [points] where they come within [reach] of [path]. Long segments are first subdivided (every
     * [step] units, other channels interpolated) so the cut follows the eraser edge closely.
     * Pieces with fewer than 2 points are dropped.
     */
    internal fun cut(points: StrokePoints, path: List<Vec>, reach: Double, step: Double): List<StrokePoints> {
        val dense = densify(points, step)
        val inside = BooleanArray(dense.size) { i -> distanceToPolyline(dense.point(i), path, closed = false) <= reach }
        val pieces = ArrayList<StrokePoints>()
        var start = -1
        for (i in 0..dense.size) {
            val keep = i < dense.size && !inside[i]
            if (keep && start < 0) start = i
            if (!keep && start >= 0) {
                if (i - start >= 2) pieces.add(dense.slice(start, i))
                start = -1
            }
        }
        return pieces
    }

    /** Inserts interpolated samples so no segment is longer than [step]. */
    internal fun densify(p: StrokePoints, step: Double): StrokePoints {
        if (p.size < 2) return p
        val b = StrokePoints.Builder(p.pressure != null, p.tilt != null, p.orientation != null)
        fun add(i: Int, j: Int, f: Float) {
            fun lerp(a: FloatArray?, def: Float) = a?.let { it[i] + (it[j] - it[i]) * f } ?: def
            b.add(
                p.x[i] + (p.x[j] - p.x[i]) * f,
                p.y[i] + (p.y[j] - p.y[i]) * f,
                p.t[i] + ((p.t[j] - p.t[i]) * f).toInt(),
                lerp(p.pressure, 1f),
                lerp(p.tilt, 0f),
                // Orientation is an angle; interpolate only between close values, otherwise keep the first.
                p.orientation?.let { o -> if (kotlin.math.abs(o[j] - o[i]) < 1f) o[i] + (o[j] - o[i]) * f else o[i] } ?: 0f,
            )
        }
        for (i in 0 until p.size - 1) {
            val n = ceil(dist(p.point(i), p.point(i + 1)) / step).toInt().coerceIn(1, 10_000)
            for (k in 0 until n) add(i, i + 1, k.toFloat() / n)
        }
        add(p.size - 1, p.size - 1, 0f)
        return b.build()
    }

    /** True if any segment of [a] comes within [reach] of any segment of [b] (single points allowed). */
    private fun polylinesWithin(a: List<Vec>, b: List<Vec>, reach: Double): Boolean {
        val segA = segments(a)
        val segB = segments(b)
        for ((p1, p2) in segA) {
            val ra = Rect(minOf(p1.x, p2.x), minOf(p1.y, p2.y), maxOf(p1.x, p2.x), maxOf(p1.y, p2.y)).inflate(reach)
            for ((q1, q2) in segB) {
                val rb = Rect(minOf(q1.x, q2.x), minOf(q1.y, q2.y), maxOf(q1.x, q2.x), maxOf(q1.y, q2.y))
                if (ra.intersects(rb) && segmentSegmentDistance(p1, p2, q1, q2) <= reach) return true
            }
        }
        return false
    }

    private fun segments(p: List<Vec>): List<Pair<Vec, Vec>> =
        if (p.size == 1) listOf(p[0] to p[0]) else (0 until p.size - 1).map { p[it] to p[it + 1] }
}
