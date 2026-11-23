package com.appname.notes.core.ink.recognition

import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.geometry.dist
import com.appname.notes.core.ink.geometry.interiorAngles
import com.appname.notes.core.ink.geometry.polygonArea
import com.appname.notes.core.ink.geometry.rotate
import com.appname.notes.core.ink.model.ShapeKind
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetic hand-drawn strokes for testing the recognizer: shapes the way people really draw them (wobbly
 * edges, rounded corners, curls at the ends, overshoot when closing, a still "hold" tail) and handwriting that
 * must never be turned into a shape. Seeded, so every run produces the same strokes.
 *
 * Coordinates are dp (page units at 100% zoom).
 */
class ShapeCorpus(seed: Long) {
    private val rng = Random(seed)

    private fun uniform(a: Double, b: Double) = a + (b - a) * rng.nextDouble()
    private fun randInt(a: Int, b: Int) = a + rng.nextInt(b - a + 1)
    private fun gauss(sigma: Double) = rng.nextGaussian() * sigma

    val shapes: Map<ShapeKind, () -> List<Vec>> = linkedMapOf(
        ShapeKind.LINE to ::line,
        ShapeKind.CIRCLE to ::circle,
        ShapeKind.ELLIPSE to ::ellipse,
        ShapeKind.RECTANGLE to { rect(square = false) },
        ShapeKind.SQUARE to { rect(square = true) },
        ShapeKind.TRIANGLE to ::triangle,
        ShapeKind.ARROW to ::arrow,
    )

    val handwriting: Map<String, () -> List<Vec>> = linkedMapOf(
        "cursive" to ::cursive,
        "signature" to ::signature,
        "zigzag" to ::zigzag,
        "figure8" to ::figure8,
        "spiral" to ::spiral,
        "word" to ::letterWord,
    )

    /** Extra sensor noise of [sigma] dp on every point. */
    fun noisy(points: List<Vec>, sigma: Double) = points.map { Vec(it.x + gauss(sigma), it.y + gauss(sigma)) }

    // ------------------------------------------------------------------ building blocks

    private fun jitter(pts: List<Vec>, amount: Double) = pts.map { Vec(it.x + gauss(amount), it.y + gauss(amount)) }

    /** A slightly wavy line from [a] to [b], like a real hand. */
    private fun wobbleLine(a: Vec, b: Vec, n: Int, amp: Double): List<Vec> {
        val l = dist(a, b)
        val nx = -(b.y - a.y) / l
        val ny = (b.x - a.x) / l
        val f1 = uniform(0.5, 2.0)
        val f2 = uniform(2.0, 5.0)
        val p1 = uniform(0.0, 6.28)
        val p2 = uniform(0.0, 6.28)
        val a1 = uniform(0.3, 1.0) * amp
        val a2 = uniform(0.0, 0.4) * amp
        return List(n) { i ->
            val t = i.toDouble() / (n - 1)
            val off = a1 * sin(f1 * PI * t + p1) * sin(PI * t) + a2 * sin(f2 * PI * t + p2)
            Vec(a.x + t * (b.x - a.x) + nx * off, a.y + t * (b.y - a.y) + ny * off)
        }
    }

    /** Small curl a pen makes when it lands or lifts. */
    private fun hook(p: Vec, ang: Double, size: Double): List<Vec> {
        val k = randInt(2, 5)
        return (k downTo 1).map { i -> Vec(p.x + size * cos(ang + i * 0.6) * i / k, p.y + size * sin(ang + i * 0.6) * i / k) }
    }

    /** The pen staying (almost) still at the end: that's the "hold". */
    private fun holdTail(pts: List<Vec>, size: Double): List<Vec> {
        val last = pts.last()
        return pts + List(randInt(0, 12)) { Vec(last.x + gauss(0.003 * size), last.y + gauss(0.003 * size)) }
    }

    // ------------------------------------------------------------------ shapes

    fun line(): List<Vec> {
        val l = uniform(30.0, 600.0)
        val ang = uniform(0.0, 2 * PI)
        val a = Vec(uniform(0.0, 500.0), uniform(0.0, 500.0))
        val b = Vec(a.x + l * cos(ang), a.y + l * sin(ang))
        var pts = wobbleLine(a, b, randInt(30, 120), 0.025 * l)
        if (rng.nextDouble() < 0.5) pts = hook(a, uniform(0.0, 6.28), 0.03 * l) + pts
        if (rng.nextDouble() < 0.5) pts = pts + hook(b, uniform(0.0, 6.28), 0.03 * l).asReversed()
        return holdTail(jitter(pts, 0.004 * l), l)
    }

    private fun ellipseStroke(rx: Double, ry: Double): List<Vec> {
        val rot = uniform(0.0, PI)
        val c = Vec(300.0, 300.0)
        val start = uniform(0.0, 2 * PI)
        val sweep = 2 * PI + Math.toRadians(uniform(-12.0, 25.0))
        val n = randInt(50, 150)
        val d = if (rng.nextBoolean()) 1 else -1
        val f = uniform(1.5, 3.0)
        val ph = uniform(0.0, 6.28)
        val amp = uniform(0.01, 0.04)
        val out = List(n) { i ->
            val t = start + d * sweep * i / (n - 1)
            val k = 1 + amp * sin(f * t + ph)
            rotate(Vec(c.x + rx * k * cos(t), c.y + ry * k * sin(t)), rot, c)
        }
        return jitter(out, 0.004 * maxOf(rx, ry))
    }

    fun circle(): List<Vec> {
        val r = uniform(15.0, 300.0)
        return ellipseStroke(r, r * uniform(0.94, 1.0))
    }

    fun ellipse(): List<Vec> {
        val rx = uniform(60.0, 300.0)
        return ellipseStroke(rx, rx * uniform(0.35, 0.68))
    }

    private fun polygonStroke(corners: List<Vec>): List<Vec> {
        val n = corners.size
        val start = randInt(0, n - 1)
        val seq = corners.subList(start, n) + corners.subList(0, start) + corners[start]
        val out = ArrayList<Vec>()
        val size = (0 until n).maxOf { dist(corners[it], corners[(it + 1) % n]) }
        for (i in 0 until n) {
            val seg = wobbleLine(seq[i], seq[i + 1], randInt(15, 40), 0.02 * dist(seq[i], seq[i + 1]))
            out.addAll(if (i == 0) seg else seg.drop(1))
        }
        // Closing overshoot or undershoot.
        val over = uniform(-0.06, 0.12) * size
        val a = seq[0]
        val b = seq[1]
        val l = dist(a, b)
        var result: List<Vec> = out
        if (over > 0) {
            result = out + Vec(a.x + (b.x - a.x) * over / l, a.y + (b.y - a.y) * over / l)
        } else {
            val k = (-over / size * out.size / n * 2).toInt()
            if (k > 0) result = out.dropLast(k)
        }
        return jitter(result, 0.004 * size)
    }

    private fun roundCorners(pts: List<Vec>, frac: Double): List<Vec> {
        val n = pts.size
        val out = ArrayList<Vec>()
        for (i in 0 until n) {
            val a = pts[Math.floorMod(i - 1, n)]
            val b = pts[i]
            val c = pts[(i + 1) % n]
            out.add(Vec(b.x + (a.x - b.x) * frac, b.y + (a.y - b.y) * frac))
            out.add(Vec(b.x + ((a.x + c.x) / 2 - b.x) * frac * 0.6, b.y + ((a.y + c.y) / 2 - b.y) * frac * 0.6))
            out.add(Vec(b.x + (c.x - b.x) * frac, b.y + (c.y - b.y) * frac))
        }
        return out
    }

    fun rect(square: Boolean): List<Vec> {
        val w = uniform(30.0, 500.0)
        val h = if (square) {
            w * uniform(0.95, 1.05)
        } else {
            w * (if (rng.nextBoolean()) uniform(0.35, 0.75) else uniform(1.35, 2.5))
        }
        val rot = Math.toRadians(if (rng.nextInt(4) < 3) 0.0 else uniform(-25.0, 25.0))
        val c = Vec(300.0, 300.0)
        var pts = listOf(
            Vec(c.x - w / 2, c.y - h / 2),
            Vec(c.x + w / 2, c.y - h / 2),
            Vec(c.x + w / 2, c.y + h / 2),
            Vec(c.x - w / 2, c.y + h / 2),
        ).map { rotate(it, rot, c) }
        if (rng.nextBoolean()) pts = pts.asReversed()
        val outline = if (rng.nextBoolean()) roundCorners(pts, uniform(0.02, 0.12)) else pts
        return holdTail(polygonStroke(outline), w)
    }

    fun triangle(): List<Vec> {
        while (true) {
            val pts = List(3) { Vec(uniform(0.0, 400.0), uniform(0.0, 400.0)) }
            if (interiorAngles(pts).min() > 28 && polygonArea(pts) > 3000) return polygonStroke(pts)
        }
    }

    fun arrow(): List<Vec> {
        val l = uniform(120.0, 600.0)
        val ang = uniform(0.0, 2 * PI)
        val a = Vec(300.0, 300.0)
        val b = Vec(a.x + l * cos(ang), a.y + l * sin(ang))
        val shaft = wobbleLine(a, b, randInt(30, 80), 0.02 * l)
        val head = l * uniform(0.12, 0.3)
        val spread = Math.toRadians(uniform(25.0, 45.0))
        val back = ang + PI
        val c1 = Vec(b.x + head * cos(back + spread), b.y + head * sin(back + spread))
        val c2 = Vec(b.x + head * cos(back - spread), b.y + head * sin(back - spread))
        // tip → barb 1 → tip → barb 2
        var tail = wobbleLine(b, c1, 10, 0.01 * head).drop(1) +
            wobbleLine(c1, b, 10, 0.01 * head).drop(1) +
            wobbleLine(b, c2, 10, 0.01 * head).drop(1)
        if (rng.nextBoolean()) {
            // Second barb cut short and redrawn straight, like a hurried V.
            val cut = tail.size * 2 / 3
            tail = tail.subList(0, cut) + wobbleLine(tail[cut], c2, 6, 0.0).drop(1)
        }
        return jitter(shaft + tail, 0.003 * l)
    }

    // ------------------------------------------------------------------ handwriting (must stay ink)

    fun cursive(): List<Vec> {
        val loops = randInt(3, 8)
        val rr = uniform(8.0, 30.0)
        val d = rr * uniform(1.1, 1.8)
        val n = loops * 24
        return List(n) { i ->
            val t = i.toDouble() / n * loops * 2 * PI
            val x = rr * t - d * sin(t) + gauss(0.5)
            val y = -d * cos(t) * uniform(0.9, 1.1)
            Vec(x, y + 0.08 * x * sin(0.01 * x))
        }
    }

    private fun bezier(p0: Vec, p1: Vec, p2: Vec, p3: Vec, n: Int) = List(n) { i ->
        val t = i.toDouble() / (n - 1)
        val u = 1 - t
        Vec(
            u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x,
            u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y,
        )
    }

    fun signature(): List<Vec> {
        val w = uniform(150.0, 500.0)
        val h = w * uniform(0.2, 0.5)
        val pts = List(randInt(6, 12)) { Vec(uniform(0.0, w), uniform(0.0, h)) }
            .map { it to it.x + uniform(-w * 0.2, w * 0.2) }.sortedBy { it.second }.map { it.first }
        val out = ArrayList<Vec>()
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val c1 = Vec(a.x + uniform(-h, h), a.y + uniform(-h, h))
            val c2 = Vec(b.x + uniform(-h, h), b.y + uniform(-h, h))
            val seg = bezier(a, c1, c2, b, 16)
            out.addAll(if (i == 0) seg else seg.drop(1))
        }
        return out
    }

    fun zigzag(): List<Vec> {
        val n = randInt(4, 9)
        val w = uniform(15.0, 40.0)
        val h = uniform(20.0, 60.0)
        val out = ArrayList<Vec>()
        for (i in 0 until n) {
            val a = Vec(i * w, if (i % 2 == 0) 0.0 else h)
            val b = Vec((i + 1) * w, if (i % 2 == 0) h else 0.0)
            val seg = wobbleLine(a, b, 8, 0.03 * h)
            out.addAll(if (i == 0) seg else seg.drop(1))
        }
        return out
    }

    fun figure8(): List<Vec> {
        val a = uniform(30.0, 150.0)
        val n = 100
        val s = uniform(0.0, 6.28)
        val out = List(n + randInt(0, 10)) { i ->
            val t = s + 2 * PI * i / n
            Vec(a * sin(t), a * sin(t) * cos(t))
        }
        return jitter(out, 0.01 * a)
    }

    fun spiral(): List<Vec> {
        val turns = uniform(1.6, 3.5)
        val n = 150
        val r0 = uniform(5.0, 20.0)
        val k = uniform(8.0, 25.0)
        val out = List(n) { i ->
            val t = turns * 2 * PI * i / n
            val r = r0 + k * t
            Vec(r * cos(t), r * sin(t))
        }
        return jitter(out, 0.5)
    }

    /** Several small letters in one stroke ("lle", "nn"…): up-down strokes with loops and humps. */
    fun letterWord(): List<Vec> {
        val out = ArrayList<Vec>()
        var x = 0.0
        repeat(randInt(3, 6)) {
            val s = uniform(15.0, 35.0)
            val seg = when (randInt(0, 2)) {
                0 -> List(13) { i -> val t = i / 12.0 * 2 * PI; Vec(x + s * 0.3 * sin(t), -s * 2 * sin(t / 2) * sin(t / 2)) }
                1 -> List(13) { i -> Vec(x + s * i / 12, -s * abs(sin(PI * i / 12))) }
                else -> List(13) { i -> val t = i / 12.0 * 2 * PI; Vec(x + s * 0.5 + s * 0.5 * cos(t + PI), -s * 0.5 * sin(t)) }
            }
            out.addAll(seg)
            x += s
        }
        return jitter(out, 0.4)
    }
}
