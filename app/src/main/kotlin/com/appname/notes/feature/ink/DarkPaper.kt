package com.appname.notes.feature.ink

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Dark paper" in dark theme (owner brief §5: adjust colour in dark mode). Display only: pages, templates and
 * ink are shown with lightness inverted and hue kept (white paper → dark grey, black ink → near white, blue
 * stays blue), slightly lifted so paper isn't pure black. Stored colours and exports never change.
 *
 * The same mapping is applied by a colour filter (saved ink) and to the live stroke's colour (Jetpack Ink's
 * low-latency layer can't be filtered), so a stroke looks the same while drawing and after lifting the pen.
 */
object DarkPaper {
    /** 5×4 row-major colour matrix: invert, rotate hue 180°, then compress into [LIFT, 255]. */
    private val matrix: FloatArray = run {
        val invert = ColorMatrixMath.of(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
        val hue = ColorMatrixMath.hueRotation(180f)
        val k = (255f - LIFT) / 255f
        val lift = ColorMatrixMath.of(
            k, 0f, 0f, 0f, LIFT,
            0f, k, 0f, 0f, LIFT,
            0f, 0f, k, 0f, LIFT,
            0f, 0f, 0f, 1f, 0f,
        )
        ColorMatrixMath.concat(lift, ColorMatrixMath.concat(hue, invert))
    }

    /** A paint that draws a layer through the dark-paper mapping. */
    fun layerPaint(): Paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix)) }

    /** [argb] as dark paper shows it (alpha unchanged). */
    fun map(argb: Int): Int = ColorMatrixMath.apply(matrix, argb)

    private const val LIFT = 30f
}

/** Whether pages are shown as dark paper right now (dark theme and the setting on). */
val LocalDarkPaper = staticCompositionLocalOf { false }

/** Plain colour-matrix arithmetic (Android's ColorMatrix layout), kept separate so it can be unit-tested. */
internal object ColorMatrixMath {
    fun of(vararg v: Float): FloatArray {
        require(v.size == 20)
        return v.copyOf()
    }

    /** a · b: apply [b] first, then [a]. */
    fun concat(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = if (col == 4) a[row * 5 + 4] else 0f
                for (k in 0 until 4) sum += a[row * 5 + k] * b[k * 5 + col]
                out[row * 5 + col] = sum
            }
        }
        return out
    }

    /** The standard luminance-preserving hue rotation. */
    fun hueRotation(degrees: Float): FloatArray {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return of(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    fun apply(m: FloatArray, argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        fun ch(row: Int) = (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] * a + m[row * 5 + 4]).toInt().coerceIn(0, 255)
        return (ch(3) shl 24) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
    }
}
