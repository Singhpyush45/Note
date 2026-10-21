package com.appname.notes.feature.ink

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import com.appname.notes.core.ink.model.arrowHeadPoints

/** Paints shared by everything that draws ink. Colours of the paper are fixed: ink is drawn on "paper". */
internal object InkPaints {
    const val PAPER = Color.WHITE
    const val RULE = 0xFFD3DCE4.toInt()
    const val MARGIN = 0xFFF2B8B8.toInt()
    const val DOT = 0xFFB9C4CE.toInt()
    const val SELECTION = 0xFF1A73E8.toInt()

    fun inkPaint(brush: InkBrush) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = brush.colorArgb
        strokeWidth = brush.size
        strokeCap = if (brush.tool == InkTool.HIGHLIGHTER) Paint.Cap.SQUARE else Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
}

/** Builds Android paths for our ink, in page coordinates. Used by the fallback renderer and thumbnails. */
internal object InkPaths {

    fun shape(g: ShapeGeometry): Path = Path().apply {
        when (g) {
            is ShapeGeometry.Segment -> {
                moveTo(g.start.x, g.start.y)
                lineTo(g.end.x, g.end.y)
                if (g.arrowHead > 0f) {
                    val (h1, h2) = arrowHeadPoints(g)
                    moveTo(h1.x.toFloat(), h1.y.toFloat())
                    lineTo(g.end.x, g.end.y)
                    lineTo(h2.x.toFloat(), h2.y.toFloat())
                }
            }
            is ShapeGeometry.Ellipse -> {
                addOval(RectF(g.center.x - g.rx, g.center.y - g.ry, g.center.x + g.rx, g.center.y + g.ry), Path.Direction.CW)
                if (g.rotationDeg != 0f) transform(Matrix().apply { setRotate(g.rotationDeg, g.center.x, g.center.y) })
            }
            is ShapeGeometry.Polygon -> {
                g.vertices.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                close()
            }
        }
    }

    /** Smooth path through the points (quadratic curves through the midpoints). */
    fun stroke(p: StrokePoints): Path = Path().apply {
        if (p.size == 0) return@apply
        moveTo(p.x[0], p.y[0])
        if (p.size == 1) {
            lineTo(p.x[0] + 0.01f, p.y[0])
            return@apply
        }
        for (i in 1 until p.size - 1) {
            quadTo(p.x[i], p.y[i], (p.x[i] + p.x[i + 1]) / 2, (p.y[i] + p.y[i + 1]) / 2)
        }
        lineTo(p.x[p.size - 1], p.y[p.size - 1])
    }

    /**
     * Fallback stroke drawing: pressure changes the width segment by segment, like a simple pen.
     * Canvas must already be in page coordinates.
     */
    fun drawStroke(canvas: Canvas, item: StrokeItem, paint: Paint) {
        val p = item.points
        val pressure = p.pressure
        if (pressure == null || item.brush.tool != InkTool.PEN) {
            canvas.drawPath(stroke(p), paint)
            return
        }
        val base = item.brush.size
        for (i in 0 until maxOf(1, p.size - 1)) {
            val j = minOf(i + 1, p.size - 1)
            paint.strokeWidth = base * (0.35f + 0.9f * (pressure[i] + pressure[j]) / 2)
            canvas.drawLine(p.x[i], p.y[i], p.x[j] + if (i == j) 0.01f else 0f, p.y[j], paint)
        }
        paint.strokeWidth = base
    }

    /** Fallback drawing of any item; canvas in page coordinates. */
    fun drawItem(canvas: Canvas, item: InkItem, paint: Paint = InkPaints.inkPaint(item.brush)) {
        when (item) {
            is StrokeItem -> drawStroke(canvas, item, paint)
            is ShapeItem -> canvas.drawPath(shape(item.geometry), paint)
        }
    }
}

/**
 * Draws pages and their ink onto a [Canvas]: paper, template, highlighter ink under everything else,
 * then pens, markers and shapes. Strokes use Jetpack Ink when available, otherwise [InkPaths].
 */
internal class InkRenderer {
    private val inkRenderer: CanvasStrokeRenderer? = if (InkEngine.available) CanvasStrokeRenderer.create() else null

    /** Ink stroke per item id, rebuilt when the item object changes (moved, recoloured …). */
    private val strokeCache = HashMap<String, Pair<StrokeItem, Stroke?>>()
    private val shapeCache = HashMap<String, Pair<ShapeItem, Path>>()

    private val paperPaint = Paint().apply { color = InkPaints.PAPER }
    private val shadowPaint = Paint().apply { color = 0x22000000 }
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkPaints.RULE; strokeWidth = 0.8f }
    private val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkPaints.MARGIN; strokeWidth = 0.8f }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkPaints.DOT }
    private val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Forgets cached strokes of items that no longer exist. */
    fun retain(ids: Set<String>) {
        strokeCache.keys.retainAll(ids)
        shapeCache.keys.retainAll(ids)
    }

    /** Paper with shadow and template. [pageToScreen] maps page units to the canvas. */
    fun drawPaper(canvas: Canvas, width: Float, height: Float, template: PageTemplate, pageToScreen: Matrix, shadowPx: Float) {
        val r = RectF(0f, 0f, width, height)
        pageToScreen.mapRect(r)
        canvas.drawRect(r.left + shadowPx, r.top + shadowPx, r.right + shadowPx, r.bottom + shadowPx, shadowPaint)
        canvas.drawRect(r, paperPaint)
        canvas.save()
        canvas.concat(pageToScreen)
        drawTemplate(canvas, width, height, template)
        canvas.restore()
    }

    private fun drawTemplate(canvas: Canvas, w: Float, h: Float, template: PageTemplate) {
        when (template) {
            PageTemplate.BLANK -> Unit
            PageTemplate.LINED -> {
                val s = PageTemplate.LINE_SPACING
                var y = s * 3
                while (y < h - s / 2) {
                    canvas.drawLine(0f, y, w, y, rulePaint)
                    y += s
                }
                canvas.drawLine(LINED_MARGIN, 0f, LINED_MARGIN, h, marginPaint)
            }
            PageTemplate.GRID -> {
                val s = PageTemplate.GRID_SPACING
                var x = s
                while (x < w) { canvas.drawLine(x, 0f, x, h, rulePaint); x += s }
                var y = s
                while (y < h) { canvas.drawLine(0f, y, w, y, rulePaint); y += s }
            }
            PageTemplate.DOTTED -> {
                val s = PageTemplate.GRID_SPACING
                var y = s
                while (y < h) {
                    var x = s
                    while (x < w) { canvas.drawCircle(x, y, 1.1f, dotPaint); x += s }
                    y += s
                }
            }
        }
    }

    /**
     * Ink of one page, clipped to the page. Items in [hidden] are skipped (e.g. being dragged);
     * [translated] items are drawn moved by ([dx], [dy]) page units.
     */
    fun drawInk(
        canvas: Canvas,
        items: List<InkItem>,
        width: Float,
        height: Float,
        pageToScreen: Matrix,
        hidden: Set<String> = emptySet(),
        translated: Set<String> = emptySet(),
        dx: Float = 0f,
        dy: Float = 0f,
    ) {
        canvas.save()
        canvas.concat(pageToScreen)
        canvas.clipRect(0f, 0f, width, height)
        // Highlighter first so writing stays crisp on top of it.
        for (pass in 0..1) {
            for (item in items) {
                val isHighlighter = item.brush.tool == InkTool.HIGHLIGHTER
                if ((pass == 0) != isHighlighter || item.id in hidden) continue
                if (item.id in translated) {
                    canvas.save()
                    canvas.translate(dx, dy)
                    val m = Matrix(pageToScreen).apply { preTranslate(dx, dy) }
                    drawItem(canvas, item, m)
                    canvas.restore()
                } else {
                    drawItem(canvas, item, pageToScreen)
                }
            }
        }
        canvas.restore()
    }

    /** Draws one item; canvas already in page coordinates, [transform] is that same transform. */
    fun drawItem(canvas: Canvas, item: InkItem, transform: Matrix) {
        when (item) {
            is StrokeItem -> {
                val stroke = inkStroke(item)
                val r = inkRenderer
                if (stroke != null && r != null) {
                    // Ink needs the canvas transform to pick the right detail and anti-aliasing.
                    r.draw(canvas, stroke, transform)
                } else {
                    paintFor(item.brush)
                    InkPaths.drawStroke(canvas, item, itemPaint)
                }
            }
            is ShapeItem -> {
                val cached = shapeCache[item.id]
                val path = if (cached != null && cached.first === item) cached.second else InkPaths.shape(item.geometry).also { shapeCache[item.id] = item to it }
                paintFor(item.brush)
                canvas.drawPath(path, itemPaint)
            }
        }
    }

    /** A live stroke drawn by us (only when Ink isn't available). */
    fun drawLive(canvas: Canvas, brush: InkBrush, points: StrokePoints, pageToScreen: Matrix) {
        canvas.save()
        canvas.concat(pageToScreen)
        paintFor(brush)
        InkPaths.drawStroke(canvas, StrokeItem("live", 0, brush, points), itemPaint)
        canvas.restore()
    }

    private fun paintFor(brush: InkBrush) {
        itemPaint.style = Paint.Style.STROKE
        itemPaint.color = brush.colorArgb
        itemPaint.strokeWidth = brush.size
        itemPaint.strokeCap = if (brush.tool == InkTool.HIGHLIGHTER) Paint.Cap.SQUARE else Paint.Cap.ROUND
        itemPaint.strokeJoin = Paint.Join.ROUND
    }

    private fun inkStroke(item: StrokeItem): Stroke? {
        if (inkRenderer == null) return null
        val cached = strokeCache[item.id]
        if (cached != null && cached.first === item) return cached.second
        val s = InkEngine.stroke(item.points, item.brush)
        strokeCache[item.id] = item to s
        return s
    }

    // ---------------------------------------------------------------- overlays (screen space)

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = InkPaints.SELECTION
    }
    private val selectionFill = Paint().apply { color = 0x141A73E8 }
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x99000000.toInt()
    }

    fun drawSelection(canvas: Canvas, rectScreen: RectF, density: Float) {
        selectionPaint.strokeWidth = 1.5f * density
        selectionPaint.pathEffect = DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)
        canvas.drawRect(rectScreen, selectionFill)
        canvas.drawRect(rectScreen, selectionPaint)
    }

    fun drawLasso(canvas: Canvas, pathScreen: List<Vec>, density: Float) {
        if (pathScreen.size < 2) return
        selectionPaint.strokeWidth = 1.5f * density
        selectionPaint.pathEffect = DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)
        val path = Path()
        pathScreen.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x.toFloat(), p.y.toFloat()) else path.lineTo(p.x.toFloat(), p.y.toFloat()) }
        canvas.drawPath(path, selectionPaint)
    }

    fun drawEraser(canvas: Canvas, center: Vec, radiusPx: Float, density: Float) {
        eraserPaint.strokeWidth = 1f * density
        canvas.drawCircle(center.x.toFloat(), center.y.toFloat(), radiusPx, eraserPaint)
    }

    private companion object {
        const val LINED_MARGIN = 64f
    }
}
