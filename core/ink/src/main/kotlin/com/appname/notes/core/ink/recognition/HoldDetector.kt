package com.appname.notes.core.ink.recognition

import kotlin.math.hypot

/**
 * Detects "draw, then hold the pen still" (SRS 6.2).
 *
 * State machine, fed with every pen sample and with timer ticks (the pen may report no samples while it is
 * perfectly still, so the UI also calls [check] from a timer):
 *
 * ```
 *  DRAWING ──(still for holdMillis)──▶ HELD ──(moves beyond tolerance)──▶ DRAWING
 * ```
 *
 * "Still" means staying within [tolerance] (page units) of the anchor point. Any bigger move re-anchors.
 * Times are milliseconds on any monotonic clock (e.g. MotionEvent.eventTime).
 */
class HoldDetector(
    private val holdMillis: Long = DEFAULT_HOLD_MILLIS,
    private val tolerance: Double,
) {
    init {
        require(holdMillis > 0 && tolerance > 0)
    }

    enum class Event {
        /** The pen has been held still: show the shape preview. */
        HOLD,

        /** The pen moved again after a hold: hide the preview, the stroke continues as ink. */
        RELEASED,
    }

    private var anchorX = 0.0
    private var anchorY = 0.0
    private var anchorTime = 0L
    private var active = false

    /** True between [Event.HOLD] and the next [Event.RELEASED] or [reset]. */
    var held = false
        private set

    fun start(x: Double, y: Double, time: Long) {
        anchorX = x; anchorY = y; anchorTime = time
        active = true
        held = false
    }

    /** Feeds one pen sample. Returns an event when the state changes. */
    fun onPoint(x: Double, y: Double, time: Long): Event? {
        if (!active) {
            start(x, y, time)
            return null
        }
        if (hypot(x - anchorX, y - anchorY) > tolerance) {
            anchorX = x; anchorY = y; anchorTime = time
            if (held) {
                held = false
                return Event.RELEASED
            }
            return null
        }
        return check(time)
    }

    /** Timer tick. Returns [Event.HOLD] once when the pen has been still for long enough. */
    fun check(now: Long): Event? {
        if (!active || held) return null
        if (now - anchorTime >= holdMillis) {
            held = true
            return Event.HOLD
        }
        return null
    }

    /** Milliseconds until a hold would fire if the pen stays still (for scheduling the timer). */
    fun millisUntilHold(now: Long): Long = if (!active || held) -1 else (anchorTime + holdMillis - now).coerceAtLeast(0)

    fun reset() {
        active = false
        held = false
    }

    companion object {
        const val DEFAULT_HOLD_MILLIS = 500L
        const val MIN_HOLD_MILLIS = 300L
        const val MAX_HOLD_MILLIS = 1500L

        /** How far (dp) the pen may drift while "still". Converted to page units by the caller. */
        const val TOLERANCE_DP = 6.0
    }
}
