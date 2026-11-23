package com.appname.notes.feature.ink

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.graphics.ColorUtils

/**
 * Dark paper in dark theme (owner brief §5, §19): pages and ink are shown on a comfortable charcoal surface
 * while the note itself keeps its real colours (switching back to Light theme or exporting a PDF uses the
 * stored colours unchanged).
 *
 * How: a colour matrix that inverts R, G, B and rotates the hue by 180° (the standard "invert lightness, keep
 * hue" transform), then lifts the dark end to [PAPER_DARK] so pure white paper becomes a restful charcoal
 * (#1E2024) rather than harsh pitch black. Black ink becomes crisp off-white; coloured ink keeps its hue.
 */
object DarkPaper {

    private const val PAPER_LIFT = 30f
    private const val SCALE = (255f - PAPER_LIFT) / 255f

    /** White paper mapped through the dark-paper transform: comfortable charcoal (#1E1E1E..#202226). */
    const val PAPER_DARK = 0xFF1E2024.toInt()

    /** Surrounding workspace canvas colour in dark mode, slightly darker than the paper for clear sheet contrast. */
    const val WORKSPACE_DARK = 0xFF131518.toInt()

    /** Surrounding workspace canvas colour in light mode. */
    const val WORKSPACE_LIGHT = 0xFFF1F3F6.toInt()

    private val matrix: ColorMatrix by lazy {
        // 1. Invert RGB: (R, G, B) -> (255 - R, 255 - G, 255 - B). flips both lightness and hue.
        val invert = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        // 2. Rotate hue by 180° in luminance-preserving space so hues return to where they started.
        //    Standard luma weights: Wr = 0.213, Wg = 0.715, Wb = 0.072; for 180° (cos = -1, sin = 0)
        //    each row becomes 2 * W_col - I.
        val r = 0.213f
        val g = 0.715f
        val b = 0.072f
        val hue180 = ColorMatrix(
            floatArrayOf(
                2 * r - 1f, 2 * g, 2 * b, 0f, 0f,
                2 * r, 2 * g - 1f, 2 * b, 0f, 0f,
                2 * r, 2 * g, 2 * b - 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        // 3. Lift [0, 255] into [PAPER_LIFT, 255] so white paper becomes charcoal instead of pure black.
        val lift = ColorMatrix(
            floatArrayOf(
                SCALE, 0f, 0f, 0f, PAPER_LIFT,
                0f, SCALE, 0f, 0f, PAPER_LIFT,
                0f, 0f, SCALE, 0f, PAPER_LIFT,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        invert.apply {
            postConcat(hue180)
            postConcat(lift)
        }
    }

    private val filter: ColorMatrixColorFilter by lazy { ColorMatrixColorFilter(matrix) }

    /** Paint for `Canvas.saveLayer` so everything drawn into the layer is shown as dark paper. */
    fun layerPaint(): Paint = Paint().apply { colorFilter = filter }

    /**
     * Maps one ARGB colour the same way (for Jetpack Ink's live-stroke view and Compose previews that don't go
     * through [layerPaint]). Alpha is kept.
     */
    fun map(argb: Int): Int {
        val a = Color.alpha(argb)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        // Invert lightness and compress into the lifted range, keeping hue and saturation.
        val invL = 1f - hsl[2]
        hsl[2] = (PAPER_LIFT / 255f + invL * SCALE).coerceIn(0f, 1f)
        val rgb = ColorUtils.HSLToColor(hsl) and 0xFFFFFF
        return (a shl 24) or rgb
    }
}

/** True when handwritten pages should be shown as dark paper here (dark theme + the user's setting is on). */
val LocalDarkPaper = staticCompositionLocalOf { false }
