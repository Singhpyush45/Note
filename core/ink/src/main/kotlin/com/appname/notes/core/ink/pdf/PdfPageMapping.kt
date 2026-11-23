package com.appname.notes.core.ink.pdf

import com.appname.notes.core.ink.geometry.Vec

/**
 * A PDF page's visible area: its crop box in PDF points (1/72 inch, origin bottom-left, y up) and the
 * clockwise display rotation from `/Rotate`.
 */
data class PdfPageBox(
    val left: Double,
    val bottom: Double,
    val right: Double,
    val top: Double,
    val rotation: Int = 0,
) {
    init {
        require(right > left && top > bottom) { "empty page box" }
    }

    /** Rotation normalised to 0, 90, 180 or 270 (PDF allows any multiple of 90, also negative). */
    val normalizedRotation: Int = ((rotation % 360) + 360) % 360 / 90 * 90

    val width: Double get() = right - left
    val height: Double get() = top - bottom
}

/**
 * Maps between our page units and PDF user space (ADR-0009 Part B).
 *
 * Our page units: 1/96 inch, origin at the top-left of the page **as displayed** (after rotation), y down.
 * PDF user space: points, origin bottom-left of the unrotated page, y up.
 *
 * With s = 72/96 = 0.75 points per unit, a displayed point is (X, Y) = (s·u, s·v), measured from the
 * displayed top-left. Rotating the page back (W, H = unrotated crop-box size, (dx, dy) from its
 * bottom-left corner):
 * ```
 *   rotation   0:  dx = X       dy = H − Y
 *   rotation  90:  dx = Y       dy = X
 *   rotation 180:  dx = W − X   dy = Y
 *   rotation 270:  dx = W − Y   dy = H − X
 * ```
 * Each is an affine map, given as a PDF `cm` matrix [a b c d e f]: x' = a·u + c·v + e, y' = b·u + d·v + f.
 */
object PdfPageMapping {

    const val POINTS_PER_UNIT = 72.0 / 96.0

    /** Size of the page as displayed, in our units. */
    fun displayedSize(box: PdfPageBox): Pair<Float, Float> {
        val sideways = box.normalizedRotation == 90 || box.normalizedRotation == 270
        val w = if (sideways) box.height else box.width
        val h = if (sideways) box.width else box.height
        return (w / POINTS_PER_UNIT).toFloat() to (h / POINTS_PER_UNIT).toFloat()
    }

    /** The `cm` matrix that turns our page units into PDF user space. */
    fun unitsToUser(box: PdfPageBox): DoubleArray {
        val s = POINTS_PER_UNIT
        return when (box.normalizedRotation) {
            0 -> doubleArrayOf(s, 0.0, 0.0, -s, box.left, box.top)
            90 -> doubleArrayOf(0.0, s, s, 0.0, box.left, box.bottom)
            180 -> doubleArrayOf(-s, 0.0, 0.0, s, box.right, box.bottom)
            else -> doubleArrayOf(0.0, -s, -s, 0.0, box.right, box.top)
        }
    }

    fun toUser(box: PdfPageBox, p: Vec): Vec {
        val m = unitsToUser(box)
        return Vec(m[0] * p.x + m[2] * p.y + m[4], m[1] * p.x + m[3] * p.y + m[5])
    }

    /** Inverse of [toUser]. */
    fun toUnits(box: PdfPageBox, q: Vec): Vec {
        val m = unitsToUser(box)
        val det = m[0] * m[3] - m[1] * m[2]
        val x = q.x - m[4]
        val y = q.y - m[5]
        return Vec((m[3] * x - m[2] * y) / det, (-m[1] * x + m[0] * y) / det)
    }
}
