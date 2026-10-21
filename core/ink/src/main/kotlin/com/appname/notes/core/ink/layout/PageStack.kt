package com.appname.notes.core.ink.layout

import com.appname.notes.core.ink.geometry.Rect
import com.appname.notes.core.ink.geometry.Vec
import kotlin.math.min

/**
 * Pages laid out one below another, like a PDF viewer in continuous mode ("world" coordinates).
 * Each page keeps its own page coordinates; [toPage]/[toWorld] convert between the two.
 * Narrower pages are centred.
 */
class PageStack(sizes: List<Pair<Float, Float>>, val gap: Double = GAP) {
    val count = sizes.size
    private val widths = DoubleArray(count) { sizes[it].first.toDouble() }
    private val heights = DoubleArray(count) { sizes[it].second.toDouble() }
    private val tops = DoubleArray(count)

    /** Width of the widest page. */
    val width: Double = widths.maxOrNull() ?: 0.0

    val height: Double

    init {
        var y = 0.0
        for (i in 0 until count) {
            tops[i] = y
            y += heights[i] + gap
        }
        height = if (count == 0) 0.0 else y - gap
    }

    fun left(i: Int): Double = (width - widths[i]) / 2

    fun top(i: Int): Double = tops[i]

    /** Page [i]'s rectangle in world coordinates. */
    fun rect(i: Int) = Rect(left(i), tops[i], left(i) + widths[i], tops[i] + heights[i])

    /** The page containing world y, or the nearest one (gaps belong to the closer page). */
    fun pageAt(worldY: Double): Int {
        if (count == 0) return -1
        var lo = 0
        var hi = count - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (tops[mid] - gap / 2 <= worldY) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun toPage(i: Int, world: Vec) = Vec(world.x - left(i), world.y - tops[i])

    fun toWorld(i: Int, page: Vec) = Vec(page.x + left(i), page.y + tops[i])

    /** Pages whose rectangle intersects world rectangle [area], for drawing only what's visible. */
    fun visible(area: Rect): IntRange {
        if (count == 0) return IntRange.EMPTY
        val first = pageAt(area.top)
        var last = first
        while (last + 1 < count && tops[last + 1] <= area.bottom) last++
        return first..last
    }

    companion object {
        const val GAP = 24.0
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
    fun fitScale(viewW: Double, stack: PageStack): Double {
        if (stack.width <= 0) return actualSize
        return min((viewW - 2 * margin) / stack.width, actualSize).coerceAtLeast(MIN_ABSOLUTE)
    }

    /** Zooming out stops at a quarter of the reading zoom: enough for "fit page" of a tall page on a wide screen. */
    fun minScale(viewW: Double, stack: PageStack) = fitScale(viewW, stack) * MIN_ZOOM
    fun maxScale() = actualSize * 4

    /** Initial view: fitted, top of [page] at the top. */
    fun initial(viewW: Double, viewH: Double, stack: PageStack, page: Int = 0): Viewport {
        val s = fitScale(viewW, stack)
        val top = if (page in 0 until stack.count) stack.top(page) else 0.0
        return clamp(Viewport(s, 0.0, margin - top * s), viewW, viewH, stack)
    }

    /** Keeps the pages on screen: centred when narrower than the view, edges clamped otherwise. */
    fun clamp(v: Viewport, viewW: Double, viewH: Double, stack: PageStack): Viewport {
        val s = v.scale.coerceIn(minScale(viewW, stack), maxScale())
        val w = stack.width * s
        val h = stack.height * s
        val dx = if (w + 2 * margin <= viewW) (viewW - w) / 2 else v.dx.coerceIn(viewW - margin - w, margin)
        val dy = if (h + 2 * margin <= viewH) margin else v.dy.coerceIn(viewH - margin - h, margin)
        return Viewport(s, dx, dy)
    }

    /** Zooms by [factor] keeping the screen point ([fx], [fy]) fixed (under the fingers). */
    fun zoom(v: Viewport, factor: Double, fx: Double, fy: Double, viewW: Double, viewH: Double, stack: PageStack): Viewport {
        val s = (v.scale * factor).coerceIn(minScale(viewW, stack), maxScale())
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
    fun currentPage(v: Viewport, viewH: Double, stack: PageStack): Int {
        if (stack.count == 0) return 0
        val bottom = v.toWorld(Vec(0.0, viewH)).y
        if (bottom >= stack.height) return stack.count - 1
        return stack.pageAt(v.toWorld(Vec(0.0, viewH / 2)).y).coerceAtLeast(0)
    }

    /** Scrolls so page [i] starts at the top. */
    fun scrollTo(v: Viewport, i: Int, viewW: Double, viewH: Double, stack: PageStack): Viewport =
        clamp(v.copy(dy = margin - stack.top(i) * v.scale), viewW, viewH, stack)

    /** "Fit width" (SRS 7.1): page [i]'s width fills the view, even beyond real size; the page starts at the top. */
    fun fitWidth(i: Int, viewW: Double, viewH: Double, stack: PageStack): Viewport {
        val r = stack.rect(i)
        val s = ((viewW - 2 * margin) / r.width).coerceIn(minScale(viewW, stack), maxScale())
        return clamp(Viewport(s, margin - r.left * s, margin - r.top * s), viewW, viewH, stack)
    }

    /** "Fit page" (SRS 7.1): all of page [i] is visible, centred. */
    fun fitPage(i: Int, viewW: Double, viewH: Double, stack: PageStack): Viewport {
        val r = stack.rect(i)
        val s = min((viewW - 2 * margin) / r.width, (viewH - 2 * margin) / r.height).coerceIn(minScale(viewW, stack), maxScale())
        val c = r.center
        return clamp(Viewport(s, viewW / 2 - c.x * s, viewH / 2 - c.y * s), viewW, viewH, stack)
    }

    fun withinZoomRange(scale: Double, viewW: Double, stack: PageStack) = scale in minScale(viewW, stack)..maxScale()
}
