package com.appname.notes.core.ink.geometry

import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.StrokePoints
import com.appname.notes.core.ink.model.WidthMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The geometry of a variant stroke (owner brief §12), independent of Android so it can be tested: the width at
 * every point (from pressure, the nib's angle to the stroke direction, and tapered ends), the filled outline
 * around the points, and dot positions for the dot pen. Coordinates are page units.
 */
object StrokeOutline {

    /** Pressure assumed for input without pressure (finger, mouse) or when pressure is switched off. */
    const val EVEN_PRESSURE = 0.6f

    /** Pressure at which a stroke reaches its full width (people rarely press harder than this). */
    private const val FULL_PRESSURE = 0.85f

    /** Ends never taper to nothing: the thinnest end is this fraction of the width. */
    private const val MIN_TAPER = 0.12f

    /** Width (page units) at each point. */
    fun widths(p: StrokePoints, brush: InkBrush): FloatArray {
        val spec = brush.effectiveStyle.spec
        val n = p.size
        val size = brush.size
        val pressure = if (brush.pressure) p.pressure else null
        fun pr(i: Int) = pressure?.get(i) ?: EVEN_PRESSURE
        fun pressureFactor(i: Int) = spec.minWidth + (1f - spec.minWidth) * (pr(i) / FULL_PRESSURE).coerceIn(0f, 1f)
        val nib = Math.toRadians(spec.nibAngleDeg.toDouble())
        val w = FloatArray(n) { i ->
            when (spec.width) {
                WidthMode.CONSTANT -> size
                WidthMode.PRESSURE -> size * pressureFactor(i)
                WidthMode.NIB -> {
                    val a = maxOf(0, i - 1)
                    val b = minOf(n - 1, i + 1)
                    val dir = atan2((p.y[b] - p.y[a]).toDouble(), (p.x[b] - p.x[a]).toDouble())
                    // Across the nib = full width; along it = thinnest.
                    val across = abs(sin(dir - nib)).toFloat()
                    val flat = spec.minWidth + (1f - spec.minWidth) * across
                    size * flat * (if (spec.nibPressure) 0.6f + 0.5f * (pr(i) / FULL_PRESSURE).coerceIn(0f, 1f) else 1f)
                }
            }
        }
        if (spec.taper > 0f && n > 2) {
            val along = cumulative(p)
            val total = along[n - 1]
            val reach = (spec.taper * total).coerceAtLeast(1e-3f)
            for (i in 0 until n) {
                val fromEnd = minOf(along[i], total - along[i])
                w[i] *= MIN_TAPER + (1f - MIN_TAPER) * smoothstep((fromEnd / reach).coerceIn(0f, 1f))
            }
        }
        return w
    }

    /**
     * The closed outline around the stroke: the left side forwards, then the right side backwards, as x,y
     * pairs. Empty for a single point (the caller draws a dot).
     */
    fun outline(p: StrokePoints, widths: FloatArray): FloatArray {
        val n = p.size
        if (n < 2) return FloatArray(0)
        val left = FloatArray(n * 2)
        val right = FloatArray(n * 2)
        var nx = 0f
        var ny = -1f
        for (i in 0 until n) {
            val a = maxOf(0, i - 1)
            val b = minOf(n - 1, i + 1)
            val dx = p.x[b] - p.x[a]
            val dy = p.y[b] - p.y[a]
            val len = hypot(dx, dy)
            if (len > 1e-4f) { // repeated points keep the previous direction
                nx = -dy / len
                ny = dx / len
            }
            val h = widths[i] / 2f
            left[2 * i] = p.x[i] + nx * h
            left[2 * i + 1] = p.y[i] + ny * h
            right[2 * i] = p.x[i] - nx * h
            right[2 * i + 1] = p.y[i] - ny * h
        }
        val out = FloatArray(n * 4)
        left.copyInto(out)
        for (i in 0 until n) {
            val src = n - 1 - i
            out[2 * n + 2 * i] = right[2 * src]
            out[2 * n + 2 * i + 1] = right[2 * src + 1]
        }
        return out
    }

    /** Evenly spaced points along the stroke, [spacing] page units apart (the first at the start), as x,y pairs. */
    fun dots(p: StrokePoints, spacing: Float): FloatArray {
        val n = p.size
        if (n == 0) return FloatArray(0)
        val step = spacing.coerceAtLeast(0.5f)
        val out = ArrayList<Float>()
        out += p.x[0]; out += p.y[0]
        var carry = 0f // distance since the last dot
        for (i in 1 until n) {
            val sx = p.x[i - 1]
            val sy = p.y[i - 1]
            val seg = hypot(p.x[i] - sx, p.y[i] - sy)
            var at = step - carry
            while (at <= seg) {
                val f = at / seg
                out += sx + (p.x[i] - sx) * f
                out += sy + (p.y[i] - sy) * f
                at += step
            }
            carry = (carry + seg) % step
        }
        return out.toFloatArray()
    }

    private fun cumulative(p: StrokePoints): FloatArray {
        val d = FloatArray(p.size)
        for (i in 1 until p.size) d[i] = d[i - 1] + hypot(p.x[i] - p.x[i - 1], p.y[i] - p.y[i - 1])
        return d
    }

    private fun smoothstep(t: Float) = t * t * (3f - 2f * t)
}
