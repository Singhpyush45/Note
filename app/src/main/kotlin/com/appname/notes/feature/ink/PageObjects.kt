package com.appname.notes.feature.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import androidx.core.graphics.withSave
import com.appname.notes.core.ink.model.ImageItem
import com.appname.notes.core.ink.model.Placement
import com.appname.notes.core.ink.model.TextAlign
import com.appname.notes.core.ink.model.TextFont
import com.appname.notes.core.ink.model.TextItem
import com.appname.notes.feature.attachments.AttachmentFileStore
import com.appname.notes.feature.attachments.decodeImage
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.ceil

/**
 * Photos placed on handwritten pages (owner request 8a). Decodes them off the main thread at about the size they
 * are shown, keeps them in a memory cache (1/12 of the app heap), and tells listeners when one is ready so the
 * page redraws. Until then a light placeholder is drawn. [sync] decodes on the calling thread (export, thumbnails).
 */
object InkImages {
    @Volatile
    private var dir: File? = null
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 12).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val loading = HashSet<String>()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ink-images").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()

    fun init(context: Context) {
        if (dir == null) dir = File(context.applicationContext.filesDir, AttachmentFileStore.DIR)
    }

    fun initDir(directory: File) {
        if (dir == null) dir = directory
    }

    fun isCached(fileName: String, targetPx: Int = 512): Boolean {
        val want = bucket(targetPx)
        return cache.get("$fileName@$want") != null || listOf(2048, 1024, 512, 256).any { cache.get("$fileName@$it") != null }
    }

    fun addListener(l: () -> Unit) = synchronized(listeners) { listeners.add(l) }
    fun removeListener(l: () -> Unit) = synchronized(listeners) { listeners.remove(l) }

    /** Steps of decode size, so zooming doesn't decode again for every small change. */
    private fun bucket(px: Int): Int = when {
        px <= 256 -> 256
        px <= 512 -> 512
        px <= 1024 -> 1024
        else -> 2048
    }

    /** The best bitmap ready now for [fileName] at about [targetPx]; starts a decode if a sharper one is needed. */
    fun get(fileName: String, targetPx: Int): Bitmap? {
        val want = bucket(targetPx)
        val key = "$fileName@$want"
        cache.get(key)?.let { return it }
        request(fileName, want)
        // Something smaller meanwhile, if we have it.
        return listOf(2048, 1024, 512, 256).firstNotNullOfOrNull { cache.get("$fileName@$it") }
    }

    /** Decodes now on this thread (background work only). */
    fun sync(fileName: String, targetPx: Int): Bitmap? {
        val want = bucket(targetPx)
        val key = "$fileName@$want"
        cache.get(key)?.let { return it }
        val d = dir ?: return null
        return decode(File(d, fileName), want)?.also { cache.put(key, it) }
    }

    private fun request(fileName: String, want: Int) {
        val key = "$fileName@$want"
        val d = dir ?: return
        synchronized(loading) { if (!loading.add(key)) return }
        worker.execute {
            val bmp = decode(File(d, fileName), want)
            if (bmp != null) cache.put(key, bmp)
            synchronized(loading) { loading.remove(key) }
            if (bmp != null) main.post { synchronized(listeners) { listeners.toList() }.forEach { it() } }
        }
    }

    private fun decode(file: File, px: Int): Bitmap? = try {
        if (file.exists()) decodeImage(file, px) else null
    } catch (e: OutOfMemoryError) {
        cache.evictAll()
        null
    } catch (e: Exception) {
        null
    }
}

/** Drawing photos and text boxes on a page; canvas in page units. */
internal object PageObjectPainter {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val placeholder = Paint().apply { color = 0x14000000 }
    private val rect = RectF()

    fun drawImage(canvas: Canvas, item: ImageItem, pxPerUnit: Float, sync: Boolean = false) {
        val p = item.place
        val target = (maxOf(p.width, p.height) * pxPerUnit).toInt().coerceAtLeast(64)
        val bmp = if (sync) InkImages.sync(item.fileName, target) else InkImages.get(item.fileName, target)
        // Guarantee true photo colours: no colour filter, full opacity, normal blending.
        bitmapPaint.colorFilter = null
        bitmapPaint.xfermode = null
        bitmapPaint.alpha = 255
        canvas.withSave {
            rotate(p.rotationDeg, p.cx, p.cy)
            if (item.flipped) scale(-1f, 1f, p.cx, p.cy)
            rect.set(p.cx - p.width / 2, p.cy - p.height / 2, p.cx + p.width / 2, p.cy + p.height / 2)
            if (bmp != null) drawBitmap(bmp, null, rect, bitmapPaint) else drawRect(rect, placeholder)
        }
    }

    private val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEEF2FB.toInt() }
    private val chipEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8AB4F8.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val playDisc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A73E8.toInt() }
    private val playMark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF202124.toInt(); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5F6368.toInt() }

    /** A recording on the page: a rounded chip with a play button, its name and length. */
    fun drawAudio(canvas: Canvas, item: com.appname.notes.core.ink.model.AudioItem) {
        val p = item.place
        canvas.withSave {
            rotate(p.rotationDeg, p.cx, p.cy)
            val l = p.cx - p.width / 2
            val t = p.cy - p.height / 2
            rect.set(l, t, l + p.width, t + p.height)
            val r = p.height / 2
            drawRoundRect(rect, r, r, chip)
            drawRoundRect(rect, r, r, chipEdge)
            val dcx = l + r
            val dcy = t + r
            val dr = r * 0.72f
            drawCircle(dcx, dcy, dr, playDisc)
            val path = android.graphics.Path().apply {
                moveTo(dcx - dr * 0.32f, dcy - dr * 0.45f)
                lineTo(dcx + dr * 0.5f, dcy)
                lineTo(dcx - dr * 0.32f, dcy + dr * 0.45f)
                close()
            }
            drawPath(path, playMark)
            titlePaint.textSize = p.height * 0.3f
            timePaint.textSize = p.height * 0.24f
            val textLeft = l + 2 * r + p.height * 0.1f
            val room = (l + p.width - p.height * 0.3f - textLeft).coerceAtLeast(1f)
            val title = android.text.TextUtils.ellipsize(item.title, titlePaint, room, android.text.TextUtils.TruncateAt.END).toString()
            drawText(title, textLeft, t + p.height * 0.45f, titlePaint)
            drawText(formatDuration(item.durationMs), textLeft, t + p.height * 0.8f, timePaint)
        }
    }

    private fun formatDuration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    fun drawText(canvas: Canvas, item: TextItem) {
        val p = item.place
        val layout = TextLayouts.layout(item.text, item.font, item.bold, item.italic, item.align, item.brush.size, item.brush.colorArgb, p.width)
        canvas.withSave {
            rotate(p.rotationDeg, p.cx, p.cy)
            translate(p.cx - p.width / 2, p.cy - p.height / 2)
            layout.draw(this)
        }
    }
}

/** Text layout for text boxes: the phone's font families, wrapped to the box width. */
object TextLayouts {
    fun typeface(font: TextFont, bold: Boolean, italic: Boolean): Typeface {
        val family = when (font) {
            TextFont.SANS -> "sans-serif"
            TextFont.SERIF -> "serif"
            TextFont.MONO -> "monospace"
            TextFont.CURSIVE -> "cursive"
            TextFont.CASUAL -> "casual"
            TextFont.CONDENSED -> "sans-serif-condensed"
            TextFont.ROUNDED -> "sans-serif-medium"
        }
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(family, style)
    }

    fun layout(text: String, font: TextFont, bold: Boolean, italic: Boolean, align: TextAlign, size: Float, color: Int, width: Float): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typeface(font, bold, italic)
            textSize = size
            this.color = color
        }
        val alignment = when (align) {
            TextAlign.START -> Layout.Alignment.ALIGN_NORMAL
            TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, ceil(width.toDouble()).toInt().coerceAtLeast(1))
            .setAlignment(alignment)
            .setIncludePad(false)
            .build()
    }

    /** The box a text needs at [width] (height measured), placed with its top-left at [left], [top]. */
    fun place(text: String, font: TextFont, bold: Boolean, italic: Boolean, align: TextAlign, size: Float, width: Float, left: Float, top: Float, rotationDeg: Float = 0f): Placement {
        val h = layout(text.ifEmpty { " " }, font, bold, italic, align, size, 0, width).height.toFloat()
        return Placement(left + width / 2, top + h / 2, width, h, rotationDeg)
    }
}
