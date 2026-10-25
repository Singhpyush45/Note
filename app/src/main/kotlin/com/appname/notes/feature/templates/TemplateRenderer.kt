package com.appname.notes.feature.templates

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.appname.notes.core.ink.template.Box
import com.appname.notes.core.ink.template.Checklist
import com.appname.notes.core.ink.template.CircleRow
import com.appname.notes.core.ink.template.DotArea
import com.appname.notes.core.ink.template.LabelAlign
import com.appname.notes.core.ink.template.LinedArea
import com.appname.notes.core.ink.template.PaperStyle
import com.appname.notes.core.ink.template.Rule
import com.appname.notes.core.ink.template.Ruling
import com.appname.notes.core.ink.template.Staves
import com.appname.notes.core.ink.template.Table
import com.appname.notes.core.ink.template.TemplateColors
import com.appname.notes.core.ink.template.TemplateComponent
import com.appname.notes.core.ink.template.TemplateLayout
import com.appname.notes.core.ink.template.TextLabel

/**
 * Draws a [TemplateLayout] in page units onto a canvas already scaled to the page: the paper, its ruling and
 * every printed part. Used by the handwriting editor, note cards and the template screens, so a template
 * looks the same everywhere. Allocates nothing per frame (paints and rect are reused); main thread only.
 */
class TemplateRenderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    // LINEAR_TEXT: text stays smooth when the page is zoomed (no hinting snapped to unscaled pixels).
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()

    /** Paper colour, ruling and printed parts. */
    fun drawPage(canvas: Canvas, layout: TemplateLayout) {
        fill.color = layout.paperColor
        canvas.drawRect(0f, 0f, layout.width, layout.height, fill)
        drawDesign(canvas, layout)
    }

    /** Ruling and printed parts only (the caller has drawn the paper). */
    fun drawDesign(canvas: Canvas, layout: TemplateLayout) {
        val save = canvas.save()
        canvas.clipRect(0f, 0f, layout.width, layout.height)
        drawRuling(canvas, layout.paper, layout.width, layout.height)
        for (c in layout.components) draw(canvas, c)
        canvas.restoreToCount(save)
    }

    private fun drawRuling(canvas: Canvas, p: PaperStyle, w: Float, h: Float) {
        val s = p.spacing.coerceAtLeast(MIN_SPACING)
        val first = if (p.top > 0f) p.top else s
        when (p.ruling) {
            Ruling.NONE -> Unit
            Ruling.LINES -> {
                line(p.lineColor, THIN)
                var y = first
                while (y < h - s / 2) { canvas.drawLine(0f, y, w, y, stroke); y += s }
            }
            Ruling.GRID -> {
                line(p.lineColor, THIN)
                var x = s
                while (x < w) { canvas.drawLine(x, first - s, x, h, stroke); x += s }
                var y = first
                while (y < h) { canvas.drawLine(0f, y, w, y, stroke); y += s }
            }
            Ruling.DOTS -> {
                fill.color = p.lineColor
                var y = first
                while (y < h) {
                    var x = s
                    while (x < w) { canvas.drawCircle(x, y, DOT_RADIUS, fill); x += s }
                    y += s
                }
            }
        }
        p.marginX?.let { m ->
            line(TemplateColors.MARGIN, THIN)
            canvas.drawLine(m, 0f, m, h, stroke)
        }
    }

    private fun draw(canvas: Canvas, c: TemplateComponent) {
        when (c) {
            is TextLabel -> {
                text.color = c.color
                text.textSize = c.size
                text.typeface = if (c.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                text.textAlign = when (c.align) {
                    LabelAlign.START -> Paint.Align.LEFT
                    LabelAlign.CENTER -> Paint.Align.CENTER
                    LabelAlign.END -> Paint.Align.RIGHT
                }
                canvas.drawText(c.text, c.x, c.y, text)
            }
            is Box -> {
                rect.set(c.x, c.y, c.x + c.w, c.y + c.h)
                c.fill?.let { fill.color = it; canvas.drawRoundRect(rect, c.radius, c.radius, fill) }
                line(c.stroke, c.strokeWidth)
                canvas.drawRoundRect(rect, c.radius, c.radius, stroke)
            }
            is Rule -> {
                line(c.color, c.width)
                canvas.drawLine(c.x1, c.y1, c.x2, c.y2, stroke)
            }
            is LinedArea -> {
                line(c.color, THIN)
                val s = c.spacing.coerceAtLeast(MIN_SPACING)
                var y = c.y + s
                while (y <= c.y + c.h + 0.01f) { canvas.drawLine(c.x, y, c.x + c.w, y, stroke); y += s }
            }
            is DotArea -> {
                fill.color = c.color
                val s = c.spacing.coerceAtLeast(MIN_SPACING)
                var y = c.y + s / 2
                while (y < c.y + c.h) {
                    var x = c.x + s / 2
                    while (x < c.x + c.w) { canvas.drawCircle(x, y, DOT_RADIUS, fill); x += s }
                    y += s
                }
            }
            is Checklist -> drawChecklist(canvas, c)
            is Table -> drawTable(canvas, c)
            is Staves -> {
                line(c.color, THIN)
                for (top in c.staffTops()) {
                    for (i in 0..4) {
                        val y = top + i * c.lineGap
                        canvas.drawLine(c.x, y, c.x + c.w, y, stroke)
                    }
                    canvas.drawLine(c.x, top, c.x, top + 4 * c.lineGap, stroke)
                    canvas.drawLine(c.x + c.w, top, c.x + c.w, top + 4 * c.lineGap, stroke)
                }
            }
            is CircleRow -> drawCircles(canvas, c)
        }
    }

    private fun drawChecklist(canvas: Canvas, c: Checklist) {
        val s = c.spacing.coerceAtLeast(MIN_SPACING)
        val box = (s * 0.46f).coerceAtMost(16f)
        for (i in 0 until c.rows) {
            val bottom = c.y + (i + 1) * s
            val boxTop = bottom - box - (s - box) * 0.35f
            rect.set(c.x, boxTop, c.x + box, boxTop + box)
            line(c.color, 1.3f)
            canvas.drawRoundRect(rect, 3f, 3f, stroke)
            line(TemplateColors.RULE, THIN)
            canvas.drawLine(c.x + box + 10f, bottom - 2f, c.x + c.w, bottom - 2f, stroke)
        }
    }

    private fun drawTable(canvas: Canvas, t: Table) {
        val cols = t.columnEdges()
        val rows = t.rowEdges()
        val headerFill = t.headerFill
        if (t.hasHeader && headerFill != null) {
            fill.color = headerFill
            canvas.drawRect(t.x, t.y, t.x + t.w, t.y + t.headerHeight, fill)
        }
        line(t.color, 1.2f)
        for (x in cols.subList(1, cols.size - 1)) canvas.drawLine(x, t.y, x, t.y + t.h, stroke)
        for (y in rows.subList(0, rows.size - 1)) if (y > t.y) canvas.drawLine(t.x, y, t.x + t.w, y, stroke)
        rect.set(t.x, t.y, t.x + t.w, t.y + t.h)
        line(t.color, 1.5f)
        canvas.drawRect(rect, stroke)

        text.textSize = t.labelSize
        text.textAlign = Paint.Align.CENTER
        text.typeface = Typeface.DEFAULT_BOLD
        text.color = TemplateColors.TITLE
        val headerBaseline = t.y + t.headerHeight / 2 + t.labelSize * 0.35f
        t.headers.forEachIndexed { i, label ->
            if (i < cols.size - 1 && label.isNotEmpty()) canvas.drawText(label, (cols[i] + cols[i + 1]) / 2, headerBaseline, text)
        }
        text.textAlign = Paint.Align.LEFT
        text.typeface = Typeface.DEFAULT
        text.color = TemplateColors.LABEL
        t.rowLabels.forEachIndexed { i, label ->
            if (i < rows.size - 1) {
                val baseline = (rows[i] + rows[i + 1]) / 2 + t.labelSize * 0.35f
                canvas.drawText(label, cols[0] + 6f, baseline, text)
            }
        }
    }

    private fun drawCircles(canvas: Canvas, c: CircleRow) {
        line(c.color, 1.3f)
        val r = c.diameter / 2
        for (i in 0 until c.count) {
            canvas.drawCircle(c.x + r + i * (c.diameter + c.gap), c.y + r, r, stroke)
        }
    }

    private fun line(color: Int, width: Float) {
        stroke.color = color
        stroke.strokeWidth = width
    }

    private companion object {
        const val THIN = 0.9f
        const val DOT_RADIUS = 1.2f
        const val MIN_SPACING = 6f
    }
}
