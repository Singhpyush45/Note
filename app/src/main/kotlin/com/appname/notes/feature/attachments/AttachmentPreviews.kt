package com.appname.notes.feature.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.feature.pdf.renderPdfPage
import java.io.File
import java.io.IOException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Small pictures of attachments for note strips, library cards and the viewer (FR-009 "previews").
 *
 * Photos are decoded at roughly the size shown (a 12-megapixel photo is never loaded whole for a card) and
 * turned upright from their EXIF orientation. PDFs show their first page. Results are kept in a memory cache
 * sized to 1/16 of the app heap, so scrolling back doesn't decode again; at most [PARALLEL] decodes run at once.
 */
class AttachmentPreviews(
    private val store: AttachmentFileStore,
    private val dispatchers: DispatcherProvider,
) {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val permits = Semaphore(PARALLEL)

    /** A picture of [fileName] about [targetPx] on its longer side, or null (not previewable / unreadable). */
    suspend fun load(fileName: String, kind: AttachmentKind, targetPx: Int): Bitmap? {
        val key = "$fileName@$targetPx"
        cache.get(key)?.let { return it }
        if (kind == AttachmentKind.FILE || kind == AttachmentKind.AUDIO) return null
        val bitmap = permits.withPermit {
            withContext(dispatchers.io) {
                val file = store.file(fileName)
                try {
                    when (kind) {
                        AttachmentKind.IMAGE -> decodeImage(file, targetPx)
                        AttachmentKind.PDF -> renderPdfPage(file, 0, targetPx)
                        AttachmentKind.FILE, AttachmentKind.AUDIO -> null
                    }
                } catch (e: IOException) {
                    null
                } catch (e: SecurityException) {
                    null
                } catch (e: OutOfMemoryError) {
                    cache.evictAll()
                    null
                } catch (e: RuntimeException) { // damaged file
                    null
                }
            }
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    fun trim() = cache.evictAll()

    private companion object {
        const val PARALLEL = 2
    }
}

/**
 * Decodes [file] so its longer side is at least [targetPx] (powers of two, the cheap way BitmapFactory
 * scales while reading), then rotates/mirrors it upright.
 */
internal fun decodeImage(file: File, targetPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    if (longSide <= 0) return null
    var sample = 1
    while (longSide / (sample * 2) >= targetPx) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val (degrees, mirrored) = exifRotation(file)
    if (degrees == 0 && !mirrored) return bitmap
    val m = Matrix().apply {
        if (mirrored) postScale(-1f, 1f)
        postRotate(degrees.toFloat())
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true).also { if (it !== bitmap) bitmap.recycle() }
}
