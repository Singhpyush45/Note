package com.appname.notes.core.ink.model

import com.appname.notes.core.ink.geometry.Rect
import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.geometry.bounds
import com.appname.notes.core.ink.geometry.ellipseOutline
import kotlin.math.cos
import kotlin.math.sin

/*
 * Handwriting data model (ADR-0009 Part A). Our own format: nothing stored depends on Jetpack Ink (NFR-11).
 *
 * Coordinates are page units: 1 unit = 1/96 inch, so an A4 page is 794 × 1123 units at any zoom or screen
 * density. y grows downwards, like on screen.
 */

/** Pen types. [code] is the stored value: never renumber. */
enum class InkTool(val code: Int) {
    /** Pressure-sensitive ballpoint-style pen. */
    PEN(1),

    /** Felt-tip marker: even width. */
    MARKER(2),

    /** Wide translucent highlighter drawn under the other ink. */
    HIGHLIGHTER(3),
    ;

    companion object {
        fun fromCode(code: Int): InkTool? = entries.firstOrNull { it.code == code }
    }
}

/** How a stroke or shape is drawn. [colorArgb] is 0xAARRGGBB; [size] is the line width in page units. */
data class InkBrush(val tool: InkTool, val colorArgb: Int, val size: Float) {
    init {
        require(size.isFinite() && size > 0f) { "brush size must be positive" }
    }
}

/**
 * The sampled points of one stroke, stored column-wise ("structure of arrays"): one array per channel.
 * Compact and fast to encode; like several `std::vector<float>` side by side in C++.
 *
 * - [x], [y]: page units.
 * - [t]: milliseconds since the stroke started.
 * - [pressure] 0..1, [tilt] 0..π/2 radians, [orientation] 0..2π radians: null when the input device
 *   doesn't report them (finger, mouse).
 */
class StrokePoints(
    val x: FloatArray,
    val y: FloatArray,
    val t: IntArray,
    val pressure: FloatArray? = null,
    val tilt: FloatArray? = null,
    val orientation: FloatArray? = null,
) {
    init {
        val n = x.size
        require(y.size == n && t.size == n) { "channel sizes differ" }
        require(pressure == null || pressure.size == n) { "pressure size differs" }
        require(tilt == null || tilt.size == n) { "tilt size differs" }
        require(orientation == null || orientation.size == n) { "orientation size differs" }
    }

    val size: Int get() = x.size

    fun point(i: Int) = Vec(x[i].toDouble(), y[i].toDouble())

    fun toVecs(): List<Vec> = List(size) { point(it) }

    /** Bounds of the point centres (add half the brush size for the drawn area). */
    fun bounds(): Rect = bounds(toVecs())

    /** Copy of the points [from] (inclusive) to [to] (exclusive). */
    fun slice(from: Int, to: Int) = StrokePoints(
        x.copyOfRange(from, to),
        y.copyOfRange(from, to),
        t.copyOfRange(from, to),
        pressure?.copyOfRange(from, to),
        tilt?.copyOfRange(from, to),
        orientation?.copyOfRange(from, to),
    )

    override fun equals(other: Any?): Boolean = other is StrokePoints &&
        x.contentEquals(other.x) && y.contentEquals(other.y) && t.contentEquals(other.t) &&
        pressure.contentEquals(other.pressure) && tilt.contentEquals(other.tilt) &&
        orientation.contentEquals(other.orientation)

    override fun hashCode(): Int {
        var h = x.contentHashCode()
        h = 31 * h + y.contentHashCode()
        h = 31 * h + t.contentHashCode()
        h = 31 * h + pressure.contentHashCode()
        h = 31 * h + tilt.contentHashCode()
        h = 31 * h + orientation.contentHashCode()
        return h
    }

    override fun toString() = "StrokePoints(size=$size)" // never log coordinates (SRS 15.2)

    /** Builds points one by one while the pen moves (like `std::vector::push_back`). */
    class Builder(
        private val hasPressure: Boolean,
        private val hasTilt: Boolean,
        private val hasOrientation: Boolean,
    ) {
        private val xs = FloatList()
        private val ys = FloatList()
        private val ts = IntList()
        private val ps = FloatList()
        private val tl = FloatList()
        private val or = FloatList()

        val size: Int get() = xs.size

        fun add(x: Float, y: Float, t: Int, pressure: Float = 1f, tilt: Float = 0f, orientation: Float = 0f) {
            xs.add(x); ys.add(y); ts.add(t)
            if (hasPressure) ps.add(pressure)
            if (hasTilt) tl.add(tilt)
            if (hasOrientation) or.add(orientation)
        }

        fun build() = StrokePoints(
            xs.toArray(),
            ys.toArray(),
            ts.toArray(),
            if (hasPressure) ps.toArray() else null,
            if (hasTilt) tl.toArray() else null,
            if (hasOrientation) or.toArray() else null,
        )
    }
}

/** Growable primitive arrays: avoid boxing every float into an object. */
internal class FloatList {
    private var a = FloatArray(64)
    var size = 0
        private set

    fun add(v: Float) {
        if (size == a.size) a = a.copyOf(size * 2)
        a[size++] = v
    }

    fun toArray(): FloatArray = a.copyOf(size)
}

internal class IntList {
    private var a = IntArray(64)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == a.size) a = a.copyOf(size * 2)
        a[size++] = v
    }

    fun toArray(): IntArray = a.copyOf(size)
}

/** Shapes the recognizer can produce. [code] is the stored value: never renumber. */
enum class ShapeKind(val code: Int) {
    LINE(1),
    ARROW(2),
    CIRCLE(3),
    ELLIPSE(4),
    TRIANGLE(5),
    RECTANGLE(6),
    SQUARE(7),
    ;

    fun accepts(g: ShapeGeometry): Boolean = when (this) {
        LINE -> g is ShapeGeometry.Segment && g.arrowHead == 0f
        ARROW -> g is ShapeGeometry.Segment && g.arrowHead > 0f
        CIRCLE, ELLIPSE -> g is ShapeGeometry.Ellipse
        TRIANGLE -> g is ShapeGeometry.Polygon && g.vertices.size == 3
        RECTANGLE, SQUARE -> g is ShapeGeometry.Polygon && g.vertices.size == 4
    }

    companion object {
        fun fromCode(code: Int): ShapeKind? = entries.firstOrNull { it.code == code }
    }
}

/** A stored point of shape geometry (float, like the rest of stored ink). */
data class Pt(val x: Float, val y: Float) {
    fun toVec() = Vec(x.toDouble(), y.toDouble())
}

fun Vec.toPt() = Pt(x.toFloat(), y.toFloat())

/** Exact vector geometry of a corrected shape. */
sealed interface ShapeGeometry {
    /** Line, or arrow when [arrowHead] > 0 (length of the head's sides, pointing at [end]). */
    data class Segment(val start: Pt, val end: Pt, val arrowHead: Float = 0f) : ShapeGeometry

    /** Ellipse (a circle when rx == ry) rotated by [rotationDeg] around its centre. */
    data class Ellipse(val center: Pt, val rx: Float, val ry: Float, val rotationDeg: Float) : ShapeGeometry

    /** Closed polygon: triangle, rectangle, square. */
    data class Polygon(val vertices: List<Pt>) : ShapeGeometry

    /** Points along the drawn outline, for hit testing, lasso and thumbnails. */
    fun outline(): List<Vec> = when (this) {
        is Segment -> {
            val s = start.toVec()
            val e = end.toVec()
            if (arrowHead == 0f) {
                listOf(s, e)
            } else {
                val (h1, h2) = arrowHeadPoints(this)
                listOf(s, e, h1, e, h2)
            }
        }
        is Ellipse -> ellipseOutline(center.toVec(), rx.toDouble(), ry.toDouble(), rotationDeg.toDouble()).let { it + it[0] }
        is Polygon -> vertices.map { it.toVec() }.let { it + it[0] }
    }
}

/** Angle between the arrow shaft and each side of its head. */
const val ARROW_HEAD_ANGLE_DEG = 28.0

/** The two outer points of an arrow head. */
fun arrowHeadPoints(s: ShapeGeometry.Segment): Pair<Vec, Vec> {
    val e = s.end.toVec()
    val back = s.start.toVec() - e
    val len = back.length
    if (len == 0.0) return e to e
    val ux = back.x / len
    val uy = back.y / len
    val a = Math.toRadians(ARROW_HEAD_ANGLE_DEG)
    val h = s.arrowHead.toDouble()
    fun side(sign: Double): Vec {
        val c = cos(sign * a)
        val sn = sin(sign * a)
        return Vec(e.x + h * (ux * c - uy * sn), e.y + h * (ux * sn + uy * c))
    }
    return side(1.0) to side(-1.0)
}

/**
 * Something drawn on a page. Items are painted in order of ([z], [id]); [z] comes from a per-page counter,
 * so new ink lands on top. An item that replaces another (shape correction, partial erase, move, recolour)
 * keeps the original's [z] so it stays at the same depth.
 */
sealed interface InkItem {
    val id: String
    val z: Long
    val brush: InkBrush

    /** Area the item paints, including line width. */
    fun bounds(): Rect

    fun withZ(z: Long): InkItem
}

data class StrokeItem(
    override val id: String,
    override val z: Long,
    override val brush: InkBrush,
    val points: StrokePoints,
) : InkItem {
    init {
        require(points.size > 0) { "a stroke needs at least one point" }
    }

    override fun bounds(): Rect = points.bounds().inflate(brush.size / 2.0)
    override fun withZ(z: Long) = copy(z = z)
}

/**
 * A corrected shape. [source] keeps the original hand-drawn stroke (SRS 6.2: original data is retained),
 * so the correction can be undone or reverted to ink later.
 */
data class ShapeItem(
    override val id: String,
    override val z: Long,
    override val brush: InkBrush,
    val kind: ShapeKind,
    val geometry: ShapeGeometry,
    val source: StrokePoints?,
) : InkItem {
    init {
        require(kind.accepts(geometry)) { "$kind can't use ${geometry::class.simpleName}" }
    }

    override fun bounds(): Rect = bounds(geometry.outline()).inflate(brush.size / 2.0)
    override fun withZ(z: Long) = copy(z = z)
}

/** Background printed on a page. [code] is the stored value. */
enum class PageTemplate(val code: Int) {
    BLANK(0),
    LINED(1),
    GRID(2),
    DOTTED(3),
    ;

    companion object {
        fun fromCode(code: Int): PageTemplate = entries.firstOrNull { it.code == code } ?: BLANK

        /** Distance between ruled lines, grid lines and dots, in page units (≈ 8 mm and 6 mm). */
        const val LINE_SPACING = 30f
        const val GRID_SPACING = 24f
    }
}

/** Standard page sizes, in page units (1/96 inch). */
object PageSizes {
    const val A4_WIDTH = 794f
    const val A4_HEIGHT = 1123f
}

/** Comparator for paint order. */
val PaintOrder: Comparator<InkItem> = compareBy<InkItem>({ it.z }, { it.id })
