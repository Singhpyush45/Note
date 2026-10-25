package com.appname.notes.feature.ink

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.appname.notes.core.ink.geometry.StrokeOutline
import com.appname.notes.core.ink.model.Finish
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.Texture
import java.util.Random
import kotlin.math.floor

/**
 * Draws pen, pencil and brush variants (owner brief §12) from their [com.appname.notes.core.ink.model.StrokeSpec]:
 * the outline from [StrokeOutline], filled solid, through a grain texture, with a soft or spray edge, as a
 * watercolour wash with a darker rim, or as dots. Canvas in page units.
 *
 * Not thread-safe (reuses its paints): one instance per thread. With [cache], outlines are kept per stroke
 * (like the Jetpack Ink stroke cache) so scrolling doesn't rebuild them.
 *
 * Limits: blur (soft, spray, wash) needs Android 9+ with hardware drawing; older devices draw those edges hard.
 */
internal class StyledInk(private val cache: Boolean = true) {

    private class Built(val outline: Path?, val dots: FloatArray?, val endX0: Float, val endY0: Float, val endX1: Float, val endY1: Float, val w0: Float, val w1: Float)

    private val built = HashMap<String, Pair<StrokeItem, Built>>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }

    /** Forgets outlines of strokes that no longer exist. */
    fun retain(ids: Set<String>) {
        built.keys.retainAll(ids)
    }

    fun draw(canvas: Canvas, item: StrokeItem) {
        val b = build(item)
        val brush = item.brush
        val spec = brush.effectiveStyle.spec
        val alpha = ((brush.colorArgb ushr 24) * spec.opacity).toInt().coerceIn(0, 255)
        val color = (alpha shl 24) or (brush.colorArgb and 0xFFFFFF)
        fill.reset()
        fill.isAntiAlias = true
        fill.style = Paint.Style.FILL
        fill.color = color
        when (spec.finish) {
            Finish.SOFT, Finish.SPRAY -> fill.maskFilter = BlurMaskFilter((brush.size * spec.softness).coerceAtLeast(0.4f), BlurMaskFilter.Blur.NORMAL)
            Finish.WASH -> fill.maskFilter = BlurMaskFilter((brush.size * spec.softness).coerceAtLeast(0.4f), BlurMaskFilter.Blur.NORMAL)
            Finish.SOLID, Finish.DOTS -> Unit
        }

        if (spec.finish == Finish.DOTS) {
            val dots = b.dots ?: return
            val r = brush.size / 2
            for (i in dots.indices step 2) canvas.drawCircle(dots[i], dots[i + 1], r, fill)
            return
        }

        when (spec.texture) {
            Texture.NONE -> body(canvas, b, spec.roundCap, fill)
            Texture.BRISTLE -> {
                // Oil: a full body of paint, then darker bristle streaks pressed into it.
                body(canvas, b, spec.roundCap, fill)
                fill.shader = Textures.shader(Texture.BRISTLE)
                fill.color = darker(color, 0.55f)
                body(canvas, b, spec.roundCap, fill)
            }
            else -> {
                // Graphite, wax, chalk, charcoal, canvas: the colour shows only where the grain catches it.
                fill.shader = Textures.shader(spec.texture)
                body(canvas, b, spec.roundCap, fill)
            }
        }

        if (spec.finish == Finish.WASH) {
            // Pigment gathers at the edge as a watercolour stroke dries.
            rim.reset()
            rim.isAntiAlias = true
            rim.style = Paint.Style.STROKE
            rim.strokeJoin = Paint.Join.ROUND
            rim.strokeWidth = (brush.size * 0.08f).coerceAtLeast(0.4f)
            rim.color = (((alpha * 1.8f).toInt().coerceIn(0, 255)) shl 24) or (darker(color, 0.85f) and 0xFFFFFF)
            rim.maskFilter = BlurMaskFilter((brush.size * 0.05f).coerceAtLeast(0.3f), BlurMaskFilter.Blur.NORMAL)
            b.outline?.let { canvas.drawPath(it, rim) }
        }
    }

    private fun body(canvas: Canvas, b: Built, roundCap: Boolean, paint: Paint) {
        b.outline?.let { canvas.drawPath(it, paint) }
        if (roundCap || b.outline == null) {
            canvas.drawCircle(b.endX0, b.endY0, b.w0 / 2, paint)
            if (b.outline != null) canvas.drawCircle(b.endX1, b.endY1, b.w1 / 2, paint)
        }
    }

    private fun build(item: StrokeItem): Built {
        if (cache) built[item.id]?.let { (key, value) -> if (key === item) return value }
        val p = item.points
        val spec = item.brush.effectiveStyle.spec
        val widths = StrokeOutline.widths(p, item.brush)
        val result = if (spec.finish == Finish.DOTS) {
            Built(null, StrokeOutline.dots(p, item.brush.size * spec.dotSpacing), p.x[0], p.y[0], p.x[0], p.y[0], widths[0], widths[0])
        } else {
            val o = StrokeOutline.outline(p, widths)
            val path = if (o.isEmpty()) {
                null
            } else {
                Path().apply {
                    moveTo(o[0], o[1])
                    var i = 2
                    while (i < o.size) { lineTo(o[i], o[i + 1]); i += 2 }
                    close()
                }
            }
            val last = p.size - 1
            Built(path, null, p.x[0], p.y[0], p.x[last], p.y[last], widths[0], widths[last])
        }
        if (cache) built[item.id] = item to result
        return result
    }

    private fun darker(argb: Int, f: Float): Int {
        val r = ((argb shr 16 and 0xFF) * f).toInt()
        val g = ((argb shr 8 and 0xFF) * f).toInt()
        val b = ((argb and 0xFF) * f).toInt()
        return (argb and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }
}

/**
 * Grain textures as small alpha-only tiles (the paint's colour shows through their alpha), generated once from a
 * fixed seed so a stroke looks the same every time it is drawn. Tiles repeat in page units, like paper tooth.
 */
internal object Textures {
    private const val SIZE = 128

    private val shaders = HashMap<Texture, BitmapShader>()

    @Synchronized
    fun shader(t: Texture): Shader = shaders.getOrPut(t) {
        BitmapShader(tile(t), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            // Texel size in page units: fine grain for graphite, coarser for wax and chalk.
            val unit = when (t) {
                Texture.GRAPHITE_FINE -> 0.25f
                Texture.GRAPHITE -> 0.35f
                Texture.GRAPHITE_COARSE, Texture.CHARCOAL -> 0.5f
                Texture.WAX, Texture.CHALK -> 0.6f
                Texture.CANVAS, Texture.BRISTLE -> 0.5f
                Texture.NONE -> 1f
            }
            setLocalMatrix(Matrix().apply { setScale(unit, unit) })
        }
    }

    /** Alpha 0..255 per texel. */
    internal fun alphas(t: Texture): IntArray {
        val rnd = Random(t.ordinal * 7919L + 17)
        val a = IntArray(SIZE * SIZE)
        when (t) {
            Texture.NONE -> a.fill(255)
            Texture.GRAPHITE_FINE -> grain(a, rnd, noise(rnd, 2), coverage = 0.6f, floor = 0.15f)
            Texture.GRAPHITE -> grain(a, rnd, noise(rnd, 4), coverage = 0.68f, floor = 0.2f)
            Texture.GRAPHITE_COARSE -> grain(a, rnd, noise(rnd, 6), coverage = 0.75f, floor = 0.25f)
            Texture.CHARCOAL -> grain(a, rnd, noise(rnd, 5), coverage = 0.82f, floor = 0.35f)
            Texture.WAX -> grain(a, rnd, noise(rnd, 8), coverage = 0.78f, floor = 0.05f, contrast = 3f)
            Texture.CHALK -> grain(a, rnd, noise(rnd, 6), coverage = 0.55f, floor = 0f, contrast = 4f)
            Texture.CANVAS -> for (y in 0 until SIZE) for (x in 0 until SIZE) {
                // A woven cloth: alternating over/under threads, slightly irregular.
                val warp = ((x / 4) + (y / 4)) % 2 == 0
                val thread = if (warp) (x % 4) else (y % 4)
                val v = 0.62f + 0.3f * (1f - kotlin.math.abs(thread - 1.5f) / 1.5f) + (rnd.nextFloat() - 0.5f) * 0.12f
                a[y * SIZE + x] = (v.coerceIn(0f, 1f) * 255).toInt()
            }
            Texture.BRISTLE -> {
                // Streaks along x (one per bristle), each with its own strength, broken here and there.
                val rows = FloatArray(SIZE) { rnd.nextFloat() }
                for (y in 0 until SIZE) for (x in 0 until SIZE) {
                    val streak = rows[y] * rows[y]
                    val gap = if (rnd.nextFloat() < 0.04f) 0f else 1f
                    a[y * SIZE + x] = (streak * gap * 200).toInt().coerceIn(0, 255)
                }
            }
        }
        return a
    }

    private fun tile(t: Texture): Bitmap {
        val a = alphas(t)
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ALPHA_8)
        // One byte per texel, row after row (ALPHA_8 rows are exactly SIZE bytes for SIZE = 128).
        bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(ByteArray(a.size) { a[it].toByte() }))
        return bmp
    }

    /** Smooth value noise in 0..1 with features about [cell] texels across; wraps at the tile edges. */
    private fun noise(rnd: Random, cell: Int): FloatArray {
        val g = SIZE / cell
        val grid = FloatArray(g * g) { rnd.nextFloat() }
        fun at(ix: Int, iy: Int) = grid[((iy % g + g) % g) * g + ((ix % g + g) % g)]
        return FloatArray(SIZE * SIZE) { i ->
            val x = (i % SIZE).toFloat() / cell
            val y = (i / SIZE).toFloat() / cell
            val x0 = floor(x).toInt()
            val y0 = floor(y).toInt()
            val fx = x - x0
            val fy = y - y0
            val top = at(x0, y0) * (1 - fx) + at(x0 + 1, y0) * fx
            val bottom = at(x0, y0 + 1) * (1 - fx) + at(x0 + 1, y0 + 1) * fx
            top * (1 - fy) + bottom * fy
        }
    }

    /**
     * Paper tooth: where the noise is above the threshold for [coverage] the colour catches (with fine speckle),
     * elsewhere only [floor] of it shows. [contrast] sharpens the edge between the two (wax, chalk).
     */
    private fun grain(out: IntArray, rnd: Random, n: FloatArray, coverage: Float, floor: Float, contrast: Float = 2f) {
        val threshold = 1f - coverage
        for (i in out.indices) {
            val edge = ((n[i] - threshold) * contrast + 0.5f).coerceIn(0f, 1f)
            val speckle = 0.75f + rnd.nextFloat() * 0.25f
            val v = floor + (1f - floor) * edge * speckle
            out[i] = (v.coerceIn(0f, 1f) * 255).toInt()
        }
    }
}
