package com.appname.notes.core.ink

import com.appname.notes.core.ink.codec.InkCodec
import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.Pt
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import java.util.Random

/** Small factories shared by the tests. */
object TestInk {
    val PEN = InkBrush(InkTool.PEN, 0xFF1A1A1A.toInt(), 2f)

    private var counter = 0
    fun newId(): String = "id-${counter++}"

    fun points(vararg p: Pair<Number, Number>, pressure: Boolean = false): StrokePoints {
        val b = StrokePoints.Builder(hasPressure = pressure, hasTilt = false, hasOrientation = false)
        p.forEachIndexed { i, (x, y) -> b.add(x.toFloat(), y.toFloat(), i * 8, pressure = 0.5f) }
        return b.build()
    }

    fun line(a: Vec, b: Vec, n: Int = 20): StrokePoints {
        val bld = StrokePoints.Builder(true, true, true)
        for (i in 0 until n) {
            val t = i.toFloat() / (n - 1)
            bld.add((a.x + (b.x - a.x) * t).toFloat(), (a.y + (b.y - a.y) * t).toFloat(), i * 8, 0.6f, 0.3f, 1.0f)
        }
        return bld.build()
    }

    fun stroke(points: StrokePoints, z: Long = 1, brush: InkBrush = PEN, id: String = newId()) =
        StrokeItem(id, z, brush, points)

    /** Random stroke with all channels, already quantised like the editor does. */
    fun randomStroke(rng: Random, z: Long, id: String = newId()): StrokeItem {
        val n = 1 + rng.nextInt(300)
        val channels = rng.nextInt(8)
        val b = StrokePoints.Builder(channels and 1 != 0, channels and 2 != 0, channels and 4 != 0)
        var x = rng.nextFloat() * 800
        var y = rng.nextFloat() * 1100
        var t = 0
        repeat(n) {
            x += rng.nextFloat() * 6 - 3; y += rng.nextFloat() * 6 - 3; t += rng.nextInt(12)
            b.add(x, y, t, rng.nextFloat(), rng.nextFloat() * 1.6f, rng.nextFloat() * 6.28f)
        }
        val tool = InkTool.entries[rng.nextInt(InkTool.entries.size)]
        val item = StrokeItem(id, z, InkBrush(tool, rng.nextInt(), 0.5f + rng.nextFloat() * 20), b.build())
        return InkCodec.quantize(item) as StrokeItem
    }

    fun randomShape(rng: Random, z: Long, id: String = newId()): ShapeItem {
        fun p() = Pt(rng.nextFloat() * 800, rng.nextFloat() * 1100)
        val kind = ShapeKind.entries[rng.nextInt(ShapeKind.entries.size)]
        val g = when (kind) {
            ShapeKind.LINE -> ShapeGeometry.Segment(p(), p())
            ShapeKind.ARROW -> ShapeGeometry.Segment(p(), p(), 5 + rng.nextFloat() * 40)
            ShapeKind.CIRCLE -> ShapeGeometry.Ellipse(p(), 30f, 30f, 0f)
            ShapeKind.ELLIPSE -> ShapeGeometry.Ellipse(p(), 60f, 25f, rng.nextFloat() * 180)
            ShapeKind.TRIANGLE -> ShapeGeometry.Polygon(List(3) { p() })
            ShapeKind.RECTANGLE, ShapeKind.SQUARE -> ShapeGeometry.Polygon(List(4) { p() })
        }
        val source = if (rng.nextBoolean()) randomStroke(rng, z).points else null
        return ShapeItem(id, z, PEN.copy(colorArgb = rng.nextInt()), kind, g, source)
    }
}
