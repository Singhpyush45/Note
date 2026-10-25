package com.appname.notes.core.ink.pdf

import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.geometry.rotate
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import com.appname.notes.core.ink.model.arrowHeadPoints

/**
 * Vector drawing commands for exporting ink into a PDF page, in our page units (the exporter wraps them in
 * the page's [PdfPageMapping.unitsToUser] matrix). Independent of any PDF library, so it is unit-tested;
 * the app replays them with PdfBox.
 *
 * Looks the same as the app's built-in renderer: smooth paths for markers, highlighters and shapes;
 * pressure pens as short round-capped segments whose width follows pressure.
 */
sealed interface PdfOp {
    /** Starts a new stroked path style. Colour components 0..1; [alpha] 0..1. */
    data class Style(val r: Float, val g: Float, val b: Float, val alpha: Float, val width: Float, val roundCaps: Boolean) : PdfOp
    data class MoveTo(val x: Float, val y: Float) : PdfOp
    data class LineTo(val x: Float, val y: Float) : PdfOp
    data class CurveTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x3: Float, val y3: Float) : PdfOp
    data object Close : PdfOp
    data object Stroke : PdfOp
}

object PdfInkOps {

    /** Highlighters first (under the writing), like on screen. */
    fun forItems(items: List<InkItem>): List<PdfOp> {
        val out = ArrayList<PdfOp>()
        for (pass in 0..1) {
            for (item in items) {
                if ((pass == 0) != (item.brush.tool == InkTool.HIGHLIGHTER)) continue
                when (item) {
                    is StrokeItem -> stroke(item, out)
                    is ShapeItem -> shape(item, out)
                    // Photos and text boxes aren't vector ink; PDF notes don't offer them.
                    is com.appname.notes.core.ink.model.ImageItem, is com.appname.notes.core.ink.model.TextItem -> Unit
                }
            }
        }
        return out
    }

    private fun style(b: InkBrush, width: Float = b.size): PdfOp.Style {
        val c = b.colorArgb
        return PdfOp.Style(
            r = (c shr 16 and 0xFF) / 255f,
            g = (c shr 8 and 0xFF) / 255f,
            b = (c and 0xFF) / 255f,
            alpha = (c ushr 24) / 255f,
            width = width,
            roundCaps = b.tool != InkTool.HIGHLIGHTER,
        )
    }

    private fun stroke(item: StrokeItem, out: MutableList<PdfOp>) {
        val p = item.points
        val pressure = p.pressure
        if (pressure != null && item.brush.tool == InkTool.PEN && p.size > 1) {
            // One short segment per sample pair; round caps join them seamlessly.
            for (i in 0 until p.size - 1) {
                val w = item.brush.size * (0.35f + 0.9f * (pressure[i] + pressure[i + 1]) / 2)
                out += style(item.brush, w)
                out += PdfOp.MoveTo(p.x[i], p.y[i])
                out += PdfOp.LineTo(p.x[i + 1], p.y[i + 1])
                out += PdfOp.Stroke
            }
            return
        }
        out += style(item.brush)
        smoothPath(p, out)
        out += PdfOp.Stroke
    }

    /** Same curve as the screen renderer: quadratic segments through midpoints, written as cubics. */
    private fun smoothPath(p: StrokePoints, out: MutableList<PdfOp>) {
        out += PdfOp.MoveTo(p.x[0], p.y[0])
        if (p.size == 1) {
            out += PdfOp.LineTo(p.x[0] + 0.01f, p.y[0]) // a dot
            return
        }
        var cx = p.x[0]
        var cy = p.y[0]
        for (i in 1 until p.size - 1) {
            val mx = (p.x[i] + p.x[i + 1]) / 2
            val my = (p.y[i] + p.y[i + 1]) / 2
            quadTo(out, cx, cy, p.x[i], p.y[i], mx, my)
            cx = mx; cy = my
        }
        out += PdfOp.LineTo(p.x[p.size - 1], p.y[p.size - 1])
    }

    /** Quadratic (p0, c, p1) as the identical cubic: control points at 2/3 of the way to c. */
    private fun quadTo(out: MutableList<PdfOp>, x0: Float, y0: Float, qx: Float, qy: Float, x1: Float, y1: Float) {
        out += PdfOp.CurveTo(
            x0 + 2f / 3f * (qx - x0), y0 + 2f / 3f * (qy - y0),
            x1 + 2f / 3f * (qx - x1), y1 + 2f / 3f * (qy - y1),
            x1, y1,
        )
    }

    private fun shape(item: ShapeItem, out: MutableList<PdfOp>) {
        out += style(item.brush)
        when (val g = item.geometry) {
            is ShapeGeometry.Segment -> {
                out += PdfOp.MoveTo(g.start.x, g.start.y)
                out += PdfOp.LineTo(g.end.x, g.end.y)
                if (g.arrowHead > 0f) {
                    val (h1, h2) = arrowHeadPoints(g)
                    out += PdfOp.MoveTo(h1.x.toFloat(), h1.y.toFloat())
                    out += PdfOp.LineTo(g.end.x, g.end.y)
                    out += PdfOp.LineTo(h2.x.toFloat(), h2.y.toFloat())
                }
            }
            is ShapeGeometry.Ellipse -> ellipse(g, out)
            is ShapeGeometry.Polygon -> {
                g.vertices.forEachIndexed { i, v -> out += if (i == 0) PdfOp.MoveTo(v.x, v.y) else PdfOp.LineTo(v.x, v.y) }
                out += PdfOp.Close
            }
        }
        out += PdfOp.Stroke
    }

    /** Four cubic arcs (the standard κ = 0.5523 approximation, error < 0.03 %), rotated around the centre. */
    private fun ellipse(g: ShapeGeometry.Ellipse, out: MutableList<PdfOp>) {
        val c = g.center.toVec()
        val rx = g.rx.toDouble()
        val ry = g.ry.toDouble()
        val k = KAPPA
        val rot = Math.toRadians(g.rotationDeg.toDouble())
        fun pt(x: Double, y: Double) = rotate(Vec(c.x + x, c.y + y), rot, c)
        val pts = listOf(
            pt(rx, 0.0),
            pt(rx, k * ry), pt(k * rx, ry), pt(0.0, ry),
            pt(-k * rx, ry), pt(-rx, k * ry), pt(-rx, 0.0),
            pt(-rx, -k * ry), pt(-k * rx, -ry), pt(0.0, -ry),
            pt(k * rx, -ry), pt(rx, -k * ry), pt(rx, 0.0),
        )
        out += PdfOp.MoveTo(pts[0].x.toFloat(), pts[0].y.toFloat())
        for (i in 0 until 4) {
            val a = pts[1 + 3 * i]
            val b = pts[2 + 3 * i]
            val e = pts[3 + 3 * i]
            out += PdfOp.CurveTo(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), e.x.toFloat(), e.y.toFloat())
        }
        out += PdfOp.Close
    }

    private const val KAPPA = 0.5522847498
}
