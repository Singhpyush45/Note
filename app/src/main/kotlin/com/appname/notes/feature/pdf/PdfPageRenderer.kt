package com.appname.notes.feature.pdf

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.ink.pdf.PdfPageMapping
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders PDF pages for the workspace (SRS 7.1, PDF-06), lazily and within a memory budget.
 *
 * - One worker thread owns Android's [PdfRenderer] (it renders one page at a time and isn't thread-safe).
 * - Whole-page bitmaps come in a few sizes ("buckets") so zooming doesn't re-render all the time; the cache
 *   is limited to about 1/6 of the app's memory and emptied under memory pressure.
 * - When zoomed in beyond the largest bucket, the visible part of a page is rendered as a sharp "detail tile"
 *   at screen resolution once the gesture settles.
 * - Out of memory while rendering → cache emptied and smaller bitmaps used; never a crash.
 *
 * Call everything except [close] from the main thread; results arrive via [onUpdate] on the main thread.
 */
class PdfPageRenderer(
    context: Context,
    private val file: File,
    private val logger: AppLogger,
) : Closeable {

    /** Called on the main thread when a requested bitmap is ready (or rendering failed). */
    var onUpdate: (() -> Unit)? = null

    /** True when the file can't be opened at all (deleted, damaged): the UI shows a message. */
    var failed: Boolean = false
        private set

    /** Called once on the main thread when the file turns out to be unreadable. */
    var onFailed: (() -> Unit)? = null

    private val app = context.applicationContext
    private val thread = HandlerThread("PdfRender").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    // Worker-thread state.
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    // Main-thread state.
    private val maxCacheBytes = (Runtime.getRuntime().maxMemory() / 6).coerceAtMost(MAX_CACHE_BYTES).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pending = HashSet<String>()
    private var maxLongSide = MAX_LONG_SIDE
    private var closed = false

    private var tile: Tile? = null
    private var tilePending: TileRequest? = null
    private val tileRunnable = Runnable { startTile() }

    /** A rendered part of a page: [rect] in page units (1/96 in), drawn at [pxPerUnit]. */
    class Tile(val page: Int, val rect: RectF, val pxPerUnit: Float, val bitmap: Bitmap)

    private data class TileRequest(val page: Int, val rect: RectF, val pxPerUnit: Float, val outW: Int, val outH: Int)

    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            // Background or the system is low on memory: let go of everything not on screen.
            if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) trim()
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() = trim()
    }

    init {
        app.registerComponentCallbacks(memoryCallbacks)
        // Open now, off the main thread: the first page draws sooner and a missing/damaged file is reported at once.
        worker.post { open() }
    }

    // ---------------------------------------------------------------- whole pages

    /**
     * Best available bitmap of [page] (any size), and asks for the size that suits a page shown
     * [screenLongSidePx] pixels long. [aspect] = height / width of the page as displayed.
     */
    fun page(page: Int, screenLongSidePx: Int, aspect: Float): Bitmap? {
        if (closed) return null
        val wanted = bucketFor(screenLongSidePx)
        val key = key(page, wanted)
        cache.get(key)?.let { return it }
        request(page, wanted, aspect)
        // Meanwhile: the largest smaller one, else any.
        return BUCKETS.filter { it < wanted }.asReversed().firstNotNullOfOrNull { cache.get(key(page, it)) }
            ?: BUCKETS.firstNotNullOfOrNull { cache.get(key(page, it)) }
    }

    /** True when a page shown this big needs a detail tile to look sharp. */
    fun needsDetail(screenLongSidePx: Int) = screenLongSidePx > maxLongSide * DETAIL_THRESHOLD

    private fun bucketFor(px: Int): Int = BUCKETS.firstOrNull { it >= px && it <= maxLongSide } ?: BUCKETS.last { it <= maxLongSide }

    private fun key(page: Int, bucket: Int) = "$page@$bucket"

    private fun request(page: Int, bucket: Int, aspect: Float) {
        val key = key(page, bucket)
        if (!pending.add(key) || failed) return
        val (w, h) = if (aspect >= 1f) (bucket / aspect).roundToInt() to bucket else bucket to (bucket * aspect).roundToInt()
        worker.post {
            val bmp = render(page, w.coerceAtLeast(1), h.coerceAtLeast(1), null)
            main.post { delivered(key, bmp) }
        }
    }

    private fun delivered(key: String, bmp: Bitmap?) {
        pending.remove(key)
        if (closed) return
        if (bmp != null) cache.put(key, bmp)
        onUpdate?.invoke()
    }

    // ---------------------------------------------------------------- detail tiles

    /** The current detail tile if it is for [page], else null. */
    fun tile(page: Int): Tile? = tile?.takeIf { it.page == page }

    /**
     * Asks for a detail tile of [page] covering [rect] (page units) at [pxPerUnit] screen pixels per unit.
     * Rendering starts after a short pause so it doesn't run on every frame of a pinch.
     */
    fun requestTile(page: Int, rect: RectF, pxPerUnit: Float, outW: Int, outH: Int) {
        if (closed || failed || outW <= 0 || outH <= 0) return
        val current = tile
        if (current != null && current.page == page && sameScale(current.pxPerUnit, pxPerUnit) && current.rect.contains(rect)) return
        val req = TileRequest(page, RectF(rect), pxPerUnit, outW, outH)
        if (req == tilePending) return
        tilePending = req
        main.removeCallbacks(tileRunnable)
        main.postDelayed(tileRunnable, TILE_DELAY_MS)
    }

    private fun startTile() {
        val req = tilePending ?: return
        worker.post {
            // PDF points → tile pixels: 1 unit = 0.75 pt, so px per point = pxPerUnit / 0.75; then shift to the rect.
            val bmp = render(req.page, req.outW, req.outH) {
                val scale = (req.pxPerUnit / PdfPageMapping.POINTS_PER_UNIT).toFloat()
                Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(-req.rect.left * req.pxPerUnit, -req.rect.top * req.pxPerUnit)
                }
            }
            main.post {
                if (tilePending == req) tilePending = null
                if (!closed && bmp != null) tile = Tile(req.page, req.rect, req.pxPerUnit, bmp)
                onUpdate?.invoke()
            }
        }
    }

    /** Drops the detail tile (e.g. after zooming out). */
    fun clearTile() {
        main.removeCallbacks(tileRunnable)
        tilePending = null
        tile = null
    }

    // ---------------------------------------------------------------- worker

    private fun sameScale(a: Float, b: Float) = kotlin.math.abs(a - b) <= 0.02f * a

    /** Renders on the worker thread. [transform] maps PDF points to bitmap pixels; null = fit the whole page. */
    private fun render(page: Int, w: Int, h: Int, transform: (() -> Matrix)?): Bitmap? {
        val r = open() ?: return null
        if (page !in 0 until r.pageCount) return null
        return try {
            r.openPage(page).use { p ->
                val bmp = createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                p.render(bmp, null, transform?.invoke(), PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        } catch (e: OutOfMemoryError) {
            main.post { outOfMemory() }
            null
        } catch (e: IllegalStateException) { // renderer closed meanwhile
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun open(): PdfRenderer? {
        renderer?.let { return it }
        if (failed) return null
        return try {
            val d = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fd = d
            PdfRenderer(d).also { renderer = it }
        } catch (e: IOException) {
            fail(e)
        } catch (e: SecurityException) {
            fail(e)
        }
    }

    private fun fail(e: Exception): PdfRenderer? {
        logger.warn(TAG, "Can't open PDF", e)
        main.post {
            if (closed || failed) return@post
            failed = true
            onFailed?.invoke()
            onUpdate?.invoke()
        }
        return null
    }

    // ---------------------------------------------------------------- memory

    private fun outOfMemory() {
        cache.evictAll()
        tile = null
        maxLongSide = max(BUCKETS.first(), maxLongSide / 2)
        logger.warn(TAG, "Out of memory rendering PDF; page bitmaps now at most $maxLongSide px")
        onUpdate?.invoke()
    }

    fun trim() {
        cache.evictAll()
        tile = null
        onUpdate?.invoke()
    }

    override fun close() {
        if (closed) return
        closed = true
        onUpdate = null
        onFailed = null
        app.unregisterComponentCallbacks(memoryCallbacks)
        main.removeCallbacks(tileRunnable)
        worker.post {
            try {
                renderer?.close()
                fd?.close()
            } catch (e: IOException) {
                logger.warn(TAG, "Closing PDF failed", e)
            }
            renderer = null
        }
        thread.quitSafely()
        cache.evictAll()
        tile = null
    }

    /** For tests: bytes currently held by page bitmaps. */
    internal val cachedBytes: Int get() = cache.size()

    private companion object {
        const val TAG = "PdfPageRenderer"
        val BUCKETS = listOf(640, 960, 1280, 1920, 2560)
        const val MAX_LONG_SIDE = 2560
        const val MAX_CACHE_BYTES = 128L * 1024 * 1024
        const val DETAIL_THRESHOLD = 1.15f
        const val TILE_DELAY_MS = 120L
    }
}
