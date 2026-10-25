package com.appname.notes.feature.ink

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.withRotation
import com.appname.notes.core.ink.input.Ruler
import kotlin.math.roundToInt

/**
 * Draws the ruler on top of the page (screen pixels): a frosted strip with millimetre-like ticks every 4 dp,
 * longer ones every 20 dp, its angle in the middle, and the turning knob at the right end.
 */
internal class RulerPainter(private val density: Float) {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9F5F7FA.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5F6368.toInt(); style = Paint.Style.STROKE; strokeWidth = density }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3C4043.toInt(); strokeWidth = density * 0.8f }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF202124.toInt(); textAlign = Paint.Align.CENTER; textSize = 12 * density }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A73E8.toInt() }
    private val knobRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2 * density }
    private val rect = RectF()

    fun draw(canvas: Canvas, r: Ruler) {
        val cx = r.cx.toFloat()
        val cy = r.cy.toFloat()
        val hl = (r.length / 2).toFloat()
        val hw = (r.width / 2).toFloat()
        canvas.withRotation(r.angleDeg.toFloat(), cx, cy) {
            rect.set(cx - hl, cy - hw, cx + hl, cy + hw)
            drawRoundRect(rect, 6 * density, 6 * density, body)
            drawRoundRect(rect, 6 * density, 6 * density, edge)
            // Ticks along both long edges.
            val step = 4 * density
            var i = 0
            var x = cx - hl + 8 * density
            while (x < cx + hl - 8 * density) {
                val len = when {
                    i % 10 == 0 -> 14 * density
                    i % 5 == 0 -> 10 * density
                    else -> 6 * density
                }
                drawLine(x, cy - hw, x, cy - hw + len, tick)
                drawLine(x, cy + hw, x, cy + hw - len, tick)
                x += step
                i++
            }
            val angle = ((r.angleDeg % 360 + 360) % 360).roundToInt()
            drawText("$angle°", cx, cy + label.textSize / 3, label)
            // The knob is drawn in ruler space (unrotated): its distance along the ruler from the centre.
            val kx = cx + (hl - hw)
            drawCircle(kx, cy, 11 * density, knob)
            drawCircle(kx, cy, 11 * density, knobRing)
        }
    }
}
