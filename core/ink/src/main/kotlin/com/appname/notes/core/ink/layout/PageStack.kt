package com.appname.notes.core.ink.layout

import com.appname.notes.core.ink.geometry.Rect
import com.appname.notes.core.ink.geometry.Vec
import kotlin.math.min

/** How a note's pages are shown (owner brief §5): separate sheets, or one continuous surface that grows as you write. */
enum class PageMode { SPLIT, INFINITE }

/** Which way the pages follow each other: down (vertical scrolling) or across (horizontal scrolling). */
enum class ScrollAxis { VERTICAL, HORIZONTAL }

/** A note's page arrangement. Separate from the page size, as the owner asked. */
data class PageArrangement(val mode: PageMode = PageMode.SPLIT, val axis: ScrollAxis = ScrollAxis.VERTICAL) {
    /** Space between pages: a small one in infinite mode (owner request), so each page still reads as one. */
    val gap: Double get() = if (mode == PageMode.SPLIT) PageStack.GAP else PageStack.INFINITE_GAP

    companion object {
        val DEFAULT = PageArrangement()
    }
}

/**
 * Pages laid out one after another ("world" coordinates): below each other ([ScrollAxis.VERTICAL], like a PDF
 * viewer in continuous mode) or side by side ([ScrollAxis.HORIZONTAL]). Each page keeps its own page
 * coordinates; [toPage]/[toWorld] convert between the two. Pages smaller across the scroll direction are centred.
 */
class PageStack(sizes: List<Pair<Float, Float>>, val axis: ScrollAxis = ScrollAxis.VERTICAL, val gap: Double = GAP) {
    val count = sizes.size
    private val widths = DoubleArray(count) { sizes[it].first.toDouble() }
    private val heights = DoubleArray(count) { sizes[it].second.toDouble() }
    private val lefts = DoubleArray(count)
    private val tops = DoubleArray(count)

    /** Size of everything: the widest page (vertical) or all pages side by side (horizontal). */
    val width: Double
    val height: Double

    /** The widest / tallest single page. */
    val maxPageWidth: Double = widths.maxOrNull() ?: 0.0
    val maxPageHeight: Double = heights.maxOrNull() ?: 0.0

    val vertical: Boolean get() = axis == ScrollAxis.VERTICAL

    init {
        var at = 0.0
        for (i in 0 until count) {
            if (vertical) {
                tops[i] = at
                lefts[i] = (maxPageWidth - widths[i]) / 2
                at += heights[i] + gap
            } else {
                lefts[i] = at
                tops[i] = (maxPageHeight - heights[i]) / 2
                at += widths[i] + gap
            }
        }
        val length = if (count == 0) 0.0 else at - gap
        width = if (vertical) maxPageWidth else length
        height = if (vertical) length else maxPageHeight
    }

    fun left(i: Int): Double = lefts[i]

    fun top(i: Int): Double = tops[i]

    /** Where page [i] starts along the scroll direction. */
    fun start(i: Int): Double = if (vertical) tops[i] else lefts[i]

    /** Length of everything along the scroll direction. */
    val length: Double get() = if (vertical) height else width

    /** Page [i]'s rectangle in world coordinates. */
    fun rect(i: Int) = Rect(lefts[i], tops[i], lefts[i] + widths[i], tops[i] + heights[i])

    /** The page at world point [world], or the nearest one along the scroll direction (gaps go to the closer page). */
    fun pageAt(world: Vec): Int = pageAtMain(if (vertical) world.y else world.x)

    /** [pageAt] for a vertical stack, by world y. */
    fun pageAt(worldY: Double): Int = pageAtMain(worldY)

    private fun pageAtMain(at: Double): Int {
        if (count == 0) return -1
        var lo = 0
        var hi = count - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (start(mid) - gap / 2 <= at) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun toPage(i: Int, world: Vec) = Vec(world.x - lefts[i], world.y - tops[i])

    fun toWorld(i: Int, page: Vec) = Vec(page.x + lefts[i], page.y + tops[i])

    /** Pages whose rectangle intersects world rectangle [area], for drawing only what's visible. */
    fun visible(area: Rect): IntRange {
        if (count == 0) return IntRange.EMPTY
        val from = if (vertical) area.top else area.left
        val to = if (vertical) area.bottom else area.right
        val first = pageAtMain(from)
        var last = first
        while (last + 1 < count && start(last + 1) <= to) last++
        return first..last
    }

    companion object {
        const val GAP = 24.0

        /** Infinite mode: pages follow closely, with just a thin break between them. */
        const val INFINITE_GAP = 10.0
    }
}

/**
 * World → screen mapping: screen = world · [scale] + ([dx], [dy]), in pixels.
 * Like a 2-D camera with uniform zoom.
 */
data class Viewport(val scale: Double, val dx: Double, val dy: Double) {
    fun toScreen(w: Vec) = Vec(w.x * scale + dx, w.y * scale + dy)
    fun toWorld(s: Vec) = Vec((s.x - dx) / scale, (s.y - dy) / scale)

    /** Visible world area of a [viewW]×[viewH] view. */
    fun visibleWorld(viewW: Double, viewH: Double) = Rect(-dx / scale, -dy / scale, (viewW - dx) / scale, (viewH - dy) / scale)
}

/**
 * Zoom and scroll rules for the page view. All sizes in pixels except where noted.
 * [actualSize] is the scale at which a page unit (1/96 inch) is shown at its real size on this screen.
 */
class ViewportRules(
    private val actualSize: Double,
    private val margin: Double,
) {
    /** Page width fills the view, but never more than real size (tablets) — the natural reading zoom. */
    fun fitScale(viewW: Double, stack: PageStack, viewH: Double = Double.MAX_VALUE): Double {
        if (stack.maxPageWidth <= 0) return actualSize
        val byWidth = (viewW - 2 * margin) / stack.maxPageWidth
        // Side by side: a whole page fits on screen, like turning pages in a book.
        val byHeight = if (stack.vertical || viewH == Double.MAX_VALUE) Double.MAX_VALUE else (viewH - 2 * margin) / stack.maxPageHeight
        return min(min(byWidth, byHeight), actualSize).coerceAtLeast(MIN_ABSOLUTE)
    }

    /** Zooming out stops at a quarter of the reading zoom: enough for "fit page" of a tall page on a wide screen. */
    fun minScale(viewW: Double, stack: PageStack, viewH: Double = Double.MAX_VALUE) = fitScale(viewW, stack, viewH) * MIN_ZOOM
    fun maxScale() = actualSize * 4

    /** Initial view: fitted, top of [page] at the top. */
    fun initial(viewW: Double, viewH: Double, stack: PageStack, page: Int = 0): Viewport {
        val s = fitScale(viewW, stack, viewH)
        val start = if (page in 0 until stack.count) stack.start(page) else 0.0
        val v = if (stack.vertical) Viewport(s, 0.0, margin - start * s) else Viewport(s, margin - start * s, 0.0)
        return clamp(v, viewW, viewH, stack)
    }

    /**
     * Keeps the pages on screen. Across the scroll direction a narrower stack is centred; along it, a shorter
     * stack starts at the margin. Otherwise the edges are clamped.
     */
    fun clamp(v: Viewport, viewW: Double, viewH: Double, stack: PageStack): Viewport {
        val s = v.scale.coerceIn(minScale(viewW, stack, viewH), maxScale())
        val w = stack.width * s
        val h = stack.height * s
        val dx = when {
            w + 2 * margin > viewW -> v.dx.coerceIn(viewW - margin - w, margin)
            stack.vertical -> (viewW - w) / 2
            else -> margin
        }
        val dy = when {
            h + 2 * margin > viewH -> v.dy.coerceIn(viewH - margin - h, margin)
            stack.vertical -> margin
            else -> (viewH - h) / 2
        }
        return Viewport(s, dx, dy)
    }

    /** Zooms by [factor] keeping the screen point ([fx], [fy]) fixed (under the fingers). */
    fun zoom(v: Viewport, factor: Double, fx: Double, fy: Double, viewW: Double, viewH: Double, stack: PageStack): Viewport {
        val s = (v.scale * factor).coerceIn(minScale(viewW, stack, viewH), maxScale())
        val k = s / v.scale
        return clamp(Viewport(s, fx - (fx - v.dx) * k, fy - (fy - v.dy) * k), viewW, viewH, stack)
    }

    fun pan(v: Viewport, ddx: Double, ddy: Double, viewW: Double, viewH: Double, stack: PageStack) =
        clamp(v.copy(dx = v.dx + ddx, dy = v.dy + ddy), viewW, viewH, stack)

    private companion object {
        const val MIN_ABSOLUTE = 0.05
        const val MIN_ZOOM = 0.25
    }

    /**
     * The page to report as current ("page 2 of 5"): the one in the middle of the view, except that
     * when scrolled to the very end the last page counts (it may be too short to reach the middle).
     */
    fun currentPage(v: Viewport, viewH: Double, stack: PageStack, viewW: Double = 0.0): Int {
        if (stack.count == 0) return 0
        val end = v.toWorld(Vec(viewW, viewH))
        if ((if (stack.vertical) end.y else end.x) >= stack.length) return stack.count - 1
        return stack.pageAt(v.toWorld(Vec(viewW / 2, viewH / 2))).coerceAtLeast(0)
    }

    /** Scrolls so page [i] starts at the top (vertical) or left edge (horizontal). */
    fun scrollTo(v: Viewport, i: Int, viewW: Double, viewH: Double, stack: PageStack): Viewport =
        clamp(
            if (stack.vertical) v.copy(dy = margin - stack.start(i) * v.scale) else v.copy(dx = margin - stack.start(i) * v.scale),
            viewW,
            viewH,
            stack,
        )

    /** Continuous 0..1 scroll progress along the document's primary scroll axis. */
    fun scrollFraction(v: Viewport, viewW: Double, viewH: Double, stack: PageStack): Float {
        if (stack.count == 0) return 0f
        return if (stack.vertical) {
            val range = (stack.height * v.scale + 2 * margin - viewH).coerceAtLeast(0.0)
            if (range <= 1e-4) 0f else ((margin - v.dy) / range).toFloat().coerceIn(0f, 1f)
        } else {
            val range = (stack.width * v.scale + 2 * margin - viewW).coerceAtLeast(0.0)
            if (range <= 1e-4) 0f else ((margin - v.dx) / range).toFloat().coerceIn(0f, 1f)
        }
    }

    /** Scrolls continuously to [fraction] (0..1) along the document's primary scroll axis. */
    fun scrollToFraction(v: Viewport, fraction: Float, viewW: Double, viewH: Double, stack: PageStack): Viewport {
        val f = if (fraction.isFinite()) fraction.coerceIn(0f, 1f).toDouble() else 0.0
        return if (stack.vertical) {
            val range = (stack.height * v.scale + 2 * margin - viewH).coerceAtLeast(0.0)
            clamp(v.copy(dy = margin - f * range), viewW, viewH, stack)
        } else {
            val range = (stack.width * v.scale + 2 * margin - viewW).coerceAtLeast(0.0)
            clamp(v.copy(dx = margin - f * range), viewW, viewH, stack)
        }
    }

    /** "Fit width" (SRS 7.1): page [i]'s width fills the view, even beyond real size; the page starts at the top. */
    fun fitWidth(i: Int, viewW: Double, viewH: Double, stack: PageStack): Viewport {
        val r = stack.rect(i)
        val s = ((viewW - 2 * margin) / r.width).coerceIn(minScale(viewW, stack, viewH), maxScale())
        return clamp(Viewport(s, margin - r.left * s, margin - r.top * s), viewW, viewH, stack)
    }

    /** "Fit page" (SRS 7.1): all of page [i] is visible, centred. */
    fun fitPage(i: Int, viewW: Double, viewH: Double, stack: PageStack): Viewport {
        val r = stack.rect(i)
        val s = min((viewW - 2 * margin) / r.width, (viewH - 2 * margin) / r.height).coerceIn(minScale(viewW, stack, viewH), maxScale())
        val c = r.center
        return clamp(Viewport(s, viewW / 2 - c.x * s, viewH / 2 - c.y * s), viewW, viewH, stack)
    }

    fun withinZoomRange(scale: Double, viewW: Double, stack: PageStack) = scale in minScale(viewW, stack)..maxScale()
}
