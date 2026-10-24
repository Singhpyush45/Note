package com.appname.notes.feature.pdf

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.repository.PdfRepository
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Small first-page pictures of PDF notes for library cards.
 *
 * Each call opens the file, renders one page into a small bitmap and closes it again, so nothing stays open
 * while scrolling. At most [PARALLEL] at a time, so a fast fling can't fill memory with renders.
 */
class PdfThumbnails(
    private val pdf: PdfRepository,
    private val store: PdfFileStore,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger,
) {
    private val permits = Semaphore(PARALLEL)

    /** PDF page [pageIndex] of [noteId], [widthPx] wide; null when the note has no readable PDF. */
    suspend fun render(noteId: NoteId, pageIndex: Int, widthPx: Int = WIDTH_PX): Bitmap? = permits.withPermit {
        withContext(dispatchers.io) {
            val doc = (pdf.document(noteId) as? AppResult.Success)?.value ?: return@withContext null
            try {
                renderPdfPage(store.file(doc.fileName), pageIndex, widthPx)
            } catch (e: IOException) {
                logger.warn(TAG, "PDF thumbnail failed", e)
                null
            } catch (e: SecurityException) {
                null
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: RuntimeException) {
                null
            }
        }
    }

    private companion object {
        const val TAG = "PdfThumbnails"
        const val WIDTH_PX = 240
        const val PARALLEL = 2
    }
}

/**
 * Renders page [pageIndex] of [file] [widthPx] wide on white, as RGB_565 (half the memory; a page has no
 * transparency). Blocking; may throw IOException/SecurityException/RuntimeException for unreadable files.
 */
internal fun renderPdfPage(file: java.io.File, pageIndex: Int, widthPx: Int): Bitmap? =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { r ->
            if (pageIndex !in 0 until r.pageCount) return@use null
            r.openPage(pageIndex).use { page ->
                val h = (widthPx.toFloat() * page.height / page.width).roundToInt().coerceIn(1, widthPx * 4)
                val argb = createBitmap(widthPx, h, Bitmap.Config.ARGB_8888) // the renderer needs ARGB
                argb.eraseColor(Color.WHITE)
                page.render(argb, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                argb.copy(Bitmap.Config.RGB_565, false).also { argb.recycle() }
            }
        }
    }
