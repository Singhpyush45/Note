package com.appname.notes.core.ink.input

import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.model.Pt
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The shape tool (owner brief §15): drag from one corner to the other. Lines and arrows go from the start point to
 * the pen; the other shapes fill the box the drag spans (square and circle use its shorter side).
 */
object ShapeTool {

    /** Shapes the tool offers, in toolbar order. */
    val KINDS = listOf(ShapeKind.LINE, ShapeKind.ARROW, ShapeKind.RECTANGLE, ShapeKind.SQUARE, ShapeKind.ELLIPSE, ShapeKind.CIRCLE, ShapeKind.TRIANGLE)

    /** Arrow head length relative to the brush size, never tiny. */
    fun arrowHead(size: Float): Float = max(10f, size * 4f)

    fun geometry(kind: ShapeKind, a: Vec, b: Vec, size: Float): ShapeGeometry {
        val left = min(a.x, b.x).toFloat()
        val top = min(a.y, b.y).toFloat()
        val right = max(a.x, b.x).toFloat()
        val bottom = max(a.y, b.y).toFloat()
        return when (kind) {
            ShapeKind.LINE -> ShapeGeometry.Segment(a.toPtF(), b.toPtF())
            ShapeKind.ARROW -> ShapeGeometry.Segment(a.toPtF(), b.toPtF(), arrowHead(size))
            ShapeKind.RECTANGLE -> ShapeGeometry.Polygon(listOf(Pt(left, top), Pt(right, top), Pt(right, bottom), Pt(left, bottom)))
            ShapeKind.SQUARE -> {
                val s = min(right - left, bottom - top)
                // Grows from the start corner towards the pen.
                val x0 = if (b.x >= a.x) a.x.toFloat() else a.x.toFloat() - s
                val y0 = if (b.y >= a.y) a.y.toFloat() else a.y.toFloat() - s
                ShapeGeometry.Polygon(listOf(Pt(x0, y0), Pt(x0 + s, y0), Pt(x0 + s, y0 + s), Pt(x0, y0 + s)))
            }
            ShapeKind.ELLIPSE -> ShapeGeometry.Ellipse(Pt((left + right) / 2, (top + bottom) / 2), (right - left) / 2, (bottom - top) / 2, 0f)
            ShapeKind.CIRCLE -> {
                val r = min(right - left, bottom - top) / 2
                val cx = if (b.x >= a.x) a.x.toFloat() + r else a.x.toFloat() - r
                val cy = if (b.y >= a.y) a.y.toFloat() + r else a.y.toFloat() - r
                ShapeGeometry.Ellipse(Pt(cx, cy), r, r, 0f)
            }
            ShapeKind.TRIANGLE -> ShapeGeometry.Polygon(listOf(Pt((left + right) / 2, top), Pt(right, bottom), Pt(left, bottom)))
        }
    }

    /** Too small to be meant (a tap): nothing is added. */
    fun tooSmall(a: Vec, b: Vec, minUnits: Double): Boolean = abs(a.x - b.x) < minUnits && abs(a.y - b.y) < minUnits

    private fun Vec.toPtF() = Pt(x.toFloat(), y.toFloat())
}
