package com.appname.notes.core.ink.input

import kotlin.math.hypot

/**
 * The laser pointer's trail (owner brief §17): where the pointer has been in the last [tailMillis], in screen
 * pixels. Each point fades out as it ages; after the pointer lifts the rest of the trail fades the same way and
 * disappears. Nothing here is ever saved into the note.
 *
 * Rainbow ("RGB") mode colours the trail by distance along it, shifting with time, so the colours flow along the
 * line. Not thread-safe: used on the main thread only.
 */
class LaserTrail(val tailMillis: Long = DEFAULT_TAIL_MILLIS) {

    /** One point to draw: [alpha] 0..1, [hue] 0..360 for rainbow mode, [stroke] separates lifts. */
    data class Point(val x: Float, val y: Float, val alpha: Float, val hue: Float, val stroke: Int)

    private val xs = ArrayList<Float>()
    private val ys = ArrayList<Float>()
    private val ts = ArrayList<Long>()
    private val along = ArrayList<Float>()
    private val strokes = ArrayList<Int>()
    private var stroke = 0
    private var distance = 0f

    /** True while the pointer is down. */
    var down: Boolean = false
        private set

    /** Where the pointer is now (while down), for the bright head of the trail. */
    var headX: Float = 0f
        private set
    var headY: Float = 0f
        private set

    fun start(x: Float, y: Float, timeMillis: Long, density: Float) {
        stroke++
        down = true
        add(x, y, timeMillis, density, first = true)
    }

    fun move(x: Float, y: Float, timeMillis: Long, density: Float) {
        if (!down) return
        add(x, y, timeMillis, density, first = false)
    }

    fun end() {
        down = false
    }

    fun clear() {
        xs.clear(); ys.clear(); ts.clear(); along.clear(); strokes.clear()
        down = false
    }

    /** Something is still visible at [now] (keep animating). */
    fun active(now: Long): Boolean {
        prune(now)
        return down || ts.isNotEmpty()
    }

    /** Visible points, oldest first, with their fade and rainbow hue at [now]. */
    fun points(now: Long): List<Point> {
        prune(now)
        val shift = (now % RAINBOW_PERIOD_MILLIS) * 360f / RAINBOW_PERIOD_MILLIS
        return ts.indices.map { i ->
            val age = (now - ts[i]).coerceAtLeast(0)
            val alpha = (1f - age.toFloat() / tailMillis).coerceIn(0f, 1f)
            Point(xs[i], ys[i], alpha, hue(along[i], shift), strokes[i])
        }
    }

    /** The hue of the head (newest point) at [now], for rainbow mode. */
    fun headHue(now: Long): Float = hue(distance, (now % RAINBOW_PERIOD_MILLIS) * 360f / RAINBOW_PERIOD_MILLIS)

    private fun hue(alongDp: Float, shift: Float) = ((alongDp * DEGREES_PER_DP - shift) % 360f + 360f) % 360f

    private fun add(x: Float, y: Float, t: Long, density: Float, first: Boolean) {
        if (!first && xs.isNotEmpty()) {
            val last = xs.size - 1
            // Ignore jitter below a pixel: it adds points without changing the line.
            val d = hypot(x - xs[last], y - ys[last])
            if (d < MIN_STEP_PX) {
                headX = x; headY = y
                ts[last] = t // still here: keep the tip bright
                return
            }
            distance += d / density.coerceAtLeast(0.1f)
        }
        xs += x; ys += y; ts += t; along += distance; strokes += stroke
        headX = x; headY = y
        if (ts.size > MAX_POINTS) dropFirst(ts.size - MAX_POINTS)
    }

    private fun prune(now: Long) {
        var n = 0
        while (n < ts.size && now - ts[n] >= tailMillis) n++
        // While down, the newest point is the tip: it never fades away under the pen.
        if (down && n == ts.size && n > 0) n--
        if (n > 0) dropFirst(n)
    }

    private fun dropFirst(n: Int) {
        repeat(n) { xs.removeAt(0); ys.removeAt(0); ts.removeAt(0); along.removeAt(0); strokes.removeAt(0) }
    }

    companion object {
        const val DEFAULT_TAIL_MILLIS = 650L
        const val RAINBOW_PERIOD_MILLIS = 1500L

        /** One full rainbow every 360/1.5 = 240 dp of trail. */
        const val DEGREES_PER_DP = 1.5f
        private const val MIN_STEP_PX = 1f
        private const val MAX_POINTS = 600
    }
}
