package com.appname.notes.core.ink.codec

import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.Pt
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Outcome of decoding stored ink. */
sealed interface DecodeResult {
    data class Ok(val item: InkItem) : DecodeResult

    /** Written by a newer app version: open read-only, never overwrite (same rule as text notes). */
    data class NewerVersion(val version: Int) : DecodeResult

    /** Damaged bytes. [reason] is safe to log (no ink data in it). */
    data class Corrupt(val reason: String) : DecodeResult
}

/**
 * Compact, versioned binary format for one ink item (SRS NFR-11: portable, documented, ours).
 * The item's id and z live in database columns; this is the body.
 *
 * ```
 * u8   format version (1)
 * u8   item type: 1 = stroke, 2 = shape
 * u8   tool, i32 colour (ARGB), f32 size                      — brush
 * stroke: points
 * shape:  u8 kind, geometry, u8 has-source, [points]
 * geometry: u8 tag, then 1 segment  = f32 x1 y1 x2 y2 head
 *                        2 ellipse  = f32 cx cy rx ry rotation
 *                        3 polygon  = varint n, n × f32 x y
 * points:  varint n, u8 channels (bit0 pressure, bit1 tilt, bit2 orientation),
 *          x, y in 1/16 unit and t in ms as zig-zag varint deltas from the previous point,
 *          then n × u16 per present channel (pressure 0..1, tilt 0..π/2, orientation 0..2π).
 * ```
 * Varint = 7 bits per byte, high bit means "more bytes" (like protobuf). Zig-zag maps signed to unsigned
 * so small negative deltas stay small: 0→0, −1→1, 1→2, −2→3 …
 *
 * Quantisation: stored values are rounded (coordinates to 1/16 unit ≈ 0.017 mm). The editor runs
 * [quantize] on every finished stroke, so what's in memory equals what's on disk exactly.
 */
object InkCodec {

    const val FORMAT_VERSION = 1

    private const val TYPE_STROKE = 1
    private const val TYPE_SHAPE = 2
    private const val GEO_SEGMENT = 1
    private const val GEO_ELLIPSE = 2
    private const val GEO_POLYGON = 3
    private const val CH_PRESSURE = 1
    private const val CH_TILT = 2
    private const val CH_ORIENTATION = 4

    private const val COORD_SCALE = 16f
    private const val U16_MAX = 65535
    private const val TILT_STEP = (PI / 2 / U16_MAX).toFloat()
    private const val ORIENT_STEP = (2 * PI / U16_MAX).toFloat()

    /** Sanity limit while decoding (a real stroke has hundreds to a few thousand points). */
    private const val MAX_POINTS = 1_000_000

    // ------------------------------------------------------------------ quantisation

    private fun qCoord(v: Float): Long = (v * COORD_SCALE).toDouble().roundToLong()
    private fun dqCoord(q: Long): Float = q / COORD_SCALE
    private fun qUnit(v: Float, step: Float): Int = (v / step).roundToInt().coerceIn(0, U16_MAX)
    private fun qPressure(v: Float): Int = (v.coerceIn(0f, 1f) * U16_MAX).roundToInt()
    private fun dqPressure(q: Int): Float = q / U16_MAX.toFloat()

    /** Rounds every channel exactly as [encode] will, so decode(encode(quantize(p))) == quantize(p). */
    fun quantize(p: StrokePoints): StrokePoints = StrokePoints(
        FloatArray(p.size) { dqCoord(qCoord(p.x[it])) },
        FloatArray(p.size) { dqCoord(qCoord(p.y[it])) },
        p.t.copyOf(),
        p.pressure?.let { a -> FloatArray(a.size) { dqPressure(qPressure(a[it])) } },
        p.tilt?.let { a -> FloatArray(a.size) { qUnit(a[it], TILT_STEP) * TILT_STEP } },
        p.orientation?.let { a -> FloatArray(a.size) { qUnit(wrap(a[it]), ORIENT_STEP) * ORIENT_STEP } },
    )

    fun quantize(item: InkItem): InkItem = when (item) {
        is StrokeItem -> item.copy(points = quantize(item.points))
        is ShapeItem -> item.copy(source = item.source?.let { quantize(it) })
    }

    private fun wrap(a: Float): Float {
        val twoPi = (2 * PI).toFloat()
        val r = a % twoPi
        return if (r < 0) r + twoPi else r
    }

    // ------------------------------------------------------------------ encode

    fun encode(item: InkItem): ByteArray {
        val w = Writer()
        w.u8(FORMAT_VERSION)
        w.u8(if (item is StrokeItem) TYPE_STROKE else TYPE_SHAPE)
        w.u8(item.brush.tool.code)
        w.i32(item.brush.colorArgb)
        w.f32(item.brush.size)
        when (item) {
            is StrokeItem -> writePoints(w, item.points)
            is ShapeItem -> {
                w.u8(item.kind.code)
                when (val g = item.geometry) {
                    is ShapeGeometry.Segment -> {
                        w.u8(GEO_SEGMENT)
                        w.f32(g.start.x); w.f32(g.start.y); w.f32(g.end.x); w.f32(g.end.y); w.f32(g.arrowHead)
                    }
                    is ShapeGeometry.Ellipse -> {
                        w.u8(GEO_ELLIPSE)
                        w.f32(g.center.x); w.f32(g.center.y); w.f32(g.rx); w.f32(g.ry); w.f32(g.rotationDeg)
                    }
                    is ShapeGeometry.Polygon -> {
                        w.u8(GEO_POLYGON)
                        w.varint(g.vertices.size.toLong())
                        for (v in g.vertices) { w.f32(v.x); w.f32(v.y) }
                    }
                }
                val src = item.source
                w.u8(if (src != null) 1 else 0)
                if (src != null) writePoints(w, src)
            }
        }
        return w.bytes()
    }

    private fun writePoints(w: Writer, p: StrokePoints) {
        w.varint(p.size.toLong())
        var ch = 0
        if (p.pressure != null) ch = ch or CH_PRESSURE
        if (p.tilt != null) ch = ch or CH_TILT
        if (p.orientation != null) ch = ch or CH_ORIENTATION
        w.u8(ch)
        var px = 0L
        var py = 0L
        var pt = 0L
        for (i in 0 until p.size) {
            val qx = qCoord(p.x[i])
            val qy = qCoord(p.y[i])
            val qt = p.t[i].toLong()
            w.zigzag(qx - px); w.zigzag(qy - py); w.zigzag(qt - pt)
            px = qx; py = qy; pt = qt
        }
        p.pressure?.forEach { w.u16(qPressure(it)) }
        p.tilt?.forEach { w.u16(qUnit(it, TILT_STEP)) }
        p.orientation?.forEach { w.u16(qUnit(wrap(it), ORIENT_STEP)) }
    }

    // ------------------------------------------------------------------ decode

    fun decode(id: String, z: Long, bytes: ByteArray): DecodeResult {
        val r = Reader(bytes)
        return try {
            val version = r.u8()
            when {
                version > FORMAT_VERSION -> return DecodeResult.NewerVersion(version)
                version < 1 -> return DecodeResult.Corrupt("bad version")
            }
            val type = r.u8()
            val tool = InkTool.fromCode(r.u8()) ?: return DecodeResult.Corrupt("unknown tool")
            val brush = InkBrush(tool, r.i32(), r.f32())
            val item: InkItem = when (type) {
                TYPE_STROKE -> StrokeItem(id, z, brush, readPoints(r))
                TYPE_SHAPE -> {
                    val kind = ShapeKind.fromCode(r.u8()) ?: return DecodeResult.Corrupt("unknown shape")
                    val geometry = when (r.u8()) {
                        GEO_SEGMENT -> ShapeGeometry.Segment(Pt(r.f32(), r.f32()), Pt(r.f32(), r.f32()), r.f32())
                        GEO_ELLIPSE -> ShapeGeometry.Ellipse(Pt(r.f32(), r.f32()), r.f32(), r.f32(), r.f32())
                        GEO_POLYGON -> {
                            val n = r.count(limit = 64)
                            ShapeGeometry.Polygon(List(n) { Pt(r.f32(), r.f32()) })
                        }
                        else -> return DecodeResult.Corrupt("unknown geometry")
                    }
                    val source = if (r.u8() == 1) readPoints(r) else null
                    ShapeItem(id, z, brush, kind, geometry, source)
                }
                else -> return DecodeResult.Corrupt("unknown item type")
            }
            if (!r.atEnd) return DecodeResult.Corrupt("trailing bytes")
            DecodeResult.Ok(item)
        } catch (e: IndexOutOfBoundsException) {
            DecodeResult.Corrupt("truncated")
        } catch (e: IllegalArgumentException) {
            DecodeResult.Corrupt(e.message ?: "invalid value")
        }
    }

    private fun readPoints(r: Reader): StrokePoints {
        val n = r.count(limit = MAX_POINTS)
        val ch = r.u8()
        require(ch and 7.inv() == 0) { "unknown channels" }
        val x = FloatArray(n)
        val y = FloatArray(n)
        val t = IntArray(n)
        var px = 0L
        var py = 0L
        var pt = 0L
        for (i in 0 until n) {
            px += r.zigzag(); py += r.zigzag(); pt += r.zigzag()
            x[i] = dqCoord(px); y[i] = dqCoord(py)
            require(pt in Int.MIN_VALUE..Int.MAX_VALUE) { "time out of range" }
            t[i] = pt.toInt()
        }
        val pressure = if (ch and CH_PRESSURE != 0) FloatArray(n) { dqPressure(r.u16()) } else null
        val tilt = if (ch and CH_TILT != 0) FloatArray(n) { r.u16() * TILT_STEP } else null
        val orientation = if (ch and CH_ORIENTATION != 0) FloatArray(n) { r.u16() * ORIENT_STEP } else null
        require(n > 0) { "empty stroke" }
        return StrokePoints(x, y, t, pressure, tilt, orientation)
    }

    // ------------------------------------------------------------------ byte helpers

    private class Writer {
        private val out = ByteArrayOutputStream(256)
        fun u8(v: Int) = out.write(v and 0xFF)
        fun u16(v: Int) { u8(v ushr 8); u8(v) }
        fun i32(v: Int) { u8(v ushr 24); u8(v ushr 16); u8(v ushr 8); u8(v) }
        fun f32(v: Float) = i32(java.lang.Float.floatToRawIntBits(v))
        fun varint(value: Long) {
            var v = value
            while (v and 0x7FL.inv() != 0L) {
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
            out.write(v.toInt())
        }
        fun zigzag(v: Long) = varint((v shl 1) xor (v shr 63))
        fun bytes(): ByteArray = out.toByteArray()
    }

    private class Reader(private val b: ByteArray) {
        private var pos = 0
        val atEnd: Boolean get() = pos == b.size
        fun u8(): Int = b[pos++].toInt() and 0xFF
        fun u16(): Int = (u8() shl 8) or u8()
        fun i32(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()
        fun f32(): Float = java.lang.Float.intBitsToFloat(i32()).also { require(it.isFinite()) { "non-finite number" } }
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val byte = u8()
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
                require(shift < 64) { "varint too long" }
            }
        }
        fun zigzag(): Long {
            val v = varint()
            return (v ushr 1) xor -(v and 1)
        }

        /** A count that must be plausible for the remaining bytes. */
        fun count(limit: Int): Int {
            val n = varint()
            require(n in 0..limit.toLong() && n <= b.size - pos) { "bad count" }
            return n.toInt()
        }
    }
}
