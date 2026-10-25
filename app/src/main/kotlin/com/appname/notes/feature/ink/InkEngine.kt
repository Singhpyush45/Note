package com.appname.notes.feature.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.StrokePoints

/**
 * Bridge between our ink model and Jetpack Ink (ADR-0009).
 *
 * Ink is native code (C++ via JNI). If its library can't be loaded — JVM unit tests, or an unusual
 * device — [available] is false and the app draws with its own simple renderer instead of crashing
 * (SRS 1.3: degrade, don't fail).
 */
object InkEngine {

    /** Tests can force the fallback renderer. */
    @Volatile
    var forceFallback: Boolean = false

    val available: Boolean
        get() = !forceFallback && loaded

    private val loaded: Boolean by lazy {
        try {
            // Creating a brush touches the native library.
            Brush.createWithColorIntArgb(StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1), 0xFF000000.toInt(), 1f, EPSILON)
            true
        } catch (e: LinkageError) { // UnsatisfiedLinkError and friends
            false
        } catch (e: ExceptionInInitializerError) {
            false
        }
    }

    /**
     * Ink brush for our pen. Brush families are pinned to version 1, so saved handwriting keeps looking
     * the same after a library update.
     */
    fun brush(b: InkBrush): Brush {
        val family = when (b.tool) {
            InkTool.PEN -> StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1)
            InkTool.MARKER -> StockBrushes.marker(StockBrushes.MarkerVersion.V1)
            InkTool.HIGHLIGHTER -> StockBrushes.highlighter(version = StockBrushes.HighlighterVersion.V1)
            // Pencils and brushes are drawn by the app's own renderer (StyledInk); this is only a safe default.
            InkTool.PENCIL, InkTool.BRUSH -> StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1)
        }
        return Brush.createWithColorIntArgb(family, b.colorArgb, b.size, EPSILON)
    }

    /**
     * Rebuilds an Ink stroke from stored points, or null if Ink rejects them (then the fallback renderer
     * draws that stroke). Ink requires non-decreasing time and no two identical consecutive samples;
     * rounding on save can create such duplicates, so they are skipped here.
     */
    fun stroke(points: StrokePoints, brush: InkBrush): Stroke? {
        if (!available) return null
        return try {
            val batch = MutableStrokeInputBatch()
            val tool = if (points.pressure != null) InputToolType.STYLUS else InputToolType.TOUCH
            var lastX = Float.NaN
            var lastY = Float.NaN
            var lastT = Long.MIN_VALUE
            for (i in 0 until points.size) {
                val x = points.x[i]
                val y = points.y[i]
                val t = maxOf(points.t[i].toLong(), lastT)
                if (x == lastX && y == lastY && t == lastT) continue
                batch.add(
                    tool,
                    x,
                    y,
                    t,
                    StrokeInput.NO_STROKE_UNIT_LENGTH,
                    points.pressure?.get(i) ?: StrokeInput.NO_PRESSURE,
                    points.tilt?.get(i) ?: StrokeInput.NO_TILT,
                    points.orientation?.get(i) ?: StrokeInput.NO_ORIENTATION,
                )
                lastX = x; lastY = y; lastT = t
            }
            Stroke(brush(brush), batch)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: LinkageError) {
            null
        }
    }

    /** Smallest distance (page units) Ink treats as different points: 1/960 inch. */
    private const val EPSILON = 0.1f
}
