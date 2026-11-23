package com.appname.notes.core.ink.input

import com.appname.notes.core.ink.geometry.Vec
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The ruler (owner brief §16): a straight edge on screen. Lines drawn along either long edge come out straight;
 * the ruler itself moves by dragging its body and turns with its knob. Screen pixels, angle in degrees
 * (clockwise, 0 = horizontal). Pure geometry, so it can be tested.
 */
data class Ruler(
    val cx: Double,
    val cy: Double,
    val angleDeg: Double,
    val length: Double,
    val width: Double,
) {
    private val rad get() = Math.toRadians(angleDeg)

    /** Unit vector along the ruler, and the normal pointing to its lower edge. */
    val along: Vec get() = Vec(cos(rad), sin(rad))
    val normal: Vec get() = Vec(-sin(rad), cos(rad))

    /** Point [p] in ruler coordinates: distance along (from centre) and across (positive = lower edge side). */
    fun local(p: Vec): Vec {
        val dx = p.x - cx
        val dy = p.y - cy
        return Vec(dx * along.x + dy * along.y, dx * normal.x + dy * normal.y)
    }

    fun onBody(p: Vec): Boolean {
        val l = local(p)
        return abs(l.x) <= length / 2 && abs(l.y) <= width / 2
    }

    /** The turning knob: a circle at the right end, inside the ruler. */
    fun knob(): Vec = Vec(cx + along.x * (length / 2 - width / 2), cy + along.y * (length / 2 - width / 2))

    fun onKnob(p: Vec, radius: Double): Boolean = com.appname.notes.core.ink.geometry.dist(p, knob()) <= radius

    /** Which edge [p] is drawing along (outside the ruler, within [snap] of it): +1 lower, −1 upper, or null. */
    fun edgeNear(p: Vec, snap: Double): Int? {
        val l = local(p)
        if (abs(l.x) > length / 2 + snap) return null
        val outside = abs(l.y) - width / 2
        if (outside < -snap / 2 || outside > snap) return null
        return if (l.y >= 0) 1 else -1
    }

    /** [p] moved onto the line just outside [edge], so the stroke runs straight along it. */
    fun project(p: Vec, edge: Int): Vec {
        val l = local(p)
        val across = edge * width / 2
        return Vec(cx + along.x * l.x + normal.x * across, cy + along.y * l.x + normal.y * across)
    }

    fun moved(dx: Double, dy: Double) = copy(cx = cx + dx, cy = cy + dy)

    /** Turned to the angle of [p] around the centre, snapping to every 15° within 3°. */
    fun turnedTowards(p: Vec): Ruler {
        var deg = Math.toDegrees(kotlin.math.atan2(p.y - cy, p.x - cx))
        val snapped = Math.round(deg / 15.0) * 15.0
        if (abs(deg - snapped) < 3.0) deg = snapped
        return copy(angleDeg = deg)
    }
}
