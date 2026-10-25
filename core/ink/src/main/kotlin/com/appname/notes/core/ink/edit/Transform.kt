package com.appname.notes.core.ink.edit

import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.Pt
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import com.appname.notes.core.ink.model.toPt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Move, uniform scale and rotation around [pivot] ("similarity" transform). Shapes stay the same kind of
 * shape under it (a circle stays a circle), which a general affine transform wouldn't guarantee.
 *
 * p' = pivot + scale · R(rotation) · (p − pivot) + (dx, dy)
 */
data class Similarity(
    val dx: Double = 0.0,
    val dy: Double = 0.0,
    val scale: Double = 1.0,
    val rotationDeg: Double = 0.0,
    val pivot: Vec = Vec(0.0, 0.0),
) {
    init {
        require(scale > 0 && scale.isFinite())
    }

    private val rad = Math.toRadians(rotationDeg)
    private val c = cos(rad) * scale
    private val s = sin(rad) * scale

    fun apply(p: Vec): Vec {
        val x = p.x - pivot.x
        val y = p.y - pivot.y
        return Vec(pivot.x + x * c - y * s + dx, pivot.y + x * s + y * c + dy)
    }

    fun apply(p: Pt): Pt = apply(p.toVec()).toPt()

    companion object {
        fun translate(dx: Double, dy: Double) = Similarity(dx = dx, dy = dy)
    }
}

fun StrokePoints.transformed(tr: Similarity): StrokePoints {
    val nx = FloatArray(size)
    val ny = FloatArray(size)
    for (i in 0 until size) {
        val p = tr.apply(point(i))
        nx[i] = p.x.toFloat(); ny[i] = p.y.toFloat()
    }
    // Pen orientation turns with the ink; pressure, tilt and timing don't change.
    val orient = orientation?.let { o ->
        val r = Math.toRadians(tr.rotationDeg).toFloat()
        FloatArray(size) { i -> ((o[i] + r) % TWO_PI_F + TWO_PI_F) % TWO_PI_F }
    }
    return StrokePoints(nx, ny, t.copyOf(), pressure?.copyOf(), tilt?.copyOf(), orient)
}

private const val TWO_PI_F = (2 * PI).toFloat()

fun ShapeGeometry.transformed(t: Similarity): ShapeGeometry = when (this) {
    is ShapeGeometry.Segment -> ShapeGeometry.Segment(t.apply(start), t.apply(end), (arrowHead * t.scale).toFloat())
    is ShapeGeometry.Ellipse -> ShapeGeometry.Ellipse(
        t.apply(center),
        (rx * t.scale).toFloat(),
        (ry * t.scale).toFloat(),
        (((rotationDeg + t.rotationDeg) % 180.0 + 180.0) % 180.0).toFloat(),
    )
    is ShapeGeometry.Polygon -> ShapeGeometry.Polygon(vertices.map { t.apply(it) })
}

/** The item moved/scaled/rotated. Line widths scale with it. Keeps id and z (it replaces the original). */
fun InkItem.transformed(t: Similarity): InkItem {
    val brush = if (t.scale == 1.0) brush else brush.copy(size = (brush.size * t.scale).toFloat())
    return when (this) {
        is StrokeItem -> copy(brush = brush, points = points.transformed(t))
        is ShapeItem -> copy(brush = brush, geometry = geometry.transformed(t), source = source?.transformed(t))
    }
}

/**
 * A mirror image across a vertical line x = [axis] ([horizontal] flip) or a horizontal line y = [axis].
 * Not a [Similarity] (it reverses handedness), so it has its own mapping. Keeps id, z and sizes.
 */
fun InkItem.flipped(horizontal: Boolean, axis: Double): InkItem {
    fun m(p: Pt): Pt = if (horizontal) Pt((2 * axis - p.x).toFloat(), p.y) else Pt(p.x, (2 * axis - p.y).toFloat())
    fun pts(sp: StrokePoints): StrokePoints {
        val nx = FloatArray(sp.size) { i -> if (horizontal) (2 * axis - sp.x[i]).toFloat() else sp.x[i] }
        val ny = FloatArray(sp.size) { i -> if (horizontal) sp.y[i] else (2 * axis - sp.y[i]).toFloat() }
        // Pen orientation (0 = up, clockwise) mirrors too.
        val orient = sp.orientation?.let { o ->
            FloatArray(sp.size) { i -> (((if (horizontal) -o[i] else PI.toFloat() - o[i]) % TWO_PI_F) + TWO_PI_F) % TWO_PI_F }
        }
        return StrokePoints(nx, ny, sp.t.copyOf(), sp.pressure?.copyOf(), sp.tilt?.copyOf(), orient)
    }
    return when (this) {
        is StrokeItem -> copy(points = pts(points))
        is ShapeItem -> copy(
            geometry = when (val g = geometry) {
                is ShapeGeometry.Segment -> g.copy(start = m(g.start), end = m(g.end))
                is ShapeGeometry.Ellipse -> g.copy(center = m(g.center), rotationDeg = (((-g.rotationDeg) % 180f) + 180f) % 180f)
                is ShapeGeometry.Polygon -> ShapeGeometry.Polygon(g.vertices.map { m(it) })
            },
            source = source?.let { pts(it) },
        )
    }
}
