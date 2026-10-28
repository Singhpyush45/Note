package com.appname.notes.feature.ink

import com.appname.notes.core.settings.LaserSettings
import com.appname.notes.core.ink.input.LaserTrail
import com.appname.notes.core.ink.input.Ruler
import com.appname.notes.core.ink.input.EditorTool
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.strokes.Stroke
import com.appname.notes.core.ink.edit.InkSession
import com.appname.notes.core.ink.edit.Similarity
import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.input.InkInputController
import com.appname.notes.core.ink.input.LiveStroke
import com.appname.notes.core.ink.input.PointerSource
import com.appname.notes.core.ink.input.Response
import com.appname.notes.core.ink.input.Sample
import com.appname.notes.core.ink.layout.PageArrangement
import com.appname.notes.core.ink.layout.PageMode
import com.appname.notes.core.ink.layout.PageStack
import com.appname.notes.core.ink.layout.Viewport
import com.appname.notes.core.ink.layout.ViewportRules
import com.appname.notes.feature.pdf.PdfPageRenderer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Where the view was looking, to restore it after rotation: the world point at the centre and the zoom. */
data class InkViewState(val centerX: Double, val centerY: Double, val zoom: Double)

/**
 * The handwriting surface: pages stacked vertically, drawn with [InkRenderer], with Jetpack Ink's
 * low-latency [InProgressStrokesView] on top for the stroke being drawn (ADR-0009).
 *
 * Input routing (SRS 6.2, 8.1):
 * - stylus → current tool; the stylus eraser end → eraser;
 * - one finger → draws or scrolls (setting [fingerDraws]); two fingers → zoom and scroll with inertia;
 * - mouse → current tool (right/middle button scrolls), wheel scrolls, Ctrl + wheel zooms;
 * - palm rejection: while a stylus draws, other touches are ignored; touches the system marks as
 *   accidental (FLAG_CANCELED) undo their stroke.
 *
 * Changes go synchronously into the [InkSession] (via [InkInputController]) on the UI thread, so a
 * finished stroke is handed from Ink's live layer to the page in the same frame — no flicker.
 */
@SuppressLint("ViewConstructor")
class InkCanvasView(context: Context) : FrameLayout(context) {

    interface Listener {
        /** Gesture state changed (selection, tool feedback): refresh toolbars. */
        fun onInteraction()

        fun onStylusDetected()

        fun onPageChanged(index: Int)

        fun onUndoRequested()

        fun onRedoRequested()

        /** A tap with the text tool at [x], [y] on [pageId] (page units), on [existing] text if any. */
        fun onTextTap(pageId: String, x: Double, y: Double, existing: com.appname.notes.core.ink.model.TextItem?) {}

        /** Ctrl+V on a keyboard. */
        fun onPasteRequested() {}

        /** A tap on a recording's chip on the page. */
        fun onAudioTap(item: com.appname.notes.core.ink.model.AudioItem) {}

        /** Press and hold (a finger that scrolls, or the text tool) at page point [p]; [x], [y] on screen. */
        fun onLongPress(pageId: String, p: Vec, x: Float, y: Float, unitsPerDp: Double) {}

        /** A stroke (or the laser) started: anything typed on the page is finished first. */
        fun onDrawStart() {}
    }

    /** An item not drawn because it is being edited on top of the page (text typed in place). */
    var hiddenItemId: String? = null
        set(value) {
            if (field == value) return
            field = value
            surface.invalidate()
        }

    /** Called after a frame where the zoom or scroll changed (things placed over the page follow it). */
    var onTransform: (() -> Unit)? = null

    /** Called whenever continuous scroll progress (0..1) along the document changes. */
    var onScrollChanged: ((Float) -> Unit)? = null
    private var lastDrawn: Viewport? = null
    private var lastReportedFraction: Float = -1f

    /** Screen pixels per page unit at the current zoom. */
    val pxPerUnit: Float get() = viewport?.scale?.toFloat() ?: 1f

    /** Current continuous scroll progress (0..1) along the document. */
    val scrollFraction: Float
        get() {
            val v = viewport ?: return 0f
            if (width == 0 || height == 0 || stack.count == 0) return 0f
            return rules.scrollFraction(v, width.toDouble(), height.toDouble(), stack)
        }

    /** Where page point [x], [y] of [pageId] is on screen (view pixels), or null. */
    fun screenPoint(pageId: String, x: Float, y: Float): android.graphics.PointF? {
        val s = session ?: return null
        val v = viewport ?: return null
        val i = s.pages.indexOfFirst { it.id == pageId }
        if (i < 0) return null
        val q = toScreen(i, v, Vec(x.toDouble(), y.toDouble()))
        return android.graphics.PointF(q.x.toFloat(), q.y.toFloat())
    }

    /** Scrolls the pages by [dx], [dy] screen pixels (e.g. to keep a text box above the keyboard). */
    fun panBy(dx: Float, dy: Float) {
        val v = viewport ?: return
        scroller.forceFinished(true)
        viewport = rules.pan(v, dx.toDouble(), dy.toDouble(), width.toDouble(), height.toDouble(), stack)
        surface.invalidate()
        reportPage()
    }

    /** Smoothly animates a vertical/horizontal pan by [dx], [dy] screen pixels. */
    fun smoothPanBy(dx: Float, dy: Float, durationMs: Int = 280) {
        if (viewport == null) return
        val ix = dx.roundToInt()
        val iy = dy.roundToInt()
        if (ix == 0 && iy == 0) return
        scroller.forceFinished(true)
        flingX = 0
        flingY = 0
        scroller.startScroll(0, 0, ix, iy, durationMs.coerceIn(120, 500))
        postInvalidateOnAnimation()
    }

    /** Continuously scrolls the viewport to [fraction] (0..1) along the document without discrete jumps. */
    fun scrollToFraction(fraction: Float) {
        val v = viewport ?: return
        if (width == 0 || height == 0 || stack.count == 0) return
        scroller.forceFinished(true)
        viewport = rules.scrollToFraction(v, fraction, width.toDouble(), height.toDouble(), stack)
        surface.invalidate()
        reportPage()
    }

    var listener: Listener? = null

    /** Whether one finger draws (true) or scrolls (false). */
    var fingerDraws: Boolean = true

    var backgroundArgb: Int = 0xFFEEF0F2.toInt()
        set(value) {
            field = value
            surface.invalidate()
        }

    /** Renders the PDF pages under the ink (PDF notes only). */
    var pdf: PdfPageRenderer? = null
        set(value) {
            if (field === value) return
            field?.onUpdate = null
            field = value
            value?.onUpdate = { surface.invalidate() }
            surface.invalidate()
        }

    private var session: InkSession? = null
    private var controller: InkInputController? = null

    private val density = resources.displayMetrics.density
    private val rules = ViewportRules(actualSize = 160.0 / 96 * density, margin = (12 * density).toDouble())
    private var stack = PageStack(emptyList())
    private var pageSizesKey: List<Pair<Float, Float>> = emptyList()

    /** Split or infinite, vertical or horizontal (owner brief §5). Changing it re-lays the pages out. */
    var arrangement: PageArrangement = PageArrangement.DEFAULT
        set(value) {
            if (field == value) return
            field = value
            pageSizesKey = emptyList() // forces a new stack
            viewport = null
            refresh()
        }

    /** How the laser pointer looks (owner brief §17). The trail is screen-only and never saved. */
    var laser: LaserSettings = LaserSettings()

    private val laserTrail = LaserTrail()
    private val laserPainter = LaserPainter()

    var darkPaper: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            surface.invalidate()
        }
    private var viewport: Viewport? = null
    private var pendingState: InkViewState? = null

    /** A page to show once the view has a size (requests can arrive before the first layout). */
    private var pendingPage: Int? = null
    private var reportedPage = -1

    private val renderer = InkRenderer()
    private val surface = Surface(context)
    private val scroller = OverScroller(context)
    /**
     * Jetpack Ink's live-stroke layer. Replaced with a fresh one each time the window becomes visible
     * again: Ink 1.0's render thread can die when the app is backgrounded right after a stroke (see
     * [InkCrashGuard]), and a new view brings a new render thread. Saved ink is drawn by [surface] and
     * is unaffected.
     */
    private var inProgress: InProgressStrokesView? = null

    init {
        scroller.setFriction(ViewConfiguration.getScrollFriction() * 0.65f)
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (InkEngine.available) attachInkLayer()
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun attachInkLayer() {
        val ink = InProgressStrokesView(context)
        ink.addFinishedStrokesListener(
            object : InProgressStrokesFinishedListener {
                override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                    // Our page already has the stroke (applied on pen-up); stop Ink drawing its copy.
                    surface.invalidate()
                    ink.removeFinishedStrokes(strokes.keys)
                }
            },
        )
        addView(ink, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        inProgress = ink
    }

    private var hiddenSinceAttach = false

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) {
            hiddenSinceAttach = true
        } else if (hiddenSinceAttach && InkEngine.available) {
            hiddenSinceAttach = false
            cancelGesture(null)
            inProgress?.let { removeView(it) }
            attachInkLayer()
        }
    }

    /** The canvas restores itself from the ViewModel; children (incl. Ink's internal views) keep no saved state. */
    override fun dispatchSaveInstanceState(container: android.util.SparseArray<android.os.Parcelable>) = dispatchFreezeSelfOnly(container)

    override fun dispatchRestoreInstanceState(container: android.util.SparseArray<android.os.Parcelable>) = dispatchThawSelfOnly(container)

    /** Connects the view to the open note. Safe to call again with the same objects. */
    fun bind(session: InkSession, controller: InkInputController) {
        if (this.session !== session || this.controller !== controller) {
            cancelGesture(null)
            this.session = session
            this.controller = controller
        }
        refresh()
    }

    /** Call after the session changed from outside the view (undo button, page added …). */
    fun refresh() {
        val s = session ?: return
        val sizes = s.pages.map { it.width to it.height }
        if (sizes != pageSizesKey) {
            pageSizesKey = sizes
            stack = PageStack(sizes, arrangement.axis, arrangement.gap)
            viewport?.let { viewport = rules.clamp(it, width.toDouble(), height.toDouble(), stack) }
        }
        renderer.retain(s.pages.flatMap { p -> p.items.items.map { it.id } }.toSet())
        ensureViewport()
        surface.invalidate()
        reportPage()
    }

    var viewState: InkViewState?
        get() {
            val v = viewport ?: return pendingState
            val c = v.toWorld(Vec(width / 2.0, height / 2.0))
            return InkViewState(c.x, c.y, v.scale / rules.fitScale(width.toDouble(), stack, height.toDouble()))
        }
        set(value) {
            pendingState = value
            if (width > 0) {
                viewport = null
                ensureViewport()
                surface.invalidate()
            }
        }

    /** "Fit width" / "Fit page" for the page currently shown (SRS 7.1). */
    fun fit(mode: FitMode) {
        val v = viewport ?: return
        if (stack.count == 0) return
        scroller.forceFinished(true)
        val w = width.toDouble()
        val h = height.toDouble()
        val page = rules.currentPage(v, h, stack, w)
        viewport = when (mode) {
            FitMode.WIDTH -> rules.fitWidth(page, w, h, stack)
            FitMode.PAGE -> rules.fitPage(page, w, h, stack)
        }
        surface.invalidate()
        reportPage()
    }

    /**
     * Scrolls to page [index]. When [animate] is true and the view is laid out, glides smoothly using
     * [OverScroller] instead of jumping in a single discrete step.
     */
    fun scrollToPage(index: Int, animate: Boolean = true) {
        // Not laid out yet (e.g. opened from a search hit): apply once the size is known.
        val v = viewport ?: run { pendingPage = index; return }
        if (index !in 0 until stack.count) return
        val target = rules.scrollTo(v, index, width.toDouble(), height.toDouble(), stack)
        val ddx = (target.dx - v.dx).roundToInt()
        val ddy = (target.dy - v.dy).roundToInt()
        if (!animate || (abs(ddx) <= 2 && abs(ddy) <= 2)) {
            scroller.forceFinished(true)
            viewport = target
            surface.invalidate()
            reportPage()
            return
        }
        scroller.forceFinished(true)
        flingX = 0
        flingY = 0
        val dist = hypot(ddx.toDouble(), ddy.toDouble())
        val duration = (220 + (dist / (height.coerceAtLeast(1)) * 110).toInt()).coerceIn(220, 440)
        scroller.startScroll(0, 0, ddx, ddy, duration)
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw > 0 && viewport != null && pendingState == null) pendingState = viewState
        viewport = null
        ensureViewport()
    }

    private fun ensureViewport() {
        if (viewport != null || width == 0 || stack.count == 0) return
        val w = width.toDouble()
        val h = height.toDouble()
        val state = pendingState
        pendingState = null
        viewport = if (state == null) {
            rules.initial(w, h, stack)
        } else {
            val s = rules.fitScale(w, stack, h) * state.zoom
            rules.clamp(Viewport(s, w / 2 - state.centerX * s, h / 2 - state.centerY * s), w, h, stack)
        }
        pendingPage?.let { page ->
            pendingPage = null
            if (page in 0 until stack.count) viewport = rules.scrollTo(viewport!!, page, w, h, stack)
        }
        reportPage()
    }

    private fun reportPage() {
        val v = viewport ?: return
        val page = rules.currentPage(v, height.toDouble(), stack, width.toDouble())
        if (page != reportedPage) {
            reportedPage = page
            listener?.onPageChanged(page)
        }
        val frac = scrollFraction
        if (abs(frac - lastReportedFraction) >= 0.001f) {
            lastReportedFraction = frac
            onScrollChanged?.invoke(frac)
        }
    }

    // ---------------------------------------------------------------- coordinates

    private fun pageToScreen(i: Int, v: Viewport): Matrix = Matrix().apply {
        val s = v.scale.toFloat()
        setScale(s, s)
        postTranslate((v.dx + stack.left(i) * v.scale).toFloat(), (v.dy + stack.top(i) * v.scale).toFloat())
    }

    private fun screenToPage(i: Int, v: Viewport, x: Float, y: Float): Vec =
        stack.toPage(i, v.toWorld(Vec(x.toDouble(), y.toDouble())))

    /** Page units per dp at the current zoom: sizes of tolerances and the eraser follow the screen. */
    private fun unitsPerDp(v: Viewport) = density / v.scale

    // ---------------------------------------------------------------- touch

    private enum class Mode { IDLE, INK, LASER, PAN, PINCH, IGNORE, RULER_MOVE, RULER_TURN }

    // ---------------------------------------------------------------- ruler (owner brief §16)

    /** Shows the ruler; strokes drawn along its edges come out straight. */
    var rulerVisible: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value && ruler == null && width > 0) ruler = defaultRuler()
            surface.invalidate()
        }

    private var ruler: Ruler? = null
    private var rulerEdge: Int? = null
    private val rulerPainter by lazy { RulerPainter(density) }

    private fun defaultRuler() = Ruler(width / 2.0, height / 2.0, 0.0, minOf(width, height) * 0.9 + 0.0, 64.0 * density)

    private fun activeRuler(): Ruler? = if (rulerVisible) (ruler ?: defaultRuler().also { ruler = it }) else null

    private var mode = Mode.IDLE
    private var inkPointer = -1
    private var inkSource = PointerSource.FINGER
    private var inkPage = -1
    private var liveStroke: InProgressStrokeId? = null

    private var panPointer = -1
    private var lastX = 0f
    private var lastY = 0f
    private var velocity: VelocityTracker? = null
    private var flingX = 0
    private var flingY = 0

    private var pinchA = -1
    private var pinchB = -1
    private var lastSpan = 0.0
    private var initialPinchSpan = 0.0
    private var lastFocusX = 0.0
    private var lastFocusY = 0.0
    private var lastPinchTime = 0L
    private var pinchVelX = 0.0
    private var pinchVelY = 0.0

    /** All touches come here: the children only draw. */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    @SuppressLint("ClickableViewAccessibility") // Drawing has no click equivalent; toolbar buttons cover actions.
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (session == null || controller == null || viewport == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                requestFocus()
                startPointer(e, 0)
            }
            MotionEvent.ACTION_POINTER_DOWN -> onExtraPointer(e, e.actionIndex)
            MotionEvent.ACTION_MOVE -> when (mode) {
                Mode.INK -> feedInk(e)
                Mode.LASER -> feedLaser(e)
                Mode.RULER_MOVE, Mode.RULER_TURN -> moveRuler(e)
                Mode.PAN -> pan(e)
                Mode.PINCH -> pinch(e)
                else -> Unit
            }
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(e, e.actionIndex)
            MotionEvent.ACTION_UP -> {
                when (mode) {
                    Mode.INK -> if (e.isCanceledTouch()) cancelGesture(e) else finishInk(e)
                    Mode.LASER -> laserTrail.end()
                    Mode.PAN -> if (isTextTap(e)) textTap(e, 0) else fling(e)
                    else -> Unit
                }
                endTouch()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelGesture(e)
                endTouch()
            }
        }
        velocity?.addMovement(e)
        return true
    }

    /** Android 13+ marks touches it decided were accidental (palm). Older versions never set the flag. */
    private fun MotionEvent.isCanceledTouch() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && flags and MotionEvent.FLAG_CANCELED != 0

    private fun sourceOf(e: MotionEvent, index: Int): PointerSource = when (e.getToolType(index)) {
        MotionEvent.TOOL_TYPE_STYLUS -> PointerSource.STYLUS
        MotionEvent.TOOL_TYPE_ERASER -> PointerSource.STYLUS_ERASER
        MotionEvent.TOOL_TYPE_MOUSE -> PointerSource.MOUSE
        else -> PointerSource.FINGER
    }

    private fun isStylus(source: PointerSource) = source == PointerSource.STYLUS || source == PointerSource.STYLUS_ERASER

    private fun startPointer(e: MotionEvent, index: Int) {
        val source = sourceOf(e, index)
        if (isStylus(source)) listener?.onStylusDetected()
        if (controller?.readOnly == true) {
            startPan(e, index) // nothing to draw: every pointer scrolls
            return
        }
        val mouseScroll = source == PointerSource.MOUSE &&
            e.buttonState and (MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY) != 0
        // A recording's chip is a button with every tool but the lasso (which moves it).
        if (controller?.tools?.tool != EditorTool.LASSO && source != PointerSource.STYLUS_ERASER) {
            pagePointAt(e.getX(index), e.getY(index))?.let { (pageId, p) ->
                val chip = session?.page(pageId)?.items?.items
                    ?.filterIsInstance<com.appname.notes.core.ink.model.AudioItem>()
                    ?.lastOrNull { it.place.contains(p) }
                if (chip != null) {
                    mode = Mode.IGNORE
                    listener?.onAudioTap(chip)
                    return
                }
            }
        }
        // Text tool: any pointer scrolls as usual; a tap (no movement) places or edits a text box on lift.
        if (controller?.tools?.tool == EditorTool.TEXT && source != PointerSource.STYLUS_ERASER) {
            textTapX = e.getX(index)
            textTapY = e.getY(index)
            startPan(e, index)
            armLongPress(e, index)
            return
        }
        textTapX = Float.NaN
        // The ruler takes touches on itself: its knob turns it, its body moves it.
        activeRuler()?.let { r ->
            val p = Vec(e.getX(index).toDouble(), e.getY(index).toDouble())
            val grab = when {
                r.onKnob(p, KNOB_DP * density) -> Mode.RULER_TURN
                r.onBody(p) -> Mode.RULER_MOVE
                else -> null
            }
            if (grab != null) {
                mode = grab
                inkPointer = e.getPointerId(index)
                lastX = e.getX(index)
                lastY = e.getY(index)
                return
            }
        }
        if ((source == PointerSource.FINGER && !fingerDraws) || mouseScroll) {
            startPan(e, index)
            if (!mouseScroll) armLongPress(e, index)
        } else {
            startDrawing(e, index, source)
        }
    }

    /** Ink, or the laser pointer / text tool when chosen (the pen's eraser end still erases). */
    private fun startDrawing(e: MotionEvent, index: Int, source: PointerSource) {
        listener?.onDrawStart()
        val tool = controller?.tools?.tool
        when {
            source == PointerSource.STYLUS_ERASER -> startInk(e, index, source)
            tool == EditorTool.LASER -> startLaser(e, index, source)
            else -> startInk(e, index, source)
        }
    }

    private var textTapX = Float.NaN
    private var textTapY = Float.NaN

    private fun isTextTap(e: MotionEvent): Boolean {
        if (textTapX.isNaN() || controller?.tools?.tool != EditorTool.TEXT) return false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        val moved = hypot(e.getX(0) - textTapX, e.getY(0) - textTapY)
        textTapX = Float.NaN
        return moved <= slop && e.eventTime - e.downTime < ViewConfiguration.getLongPressTimeout() * 2
    }

    /** Text tool: the tap goes to the screen (edit the text box there, or start one). */
    private fun textTap(e: MotionEvent, index: Int) {
        val s = session ?: return
        val v = viewport ?: return
        val page = stack.pageAt(v.toWorld(Vec(e.getX(index).toDouble(), e.getY(index).toDouble())))
        if (page < 0) return
        mode = Mode.IGNORE
        val p = screenToPage(page, v, e.getX(index), e.getY(index))
        val hit = s.pages[page].items.items
            .filterIsInstance<com.appname.notes.core.ink.model.TextItem>()
            .lastOrNull { it.place.contains(p) }
        listener?.onTextTap(s.pages[page].id, p.x, p.y, hit)
    }

    /** Where a drop or paste at screen [x], [y] lands: page id and page point, or null off the pages. */
    fun pagePointAt(x: Float, y: Float): Pair<String, Vec>? {
        val s = session ?: return null
        val v = viewport ?: return null
        val page = stack.pageAt(v.toWorld(Vec(x.toDouble(), y.toDouble())))
        if (page < 0) return null
        return s.pages[page].id to screenToPage(page, v, x, y)
    }

    private val imagesReady: () -> Unit = { surface.invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        InkImages.init(context)
        InkImages.addListener(imagesReady)
    }

    // ---------------------------------------------------------------- press and hold

    private var longPressX = 0f
    private var longPressY = 0f
    private var longPressArmed = false
    private val longPressRunnable = Runnable {
        if (!longPressArmed || mode != Mode.PAN) return@Runnable
        longPressArmed = false
        val at = pagePointAt(longPressX, longPressY) ?: return@Runnable
        mode = Mode.IGNORE
        textTapX = Float.NaN
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        listener?.onLongPress(at.first, at.second, longPressX, longPressY, viewport?.let { unitsPerDp(it) } ?: 1.0)
    }

    private fun armLongPress(e: MotionEvent, index: Int) {
        if (e.pointerCount > 1) return
        longPressX = e.getX(index)
        longPressY = e.getY(index)
        longPressArmed = true
        removeCallbacks(longPressRunnable)
        postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun disarmLongPress() {
        longPressArmed = false
        removeCallbacks(longPressRunnable)
    }

    private fun onExtraPointer(e: MotionEvent, index: Int) {
        disarmLongPress()
        val source = sourceOf(e, index)
        when (mode) {
            Mode.INK -> when {
                isStylus(inkSource) -> Unit // palm or finger while the pen writes: ignore it
                isStylus(source) -> { // the finger stroke was really the palm: the pen wins
                    cancelGesture(e)
                    startDrawing(e, index, source)
                }
                else -> { // second finger: it's a pinch/two-finger pan, not a stroke
                    cancelGesture(e)
                    startPinch(e, index)
                }
            }
            Mode.LASER -> when {
                isStylus(inkSource) -> Unit
                isStylus(source) -> {
                    laserTrail.end()
                    startDrawing(e, index, source)
                }
                else -> { // second finger: pinch
                    laserTrail.end()
                    startPinch(e, index)
                }
            }
            Mode.PAN -> if (isStylus(source)) {
                startDrawing(e, index, source)
            } else {
                startPinch(e, index)
            }
            Mode.IDLE, Mode.IGNORE -> if (isStylus(source)) startDrawing(e, index, source)
            Mode.PINCH, Mode.RULER_MOVE, Mode.RULER_TURN -> Unit
        }
    }

    private fun moveRuler(e: MotionEvent) {
        val r = ruler ?: return
        val index = e.findPointerIndex(inkPointer)
        if (index < 0) return
        val x = e.getX(index)
        val y = e.getY(index)
        ruler = if (mode == Mode.RULER_TURN) r.turnedTowards(Vec(x.toDouble(), y.toDouble())) else r.moved((x - lastX).toDouble(), (y - lastY).toDouble())
        lastX = x
        lastY = y
        surface.invalidate()
    }

    private fun onPointerUp(e: MotionEvent, index: Int) {
        val id = e.getPointerId(index)
        when (mode) {
            Mode.INK -> if (id == inkPointer) {
                if (e.isCanceledTouch()) cancelGesture(e) else finishInk(e)
                mode = Mode.IGNORE
            }
            Mode.LASER -> if (id == inkPointer) {
                laserTrail.end()
                mode = Mode.IGNORE
            }
            Mode.PINCH -> if (id == pinchA || id == pinchB) {
                val other = if (id == pinchA) pinchB else pinchA
                val otherIndex = e.findPointerIndex(other)
                if (!fingerDraws && otherIndex >= 0) {
                    panPointer = other
                    lastX = e.getX(otherIndex)
                    lastY = e.getY(otherIndex)
                    mode = Mode.PAN
                } else {
                    // Two-finger scroll when fingerDraws is true: carry momentum into a smooth inertia fling!
                    val minVel = ViewConfiguration.get(context).scaledMinimumFlingVelocity
                    if (hypot(pinchVelX, pinchVelY) >= minVel) {
                        flingX = 0
                        flingY = 0
                        scroller.fling(0, 0, pinchVelX.toInt(), pinchVelY.toInt(), Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE)
                        postInvalidateOnAnimation()
                    }
                    mode = Mode.IGNORE
                }
                reportPage()
            }
            Mode.PAN -> if (id == panPointer) {
                val next = (0 until e.pointerCount).firstOrNull { it != index } ?: return
                panPointer = e.getPointerId(next)
                lastX = e.getX(next)
                lastY = e.getY(next)
            }
            else -> Unit
        }
    }

    private fun endTouch() {
        disarmLongPress()
        rulerEdge = null
        mode = Mode.IDLE
        inkPointer = -1
        removeCallbacks(holdRunnable)
        velocity?.recycle()
        velocity = null
        listener?.onInteraction()
    }

    // ---------------------------------------------------------------- ink gestures

    private fun sampleAt(e: MotionEvent, index: Int, page: Int, v: Viewport, historical: Int = -1): Sample {
        var x = if (historical < 0) e.getX(index) else e.getHistoricalX(index, historical)
        var y = if (historical < 0) e.getY(index) else e.getHistoricalY(index, historical)
        // Along the ruler: every point goes onto its edge, so the line is straight.
        val edge = rulerEdge
        val r = ruler
        if (edge != null && r != null) {
            val q = r.project(Vec(x.toDouble(), y.toDouble()), edge)
            x = q.x.toFloat()
            y = q.y.toFloat()
        }
        val t = if (historical < 0) e.eventTime else e.getHistoricalEventTime(historical)
        val p = screenToPage(page, v, x, y)
        val stylus = isStylus(inkSource)
        val pressure = if (historical < 0) e.getPressure(index) else e.getHistoricalPressure(index, historical)
        val tilt = if (historical < 0) e.getAxisValue(MotionEvent.AXIS_TILT, index) else e.getHistoricalAxisValue(MotionEvent.AXIS_TILT, index, historical)
        val orientation = if (historical < 0) e.getOrientation(index) else e.getHistoricalOrientation(index, historical)
        return Sample(
            p.x,
            p.y,
            t,
            pressure = if (stylus) pressure.coerceIn(0f, 1f) else -1f,
            tilt = if (stylus) tilt.coerceIn(0f, (PI / 2).toFloat()) else -1f,
            // Android: −π..π with 0 = pointing up; stored as 0..2π.
            orientation = if (stylus) (if (orientation < 0) orientation + (2 * PI).toFloat() else orientation) else -1f,
        )
    }

    private fun startInk(e: MotionEvent, index: Int, source: PointerSource) {
        val c = controller ?: return
        val s = session ?: return
        val v = viewport ?: return
        val page = stack.pageAt(v.toWorld(Vec(e.getX(index).toDouble(), e.getY(index).toDouble())))
        if (page < 0) return
        mode = Mode.INK
        inkPointer = e.getPointerId(index)
        inkSource = source
        inkPage = page
        // Starting next to the ruler's edge (with a drawing tool): the stroke follows the edge.
        rulerEdge = activeRuler()?.takeIf { c.tools.tool.inkTool != null && source != PointerSource.STYLUS_ERASER }
            ?.edgeNear(Vec(e.getX(index).toDouble(), e.getY(index).toDouble()), RULER_SNAP_DP * density)
        val response = c.down(s.pages[page].id, sampleAt(e, index, page, v), source, unitsPerDp(v))
        handle(response, e)
        scheduleHold()
        listener?.onInteraction()
    }

    // ---------------------------------------------------------------- laser pointer

    private fun startLaser(e: MotionEvent, index: Int, source: PointerSource) {
        mode = Mode.LASER
        inkPointer = e.getPointerId(index)
        inkSource = source
        laserTrail.start(e.getX(index), e.getY(index), e.eventTime, density)
        surface.postInvalidateOnAnimation()
        listener?.onInteraction()
    }

    private fun feedLaser(e: MotionEvent) {
        val index = e.findPointerIndex(inkPointer)
        if (index < 0) return
        for (h in 0 until e.historySize) laserTrail.move(e.getHistoricalX(index, h), e.getHistoricalY(index, h), e.getHistoricalEventTime(h), density)
        laserTrail.move(e.getX(index), e.getY(index), e.eventTime, density)
        surface.postInvalidateOnAnimation()
    }

    private fun feedInk(e: MotionEvent) {
        val c = controller ?: return
        val v = viewport ?: return
        val index = e.findPointerIndex(inkPointer)
        if (index < 0 || inkPage < 0) return
        val samples = ArrayList<Sample>(e.historySize + 1)
        for (h in 0 until e.historySize) samples.add(sampleAt(e, index, inkPage, v, h))
        samples.add(sampleAt(e, index, inkPage, v))
        liveStroke?.let { inProgress?.addToStroke(e, inkPointer, it) }
        handle(c.move(samples), e)
        // Drawn by us (a variant, or no Jetpack Ink): show the new points now.
        if (liveStroke == null) surface.invalidate()
        scheduleHold()
    }

    private fun finishInk(e: MotionEvent) {
        val c = controller ?: return
        val v = viewport ?: return
        val index = e.findPointerIndex(inkPointer)
        val last = if (index >= 0 && inkPage >= 0) sampleAt(e, index, inkPage, v) else null
        removeCallbacks(holdRunnable)
        val response = c.up(last)
        val id = liveStroke
        liveStroke = null
        if (id != null) {
            if (response.live == LiveStroke.FINISH && index >= 0) inProgress?.finishStroke(e, inkPointer, id) else inProgress?.cancelStroke(id, e)
        }
        handle(response.copy(live = LiveStroke.NONE), e)
        inkPage = -1
        if (c.selectionMoved) carrySelectionToPageUnder()
    }

    /**
     * Ink dragged past its page's edge goes onto the page it was dropped on (owner report: it used to stay cut off
     * at the bottom of its own page). Decided by where the middle of the selection ended up.
     */
    private fun carrySelectionToPageUnder() {
        val c = controller ?: return
        val s = session ?: return
        val sel = c.selection ?: return
        val i = s.pageIndex(sel.pageId)
        if (i < 0) return
        val center = sel.bounds.center
        val world = stack.toWorld(i, center)
        val j = stack.pageAt(world)
        if (j < 0 || j == i) return
        val q = stack.toPage(j, world)
        if (c.moveSelectionToPage(s.pages[j].id, q.x - center.x, q.y - center.y)) {
            surface.invalidate()
            listener?.onInteraction()
        }
    }

    private fun cancelGesture(e: MotionEvent?) {
        disarmLongPress()
        removeCallbacks(holdRunnable)
        laserTrail.end()
        val response = controller?.cancel() ?: Response.NONE
        liveStroke?.let { inProgress?.cancelStroke(it, e) }
        liveStroke = null
        inkPage = -1
        if (response.redraw) surface.invalidate()
    }

    private fun handle(r: Response, e: MotionEvent?) {
        when (r.live) {
            LiveStroke.START -> {
                val ink = inProgress
                val brush = controller?.liveStroke()?.first
                val v = viewport
                // Variants (pencils, brushes …) are drawn live by us, so the wet stroke matches the dry one.
                if (ink != null && brush != null && brush.usesJetpack && rulerEdge == null && e != null && v != null && inkPage >= 0) {
                    val toPage = Matrix().also { pageToScreen(inkPage, v).invert(it) }
                    ink.maskPath = outsidePage(inkPage, v)
                    // Dark paper: the live layer can't be filtered, so its colour is mapped the same way here.
                    val shown = if (darkPaper) brush.copy(colorArgb = DarkPaper.map(brush.colorArgb)) else brush
                    liveStroke = ink.startStroke(e, inkPointer, InkEngine.brush(shown), toPage)
                }
            }
            LiveStroke.CANCEL -> {
                liveStroke?.let { inProgress?.cancelStroke(it, e) }
                liveStroke = null
            }
            LiveStroke.FINISH, LiveStroke.NONE -> Unit
        }
        if (r.haptic) {
            performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
            )
            listener?.onInteraction()
        }
        if (r.redraw) surface.invalidate()
    }

    /** Live ink outside the page is hidden, matching the clipped finished ink. */
    private fun outsidePage(i: Int, v: Viewport): Path {
        val r = RectF(0f, 0f, session!!.pages[i].width, session!!.pages[i].height)
        pageToScreen(i, v).mapRect(r)
        return Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRect(r, Path.Direction.CW)
        }
    }

    private val holdRunnable = Runnable {
        val c = controller ?: return@Runnable
        handle(c.tick(SystemClock.uptimeMillis()), null)
        scheduleHold()
    }

    private fun scheduleHold() {
        removeCallbacks(holdRunnable)
        val wait = controller?.millisUntilHold(SystemClock.uptimeMillis()) ?: -1
        if (wait >= 0) postDelayed(holdRunnable, wait + 1)
    }

    // ---------------------------------------------------------------- navigation

    private fun startPan(e: MotionEvent, index: Int) {
        mode = Mode.PAN
        panPointer = e.getPointerId(index)
        lastX = e.getX(index)
        lastY = e.getY(index)
        velocity?.recycle()
        velocity = VelocityTracker.obtain()
    }

    private fun pan(e: MotionEvent) {
        val index = e.findPointerIndex(panPointer)
        val v = viewport ?: return
        if (index < 0) return
        val x = e.getX(index)
        val y = e.getY(index)
        if (longPressArmed && hypot((x - longPressX).toDouble(), (y - longPressY).toDouble()) > ViewConfiguration.get(context).scaledTouchSlop) {
            disarmLongPress()
        }
        viewport = rules.pan(v, (x - lastX).toDouble(), (y - lastY).toDouble(), width.toDouble(), height.toDouble(), stack)
        lastX = x
        lastY = y
        surface.invalidate()
        reportPage()
    }

    private fun fling(e: MotionEvent) {
        val vt = velocity ?: return
        vt.addMovement(e)
        vt.computeCurrentVelocity(1000)
        val vx = vt.getXVelocity(panPointer).toInt()
        val vy = vt.getYVelocity(panPointer).toInt()
        flingX = 0
        flingY = 0
        scroller.fling(0, 0, vx, vy, Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE)
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (!scroller.computeScrollOffset()) return
        val v = viewport ?: return
        val x = scroller.currX
        val y = scroller.currY
        val next = rules.pan(v, (x - flingX).toDouble(), (y - flingY).toDouble(), width.toDouble(), height.toDouble(), stack)
        flingX = x
        flingY = y
        if (next == v && scroller.isFinished) {
            scroller.forceFinished(true)
            reportPage()
        } else {
            viewport = next
            surface.invalidate()
            reportPage()
            if (!scroller.isFinished) postInvalidateOnAnimation()
        }
    }

    private fun startPinch(e: MotionEvent, secondIndex: Int) {
        val first = (0 until e.pointerCount).firstOrNull { it != secondIndex } ?: return
        mode = Mode.PINCH
        pinchA = e.getPointerId(first)
        pinchB = e.getPointerId(secondIndex)
        val (span, fx, fy) = pinchGeometry(e) ?: return
        lastSpan = span
        initialPinchSpan = span
        lastFocusX = fx
        lastFocusY = fy
        lastPinchTime = e.eventTime
        pinchVelX = 0.0
        pinchVelY = 0.0
    }

    private fun pinchGeometry(e: MotionEvent): Triple<Double, Double, Double>? {
        val a = e.findPointerIndex(pinchA)
        val b = e.findPointerIndex(pinchB)
        if (a < 0 || b < 0) return null
        val ax = e.getX(a).toDouble()
        val ay = e.getY(a).toDouble()
        val bx = e.getX(b).toDouble()
        val by = e.getY(b).toDouble()
        return Triple(hypot(ax - bx, ay - by), (ax + bx) / 2, (ay + by) / 2)
    }

    private fun pinch(e: MotionEvent) {
        val v = viewport ?: return
        val (span, fx, fy) = pinchGeometry(e) ?: return
        val w = width.toDouble()
        val h = height.toDouble()
        var next = v
        val dfx = fx - lastFocusX
        val dfy = fy - lastFocusY
        val dt = (e.eventTime - lastPinchTime).coerceAtLeast(1L)
        // Smooth exponential moving average of two-finger pan velocity for natural inertia on release.
        val instVx = dfx * 1000.0 / dt
        val instVy = dfy * 1000.0 / dt
        pinchVelX = pinchVelX * 0.4 + instVx * 0.6
        pinchVelY = pinchVelY * 0.4 + instVy * 0.6
        // Avoid accidental micro-zoom jitter during pure two-finger vertical scrolling unless span changes meaningfully.
        val spanChanged = abs(span - initialPinchSpan) > 10.0 * density
        if (lastSpan > 0 && span > 0 && spanChanged) {
            next = rules.zoom(next, span / lastSpan, fx, fy, w, h, stack)
            lastSpan = span
        }
        next = rules.pan(next, dfx, dfy, w, h, stack)
        viewport = next
        lastFocusX = fx
        lastFocusY = fy
        lastPinchTime = e.eventTime
        surface.invalidate()
        reportPage()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val v = viewport
        if (v != null && event.isFromSource(InputDevice.SOURCE_CLASS_POINTER) && event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val wheel = event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble()
            val hwheel = event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble()
            if (event.metaState and KeyEvent.META_CTRL_ON != 0) {
                scroller.forceFinished(true)
                viewport = rules.zoom(v, Math.pow(1.1, wheel), event.x.toDouble(), event.y.toDouble(), width.toDouble(), height.toDouble(), stack)
                surface.invalidate()
                reportPage()
            } else {
                val step = 72 * density.toDouble()
                smoothPanBy((-hwheel * step).toFloat(), (wheel * step).toFloat(), durationMs = 160)
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            when {
                keyCode == KeyEvent.KEYCODE_Z && event.isShiftPressed -> { listener?.onRedoRequested(); return true }
                keyCode == KeyEvent.KEYCODE_Z -> { listener?.onUndoRequested(); return true }
                keyCode == KeyEvent.KEYCODE_Y -> { listener?.onRedoRequested(); return true }
                keyCode == KeyEvent.KEYCODE_V -> { listener?.onPasteRequested(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        InkImages.removeListener(imagesReady)
        cancelGesture(null)
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------- drawing

    private inner class Surface(context: Context) : View(context) {
        private val shadow = 1.5f * density
        private val selectionRect = RectF()
        private val selectionOutline = RectF()
        private val pageRect = RectF()
        private val visibleUnits = RectF()
        private val tileRect = RectF()
        private val inverse = Matrix()
        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val darkLayer = DarkPaper.layerPaint()
        private val layerRect = RectF()

        /** The PDF page under page [page]: best cached bitmap now, plus a sharp detail tile when zoomed in. */
        private fun drawPdfPage(canvas: Canvas, page: InkSession.Page, m: Matrix, scale: Float) {
            val r = pdf ?: return
            val index = page.pdfPageIndex ?: return
            pageRect.set(0f, 0f, page.width, page.height)
            m.mapRect(pageRect)
            val longSide = maxOf(pageRect.width(), pageRect.height()).toInt()
            r.page(index, longSide, page.height / page.width)?.let { canvas.drawBitmap(it, null, pageRect, bitmapPaint) }
            if (!r.needsDetail(longSide)) return
            // The part of this page that is on screen, in page units.
            visibleUnits.set(0f, 0f, width.toFloat(), height.toFloat())
            if (!visibleUnits.intersect(pageRect)) return
            val w = visibleUnits.width().toInt()
            val h = visibleUnits.height().toInt()
            m.invert(inverse)
            inverse.mapRect(visibleUnits)
            r.requestTile(index, visibleUnits, scale, w, h)
            r.tile(index)?.let { t ->
                tileRect.set(t.rect)
                m.mapRect(tileRect)
                canvas.drawBitmap(t.bitmap, null, tileRect, bitmapPaint)
            }
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(backgroundArgb)
            val s = session ?: return
            val c = controller ?: return
            val v = viewport ?: return
            val selection = c.selection
            val live = c.selectionTransform
            val liveMatrix = live?.let { similarityMatrix(it) }
            for (i in stack.visible(v.visibleWorld(width.toDouble(), height.toDouble()))) {
                val page = s.pages.getOrNull(i) ?: continue
                val m = pageToScreen(i, v)
                // Dark paper transforms the page and PDF, while embedded photos retain their original colours.
                val edge = if (arrangement.mode == PageMode.INFINITE) shadow * 0.5f else shadow
                val pageLayer = if (darkPaper) {
                    layerRect.set(0f, 0f, page.width, page.height)
                    m.mapRect(layerRect)
                    layerRect.inset(-shadow * 2, -shadow * 2)
                    canvas.saveLayer(layerRect, darkLayer)
                } else -1
                renderer.drawPaper(canvas, page.width, page.height, page.template, m, edge, page.layout)
                drawPdfPage(canvas, page, m, v.scale.toFloat())
                if (pageLayer >= 0) canvas.restoreToCount(pageLayer)

                val editing = selection != null && selection.pageId == page.id && liveMatrix != null
                val hidden = hiddenItemId
                val visibleItems = if (hidden == null) page.items.items else page.items.items.filter { it.id != hidden }
                // Images are the bottom ink pass. Draw them outside DarkPaper's filter so their colours are unchanged.
                renderer.drawInk(
                    canvas,
                    visibleItems.filterIsInstance<com.appname.notes.core.ink.model.ImageItem>(),
                    page.width,
                    page.height,
                    m,
                    hidden = if (editing) selection.ids else emptySet(),
                )
                val inkLayer = if (darkPaper) {
                    layerRect.set(0f, 0f, page.width, page.height)
                    m.mapRect(layerRect)
                    layerRect.inset(-shadow * 2, -shadow * 2)
                    canvas.saveLayer(layerRect, darkLayer)
                } else -1
                renderer.drawInk(
                    canvas,
                    visibleItems.filterNot { it is com.appname.notes.core.ink.model.ImageItem },
                    page.width,
                    page.height,
                    m,
                    // Ink being dragged is drawn after all pages, unclipped, so it can be carried onto another page.
                    hidden = if (editing) selection.ids else emptySet(),
                )
                if (c.activePageId == page.id) {
                    c.shapePreview?.let { renderer.drawInk(canvas, listOf(it), page.width, page.height, m) }
                    c.liveStroke()?.let { (brush, points) ->
                        if (this@InkCanvasView.liveStroke == null) renderer.drawLive(canvas, brush, points, m)
                    }
                }
                if (inkLayer >= 0) canvas.restoreToCount(inkLayer)
                if (c.activePageId == page.id) {
                    if (c.lassoPath.isNotEmpty()) {
                        renderer.drawLasso(canvas, c.lassoPath.map { toScreen(i, v, it) }, density)
                    }
                    c.eraserAt?.let { renderer.drawEraser(canvas, toScreen(i, v, it), (c.eraserRadius * v.scale).toFloat(), density) }
                }
                if (selection != null && selection.pageId == page.id) {
                    val b = selection.bounds
                    val r = selectionRect
                    r.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
                    liveMatrix?.mapRect(r)
                    m.mapRect(r)
                    selectionOutline.set(r)
                    selectionOutline.inset(-4 * density, -4 * density)
                    renderer.drawSelection(canvas, selectionOutline, density)
                    renderer.drawHandle(canvas, r.left, r.top, density)
                    renderer.drawHandle(canvas, r.right, r.top, density)
                    renderer.drawHandle(canvas, r.left, r.bottom, density)
                    renderer.drawHandle(canvas, r.right, r.bottom, density)
                    renderer.drawRotateHandle(canvas, r.centerX(), selectionOutline.top, r.top - (InkInputController.ROTATE_HANDLE_DP * density).toFloat(), density)
                }
            }
            // The selection while it is moved, resized or turned: on top, not cut off at its page's edge.
            if (selection != null && liveMatrix != null) {
                val i = s.pageIndex(selection.pageId)
                val page = s.pages.getOrNull(i)
                if (page != null) {
                    val selectedItems = page.items.items.filter { it.id in selection.ids }
                    val pageToScreen = pageToScreen(i, v)
                    renderer.drawInk(
                        canvas,
                        selectedItems.filterIsInstance<com.appname.notes.core.ink.model.ImageItem>(),
                        page.width,
                        page.height,
                        pageToScreen,
                        transformed = selection.ids,
                        itemTransform = liveMatrix,
                        clip = false,
                    )
                    val layer = if (darkPaper) canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), darkLayer) else -1
                    renderer.drawInk(
                        canvas,
                        selectedItems.filterNot { it is com.appname.notes.core.ink.model.ImageItem },
                        page.width,
                        page.height,
                        pageToScreen,
                        transformed = selection.ids,
                        itemTransform = liveMatrix,
                        clip = false,
                    )
                    if (layer >= 0) canvas.restoreToCount(layer)
                }
            }
            activeRuler()?.let { rulerPainter.draw(canvas, it) }
            if (v != lastDrawn) {
                lastDrawn = v
                onTransform?.invoke()
            }
            // The laser pointer: on top of everything, fading; keeps animating until it has gone.
            val now = SystemClock.uptimeMillis()
            if (laserTrail.active(now)) {
                laserPainter.draw(canvas, laserTrail, now, laser, density)
                postInvalidateOnAnimation()
            }
        }
    }

    private fun toScreen(i: Int, v: Viewport, page: Vec): Vec = v.toScreen(stack.toWorld(i, page))

    /** Our page-unit transform as an Android matrix: scale and rotate around the pivot, then move. */
    private fun similarityMatrix(t: Similarity): Matrix = Matrix().apply {
        val px = t.pivot.x.toFloat()
        val py = t.pivot.y.toFloat()
        setScale(t.scale.toFloat(), t.scale.toFloat(), px, py)
        postRotate(t.rotationDeg.toFloat(), px, py)
        postTranslate(t.dx.toFloat(), t.dy.toFloat())
    }
}

/** Touch radius of the ruler's turning knob, and how close to its edge a stroke must start to follow it (dp). */
private const val KNOB_DP = 26.0
private const val RULER_SNAP_DP = 24.0
