package com.appname.notes.feature.ink

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import com.appname.notes.core.ink.input.LaserTrail
import com.appname.notes.core.settings.LaserSettings
import com.appname.notes.core.settings.LaserStyle

/**
 * Draws the laser pointer's trail (owner brief §17) on top of the page, in screen pixels:
 * - [LaserStyle.SOLID]: a glowing coloured line;
 * - [LaserStyle.OUTLINE]: a coloured line with a bright white core, like a neon tube;
 * - [LaserStyle.DOT]: a glowing dot with a fading tail of dots.
 * Rainbow mode colours the trail by distance along it, flowing with time. Main thread only.
 *
 * The glow uses a blur, which needs Android 9+ with hardware drawing; elsewhere the line is drawn without glow.
 */
internal class LaserPainter {

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var glowRadius = -1f
    private var glow: BlurMaskFilter? = null
    private val hsv = FloatArray(3)

    fun draw(canvas: Canvas, trail: LaserTrail, now: Long, settings: LaserSettings, density: Float) {
        val points = trail.points(now)
        val w = settings.widthDp.coerceIn(1f, 40f) * density
        if (glowRadius != w) {
            glowRadius = w
            glow = BlurMaskFilter(w * GLOW_BLUR, BlurMaskFilter.Blur.NORMAL)
        }
        fun colorAt(hue: Float, alpha: Float): Int {
            val base = if (settings.rainbow) rainbow(hue) else settings.colorArgb
            return ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or (base and RGB)
        }

        when (settings.style) {
            LaserStyle.SOLID, LaserStyle.OUTLINE -> {
                // Three passes over the segments: glow, colour, (outline) white core.
                val passes = if (settings.style == LaserStyle.OUTLINE) 3 else 2
                for (pass in 0 until passes) {
                    line.maskFilter = if (pass == 0) glow else null
                    // Butt ends: round ends of fading segments would overlap at every joint and show as beads.
                    line.strokeCap = if (pass == 0) Paint.Cap.ROUND else Paint.Cap.BUTT
                    line.strokeWidth = when (pass) {
                        0 -> w * GLOW_WIDTH
                        1 -> w
                        else -> w * CORE_WIDTH
                    }
                    for (i in 1 until points.size) {
                        val a = points[i - 1]
                        val b = points[i]
                        if (a.stroke != b.stroke) continue
                        val alpha = b.alpha * when (pass) {
                            0 -> GLOW_ALPHA
                            1 -> 1f
                            else -> CORE_ALPHA
                        }
                        if (alpha <= 0.01f) continue
                        line.color = if (pass == 2) (((alpha * 255).toInt()) shl 24) or RGB else colorAt(b.hue, alpha)
                        canvas.drawLine(a.x, a.y, b.x, b.y, line)
                    }
                }
                line.maskFilter = null
                line.strokeCap = Paint.Cap.ROUND
            }
            LaserStyle.DOT -> {
                // A tail of shrinking dots, about one dot width apart.
                var lastX = Float.NaN
                var lastY = Float.NaN
                for (p in points) {
                    if (!lastX.isNaN() && (p.x - lastX) * (p.x - lastX) + (p.y - lastY) * (p.y - lastY) < w * w) continue
                    lastX = p.x
                    lastY = p.y
                    if (p.alpha <= 0.01f) continue
                    dot.maskFilter = null
                    dot.color = colorAt(p.hue, p.alpha)
                    canvas.drawCircle(p.x, p.y, w * 0.5f * (0.3f + 0.7f * p.alpha), dot)
                }
            }
        }

        // The bright head where the pointer is now.
        if (trail.down) {
            val c = colorAt(trail.headHue(now), 1f)
            dot.maskFilter = glow
            dot.color = (GLOW_HEAD_ALPHA shl 24) or (c and RGB)
            canvas.drawCircle(trail.headX, trail.headY, w * HEAD_GLOW, dot)
            dot.maskFilter = null
            dot.color = c
            canvas.drawCircle(trail.headX, trail.headY, w * HEAD, dot)
            if (settings.style != LaserStyle.SOLID) {
                dot.color = (0xE6 shl 24) or RGB
                canvas.drawCircle(trail.headX, trail.headY, w * HEAD * CORE_WIDTH, dot)
            }
        }
    }

    private fun rainbow(hue: Float): Int {
        hsv[0] = hue
        hsv[1] = 1f
        hsv[2] = 1f
        return android.graphics.Color.HSVToColor(hsv)
    }

    private companion object {
        const val RGB = 0xFFFFFF
        const val GLOW_WIDTH = 2.8f
        const val GLOW_BLUR = 0.9f
        const val GLOW_ALPHA = 0.45f
        const val CORE_WIDTH = 0.4f
        const val CORE_ALPHA = 0.95f
        const val HEAD = 0.85f
        const val HEAD_GLOW = 2.2f
        const val GLOW_HEAD_ALPHA = 0x80
    }
}
