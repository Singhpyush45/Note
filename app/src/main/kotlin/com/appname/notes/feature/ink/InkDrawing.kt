package com.appname.notes.feature.ink

import com.appname.notes.core.ink.model.TextItem
import com.appname.notes.core.ink.model.ImageItem
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.withMatrix
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
/** Paint order of a page: photos (0), highlighter (1), everything else (2), each in z order. */
internal object InkLayers {
    val PASSES = 0..2

    fun of(item: InkItem): Int = when {
        item is ImageItem -> 0
        item.brush.tool == InkTool.HIGHLIGHTER && item !is TextItem -> 1
        else -> 2
    }
}

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

    /**
     * Drawing of any item without Jetpack Ink (thumbnails, PDF export); canvas in page coordinates. Variants
     * (pencils, brushes …) are drawn by [styled], one per thread.
     */
    fun drawItem(canvas: Canvas, item: InkItem, paint: Paint = InkPaints.inkPaint(item.brush), styled: StyledInk? = null, syncImages: Boolean = true) {
        when (item) {
            is StrokeItem -> if (item.brush.usesJetpack) drawStroke(canvas, item, paint) else (styled ?: StyledInk(cache = false)).draw(canvas, item)
            is ShapeItem -> {
                val path = shape(item.geometry)
                drawFill(canvas, item, path)
                canvas.drawPath(path, paint)
            }
            // Export and thumbnails run off the main thread: photos are decoded right here.
            is ImageItem -> PageObjectPainter.drawImage(canvas, item, canvas.matrix.mapRadius(1f).coerceAtLeast(1f), sync = syncImages)
            is TextItem -> PageObjectPainter.drawText(canvas, item)
            is com.appname.notes.core.ink.model.AudioItem -> PageObjectPainter.drawAudio(canvas, item)
        }
    }

    private val fillPaint = ThreadLocal.withInitial { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }

    /** A filled shape's inside, under its outline (owner brief §14–15). */
    fun drawFill(canvas: Canvas, item: ShapeItem, path: Path) {
        val fill = item.fill ?: return
        val p = fillPaint.get()!!
        val end = item.fillEnd
        if (end != null) {
            val b = android.graphics.RectF()
            @Suppress("DEPRECATION") path.computeBounds(b, true)
            p.shader = android.graphics.LinearGradient(b.left, b.top, b.right, b.bottom, fill, end, android.graphics.Shader.TileMode.CLAMP)
        } else {
            p.shader = null
            p.color = fill
        }
        canvas.drawPath(path, p)
        p.shader = null
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
    private val styled = StyledInk()

    private val paperPaint = Paint().apply { color = InkPaints.PAPER }
    private val shadowPaint = Paint().apply { color = 0x22000000 }
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkPaints.RULE; strokeWidth = 0.8f }
    private val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkPaints.MARGIN; strokeWidth = 0.8f }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkPaints.DOT }
    private val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paperRect = RectF()
    private val combined = Matrix()

    /** Forgets cached strokes of items that no longer exist. */
    fun retain(ids: Set<String>) {
        strokeCache.keys.retainAll(ids)
        shapeCache.keys.retainAll(ids)
        styled.retain(ids)
    }

    private val templateRenderer = com.appname.notes.feature.templates.TemplateRenderer()

    /**
     * Paper with shadow and its background: the page's template design ([layout], owner brief §4) or the plain
     * [template]. [pageToScreen] maps page units to the canvas.
     */
    fun drawPaper(
        canvas: Canvas,
        width: Float,
        height: Float,
        template: PageTemplate,
        pageToScreen: Matrix,
        shadowPx: Float,
        layout: com.appname.notes.core.ink.template.TemplateLayout? = null,
    ) {
        val r = paperRect
        r.set(0f, 0f, width, height)
        pageToScreen.mapRect(r)
        canvas.drawRect(r.left + shadowPx, r.top + shadowPx, r.right + shadowPx, r.bottom + shadowPx, shadowPaint)
        if (layout != null) {
            paperPaint.color = layout.paperColor
            canvas.drawRect(r, paperPaint)
            paperPaint.color = InkPaints.PAPER
            // The design is drawn at the page's own size, whatever size the template was made for.
            val zoom = pageToScreen.mapRadius(1f)
            canvas.withMatrix(pageToScreen) { templateRenderer.drawDesign(this, layout.resized(width, height), zoom) }
            return
        }
        canvas.drawRect(r, paperPaint)
        canvas.withMatrix(pageToScreen) { drawTemplate(this, width, height, template) }
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
     * Ink of one page, clipped to the page. Items in [hidden] are skipped; [transformed] items are drawn
     * with [itemTransform] (page units) applied first, e.g. while a selection is dragged or resized.
     */
    fun drawInk(
        canvas: Canvas,
        items: List<InkItem>,
        width: Float,
        height: Float,
        pageToScreen: Matrix,
        hidden: Set<String> = emptySet(),
        transformed: Set<String> = emptySet(),
        itemTransform: Matrix? = null,
    ) {
        canvas.withMatrix(pageToScreen) {
            clipRect(0f, 0f, width, height)
            // Photos first, then highlighter, then writing, so writing stays crisp on top.
            for (pass in InkLayers.PASSES) {
                for (item in items) {
                    if (InkLayers.of(item) != pass || item.id in hidden) continue
                    if (itemTransform != null && item.id in transformed) {
                        combined.set(pageToScreen)
                        combined.preConcat(itemTransform)
                        withMatrix(itemTransform) { drawItem(this, item, combined) }
                    } else {
                        drawItem(this, item, pageToScreen)
                    }
                }
            }
        }
    }

    /** Draws one item; canvas already in page coordinates, [transform] is that same transform. */
    fun drawItem(canvas: Canvas, item: InkItem, transform: Matrix) {
        when (item) {
            is StrokeItem -> {
                if (!item.brush.usesJetpack) {
                    styled.draw(canvas, item)
                    return
                }
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
                InkPaths.drawFill(canvas, item, path)
                paintFor(item.brush)
                canvas.drawPath(path, itemPaint)
            }
            is ImageItem -> PageObjectPainter.drawImage(canvas, item, transform.mapRadius(1f))
            is TextItem -> PageObjectPainter.drawText(canvas, item)
            is com.appname.notes.core.ink.model.AudioItem -> PageObjectPainter.drawAudio(canvas, item)
        }
    }

    /** A live stroke drawn by us: variants always (so wet ink looks like dry ink), classic ink when Ink isn't available. */
    fun drawLive(canvas: Canvas, brush: InkBrush, points: StrokePoints, pageToScreen: Matrix) {
        val live = StrokeItem(LIVE_ID, 0, brush, points)
        canvas.withMatrix(pageToScreen) {
            if (!brush.usesJetpack) {
                liveStyled.draw(this, live)
            } else {
                paintFor(brush)
                InkPaths.drawStroke(this, live, itemPaint)
            }
        }
    }

    /** The live stroke changes every frame: never cached. */
    private val liveStyled = StyledInk(cache = false)

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

    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    /** The resize handle: a ring at the selection's bottom-right corner. */
    fun drawHandle(canvas: Canvas, x: Float, y: Float, density: Float) {
        selectionPaint.pathEffect = null
        selectionPaint.strokeWidth = 2f * density
        canvas.drawCircle(x, y, 7 * density, handleFill)
        canvas.drawCircle(x, y, 7 * density, selectionPaint)
    }

    /** The rotate handle: a ring with a turning arrow above the selection, joined to it by a short line. */
    fun drawRotateHandle(canvas: Canvas, x: Float, topOfSelection: Float, y: Float, density: Float) {
        selectionPaint.pathEffect = null
        selectionPaint.strokeWidth = 1.5f * density
        canvas.drawLine(x, topOfSelection, x, y + 9 * density, selectionPaint)
        canvas.drawCircle(x, y, 9 * density, handleFill)
        selectionPaint.strokeWidth = 2f * density
        canvas.drawCircle(x, y, 9 * density, selectionPaint)
        val r = 4.5f * density
        canvas.drawArc(x - r, y - r, x + r, y + r, -60f, 270f, false, selectionPaint)
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
        const val LIVE_ID = "live"
    }
}
