package com.appname.notes.core.ink.input

import com.appname.notes.core.ink.codec.InkCodec
import com.appname.notes.core.ink.edit.Eraser
import com.appname.notes.core.ink.edit.InkEdit
import com.appname.notes.core.ink.edit.InkSession
import com.appname.notes.core.ink.edit.Lasso
import com.appname.notes.core.ink.edit.Similarity
import com.appname.notes.core.ink.edit.transformed
import com.appname.notes.core.ink.geometry.Rect
import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.geometry.bounds
import com.appname.notes.core.ink.geometry.dist
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import com.appname.notes.core.ink.model.toPt
import com.appname.notes.core.ink.recognition.HoldDetector
import com.appname.notes.core.ink.recognition.ShapeRecognizer

/** Tools in the editor's toolbar. */
enum class EditorTool { PEN, MARKER, HIGHLIGHTER, ERASER, LASSO }

/** Stroke eraser removes whole strokes and shapes; area eraser removes only the ink under it. */
enum class EraserMode { STROKE, AREA }

/** What produced the input. The view decides whether a finger draws or scrolls before calling us. */
enum class PointerSource { STYLUS, STYLUS_ERASER, FINGER, MOUSE }

/**
 * One input sample in page units. Optional channels are negative when the device doesn't report them
 * (same convention as Jetpack Ink).
 */
data class Sample(
    val x: Double,
    val y: Double,
    val timeMillis: Long,
    val pressure: Float = -1f,
    val tilt: Float = -1f,
    val orientation: Float = -1f,
)

data class InkToolState(
    val tool: EditorTool = EditorTool.PEN,
    val brushes: Map<InkTool, InkBrush> = DefaultBrushes,
    val eraserMode: EraserMode = EraserMode.STROKE,
    /** Eraser diameter on screen. */
    val eraserSizeDp: Double = 16.0,
    val shapeCorrection: Boolean = true,
    val holdMillis: Long = HoldDetector.DEFAULT_HOLD_MILLIS,
) {
    fun brushFor(tool: EditorTool): InkBrush? = when (tool) {
        EditorTool.PEN -> brushes[InkTool.PEN]
        EditorTool.MARKER -> brushes[InkTool.MARKER]
        EditorTool.HIGHLIGHTER -> brushes[InkTool.HIGHLIGHTER]
        EditorTool.ERASER, EditorTool.LASSO -> null
    }
}

val DefaultBrushes: Map<InkTool, InkBrush> = mapOf(
    InkTool.PEN to InkBrush(InkTool.PEN, 0xFF1F1F1F.toInt(), 2.5f),
    InkTool.MARKER to InkBrush(InkTool.MARKER, 0xFF1565C0.toInt(), 5f),
    InkTool.HIGHLIGHTER to InkBrush(InkTool.HIGHLIGHTER, 0x66FFEB3B, 18f),
)

/** Lasso selection on one page. */
data class Selection(val pageId: String, val ids: Set<String>, val bounds: Rect)

/** What the view should do with Jetpack Ink's live (low-latency) stroke after a call. */
enum class LiveStroke { NONE, START, CANCEL, FINISH }

data class Response(
    val live: LiveStroke = LiveStroke.NONE,
    val redraw: Boolean = false,
    /** A shape just snapped: a short vibration confirms it. */
    val haptic: Boolean = false,
) {
    companion object {
        val NONE = Response()
        val REDRAW = Response(redraw = true)
    }
}

/**
 * Turns pen/finger input into changes on an [InkSession]: drawing, draw-and-hold shape correction,
 * erasing, lasso select and move (SRS 6.2).
 *
 * One gesture at a time: [down] → [move]… → [up], or [cancel]. The view also calls [tick] from a timer
 * while the pen is down, because a perfectly still pen may send no samples. All coordinates are page
 * units of the page the gesture started on; [unitsPerDp] converts on-screen sizes (tolerances, eraser)
 * so they feel the same at any zoom.
 */
class InkInputController(
    private val session: InkSession,
    private val newId: () -> String,
) {
    var tools: InkToolState = InkToolState()
        set(value) {
            if (value.tool != field.tool) {
                cancel()
                selection = null
            }
            field = value
        }

    /** True while the note can't be changed (unreadable newer data, trashed). */
    var readOnly: Boolean = false

    var selection: Selection? = null
        private set

    // ---------------------------------------------------------------- overlay state the view draws

    /** Corrected shape shown while the pen is held. */
    var shapePreview: ShapeItem? = null
        private set

    /** Lasso path being drawn. */
    var lassoPath: List<Vec> = emptyList()
        private set

    /** How far the selection is being dragged (drawn translated until the pen lifts). */
    var selectionOffset: Vec = ZERO
        private set

    /** Eraser position while erasing (to show its outline). */
    var eraserAt: Vec? = null
        private set

    val eraserRadius: Double get() = tools.eraserSizeDp / 2 * unitsPerDp

    /** The page of the running gesture. */
    val activePageId: String? get() = gesture?.pageId

    /** Points of the stroke being drawn (for the fallback renderer when Ink isn't available). */
    fun liveStroke(): Pair<InkBrush, StrokePoints>? {
        val g = gesture as? Gesture.Draw ?: return null
        if (g.shape != null || g.points.size == 0) return null
        return g.brush to g.points.build()
    }

    // ---------------------------------------------------------------- gestures

    private sealed interface Gesture {
        val pageId: String

        class Draw(
            override val pageId: String,
            val brush: InkBrush,
            val points: StrokePoints.Builder,
            val startTime: Long,
            val hold: HoldDetector,
            var lastT: Int = 0,
            var shape: Snap? = null,
        ) : Gesture

        class Erase(override val pageId: String, var last: Vec) : Gesture
        class LassoDraw(override val pageId: String) : Gesture
        class MoveSelection(override val pageId: String, val start: Vec) : Gesture
    }

    /** A snapped shape and the point where the pen was held (for resizing by dragging). */
    private class Snap(val base: ShapeGeometry, val anchor: Vec)

    private var gesture: Gesture? = null
    private var unitsPerDp = 1.0

    val gestureActive: Boolean get() = gesture != null

    fun down(pageId: String, s: Sample, source: PointerSource, unitsPerDp: Double): Response {
        cancel()
        if (readOnly || session.page(pageId) == null) return Response.NONE
        this.unitsPerDp = unitsPerDp
        val p = Vec(s.x, s.y)
        val tool = if (source == PointerSource.STYLUS_ERASER) EditorTool.ERASER else tools.tool
        return when (tool) {
            EditorTool.PEN, EditorTool.MARKER, EditorTool.HIGHLIGHTER -> {
                selection = null
                val stylus = source == PointerSource.STYLUS
                val builder = StrokePoints.Builder(
                    hasPressure = stylus && s.pressure >= 0,
                    hasTilt = stylus && s.tilt >= 0,
                    hasOrientation = stylus && s.orientation >= 0,
                )
                val hold = HoldDetector(tools.holdMillis, HoldDetector.TOLERANCE_DP * unitsPerDp)
                val g = Gesture.Draw(pageId, tools.brushFor(tool)!!, builder, s.timeMillis, hold)
                hold.start(s.x, s.y, s.timeMillis)
                addPoint(g, s)
                gesture = g
                Response(live = LiveStroke.START, redraw = true)
            }
            EditorTool.ERASER -> {
                if (!session.beginGesture(pageId)) return Response.NONE
                selection = null
                gesture = Gesture.Erase(pageId, p)
                eraserAt = p
                eraseAlong(pageId, listOf(p))
                Response.REDRAW
            }
            EditorTool.LASSO -> {
                val sel = selection
                if (sel != null && sel.pageId == pageId && sel.bounds.inflate(8 * unitsPerDp).contains(p)) {
                    gesture = Gesture.MoveSelection(pageId, p)
                } else {
                    selection = null
                    lassoPath = listOf(p)
                    gesture = Gesture.LassoDraw(pageId)
                }
                Response.REDRAW
            }
        }
    }

    fun move(samples: List<Sample>): Response {
        var response = Response.NONE
        for (s in samples) response = merge(response, moveOne(s))
        return response
    }

    private fun moveOne(s: Sample): Response {
        val p = Vec(s.x, s.y)
        return when (val g = gesture ?: return Response.NONE) {
            is Gesture.Draw -> {
                addPoint(g, s)
                val snap = g.shape
                if (snap != null) {
                    adjustShape(g, snap, p)
                    Response.REDRAW
                } else if (g.hold.onPoint(s.x, s.y, s.timeMillis) == HoldDetector.Event.HOLD) {
                    trySnap(g)
                } else {
                    Response.NONE
                }
            }
            is Gesture.Erase -> {
                eraseAlong(g.pageId, listOf(g.last, p))
                g.last = p
                eraserAt = p
                Response.REDRAW
            }
            is Gesture.LassoDraw -> {
                if (dist(lassoPath.last(), p) >= unitsPerDp) lassoPath = lassoPath + p
                Response.REDRAW
            }
            is Gesture.MoveSelection -> {
                selectionOffset = p - g.start
                Response.REDRAW
            }
        }
    }

    /** Timer tick while the pen is down and still. */
    fun tick(now: Long): Response {
        val g = gesture as? Gesture.Draw ?: return Response.NONE
        if (g.shape != null) return Response.NONE
        return if (g.hold.check(now) == HoldDetector.Event.HOLD) trySnap(g) else Response.NONE
    }

    /** Milliseconds until [tick] could fire a hold, or −1 when no timer is needed. */
    fun millisUntilHold(now: Long): Long {
        val g = gesture as? Gesture.Draw ?: return -1
        if (g.shape != null || !tools.shapeCorrection) return -1
        return g.hold.millisUntilHold(now)
    }

    fun up(last: Sample?): Response {
        val g = gesture ?: return Response.NONE
        if (last != null) moveOne(last)
        gesture = null
        return when (g) {
            is Gesture.Draw -> finishDraw(g)
            is Gesture.Erase -> {
                session.endGesture()
                eraserAt = null
                Response.REDRAW
            }
            is Gesture.LassoDraw -> {
                val path = lassoPath
                lassoPath = emptyList()
                val items = session.items(g.pageId)
                selection = if (path.size < 3 || bounds(path).diagonal < 6 * unitsPerDp) {
                    null // a tap: clears the selection
                } else {
                    selectionOf(g.pageId, Lasso.select(items, path))
                }
                Response.REDRAW
            }
            is Gesture.MoveSelection -> {
                val offset = selectionOffset
                selectionOffset = ZERO
                val selected = selectedItems()
                if (offset.length >= 2 * unitsPerDp && selected.isNotEmpty()) {
                    val edit = Lasso.move(selected, offset.x, offset.y)
                    session.apply(g.pageId, InkEdit(edit.removed, edit.added.map { InkCodec.quantize(it) }))
                    selection = selectionOf(g.pageId, selectedItems())
                }
                Response.REDRAW
            }
        }
    }

    /** Abandons the running gesture without changing anything (e.g. a second finger started a pinch). */
    fun cancel(): Response {
        val g = gesture ?: return Response.NONE
        gesture = null
        shapePreview = null
        lassoPath = emptyList()
        selectionOffset = ZERO
        eraserAt = null
        return when (g) {
            is Gesture.Draw -> Response(live = if (g.shape == null) LiveStroke.CANCEL else LiveStroke.NONE, redraw = true)
            is Gesture.Erase -> {
                session.cancelGesture()
                Response.REDRAW
            }
            else -> Response.REDRAW
        }
    }

    // ---------------------------------------------------------------- drawing

    private fun addPoint(g: Gesture.Draw, s: Sample) {
        // Ink needs non-decreasing time; sample clocks are, but guard anyway.
        val t = (s.timeMillis - g.startTime).toInt().coerceAtLeast(g.lastT)
        g.lastT = t
        g.points.add(
            s.x.toFloat(),
            s.y.toFloat(),
            t,
            pressure = s.pressure.coerceIn(0f, 1f),
            tilt = s.tilt.coerceAtLeast(0f),
            orientation = s.orientation.coerceAtLeast(0f),
        )
    }

    private fun trySnap(g: Gesture.Draw): Response {
        if (!tools.shapeCorrection) return Response.NONE
        val points = g.points.build()
        val rec = ShapeRecognizer.recognize(points, unitsPerDp) ?: return Response.NONE
        val last = points.point(points.size - 1)
        g.shape = Snap(rec.geometry, last)
        shapePreview = ShapeItem(PREVIEW_ID, 0, g.brush, rec.kind, rec.geometry, null)
        return Response(live = LiveStroke.CANCEL, redraw = true, haptic = true)
    }

    /** After snapping, dragging the pen reshapes: a line's end follows it; closed shapes scale. */
    private fun adjustShape(g: Gesture.Draw, snap: Snap, pen: Vec) {
        val preview = shapePreview ?: return
        val geometry = when (val base = snap.base) {
            is ShapeGeometry.Segment -> {
                val (s, e) = ShapeRecognizer.snapAngle(base.start.toVec(), pen)
                base.copy(start = s.toPt(), end = e.toPt())
            }
            is ShapeGeometry.Ellipse -> scaled(base, base.center.toVec(), snap.anchor, pen)
            is ShapeGeometry.Polygon -> {
                val c = base.vertices.map { it.toVec() }.let { v -> Vec(v.sumOf { it.x } / v.size, v.sumOf { it.y } / v.size) }
                scaled(base, c, snap.anchor, pen)
            }
        }
        shapePreview = preview.copy(geometry = geometry)
    }

    private fun scaled(base: ShapeGeometry, center: Vec, anchor: Vec, pen: Vec): ShapeGeometry {
        val d0 = dist(center, anchor)
        if (d0 < 1e-6) return base
        val k = (dist(center, pen) / d0).coerceIn(0.2, 5.0)
        return base.transformed(Similarity(scale = k, pivot = center))
    }

    private fun finishDraw(g: Gesture.Draw): Response {
        val page = session.page(g.pageId) ?: return Response(live = LiveStroke.CANCEL, redraw = true)
        val stroke = InkCodec.quantize(StrokeItem(newId(), page.items.nextZ, g.brush, g.points.build())) as StrokeItem
        val preview = shapePreview
        shapePreview = null
        if (g.shape == null || preview == null) {
            session.apply(g.pageId, InkEdit.add(stroke))
            return Response(live = LiveStroke.FINISH, redraw = true)
        }
        // Two undo steps on purpose: the first undo brings back the hand-drawn stroke (SRS 6.2).
        session.apply(g.pageId, InkEdit.add(stroke))
        val shape = preview.copy(id = newId(), z = stroke.z, source = stroke.points)
        session.apply(g.pageId, InkEdit.replace(stroke, shape))
        return Response.REDRAW
    }

    // ---------------------------------------------------------------- erasing

    private fun eraseAlong(pageId: String, path: List<Vec>) {
        val items = session.items(pageId)
        val edit = when (tools.eraserMode) {
            EraserMode.STROKE -> Eraser.touched(items, path, eraserRadius).takeIf { it.isNotEmpty() }?.let { InkEdit.remove(it) }
            EraserMode.AREA -> Eraser.erasePartially(items, path, eraserRadius, newId)?.let { e ->
                InkEdit(e.removed, e.added.map { InkCodec.quantize(it) })
            }
        } ?: return
        session.applyInGesture(edit)
    }

    // ---------------------------------------------------------------- selection

    /** Selected items that still exist (undo may have removed some). */
    fun selectedItems(): List<InkItem> {
        val sel = selection ?: return emptyList()
        return session.items(sel.pageId).filter { it.id in sel.ids }
    }

    /** Call after undo/redo or other outside changes so the selection drops vanished items. */
    fun refreshSelection() {
        val sel = selection ?: return
        selection = selectionOf(sel.pageId, selectedItems())
    }

    fun clearSelection() {
        selection = null
    }

    fun deleteSelection(): Boolean {
        val sel = selection ?: return false
        val items = selectedItems()
        selection = null
        return items.isNotEmpty() && session.apply(sel.pageId, Lasso.delete(items))
    }

    fun recolorSelection(colorArgb: Int): Boolean {
        val sel = selection ?: return false
        val items = selectedItems()
        if (items.isEmpty()) return false
        // Highlighter ink keeps its translucency.
        val edit = InkEdit(
            items,
            items.map { item ->
                val alpha = if (item.brush.tool == InkTool.HIGHLIGHTER) item.brush.colorArgb ushr 24 else 0xFF
                val brush = item.brush.copy(colorArgb = (alpha shl 24) or (colorArgb and 0xFFFFFF))
                when (item) {
                    is StrokeItem -> item.copy(brush = brush)
                    is ShapeItem -> item.copy(brush = brush)
                }
            },
        )
        return session.apply(sel.pageId, edit)
    }

    /** Copies the selection a little down-right and selects the copies. */
    fun duplicateSelection(offsetDp: Double = 16.0): Boolean {
        val sel = selection ?: return false
        val items = selectedItems()
        val page = session.page(sel.pageId) ?: return false
        if (items.isEmpty()) return false
        val off = offsetDp * unitsPerDp
        val edit = Lasso.duplicate(items, off, off, page.items.nextZ, newId).let { e ->
            InkEdit(e.removed, e.added.map { InkCodec.quantize(it) })
        }
        if (!session.apply(sel.pageId, edit)) return false
        selection = selectionOf(sel.pageId, edit.added)
        return true
    }

    private fun selectionOf(pageId: String, items: List<InkItem>): Selection? {
        if (items.isEmpty()) return null
        val box = items.map { it.bounds() }.reduce { a, b -> a.union(b) }
        return Selection(pageId, items.map { it.id }.toSet(), box)
    }

    private fun merge(a: Response, b: Response) = Response(
        live = if (b.live != LiveStroke.NONE) b.live else a.live,
        redraw = a.redraw || b.redraw,
        haptic = a.haptic || b.haptic,
    )

    companion object {
        private val ZERO = Vec(0.0, 0.0)
        const val PREVIEW_ID = "shape-preview"
    }
}
