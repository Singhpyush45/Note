package com.appname.notes.feature.textindex

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.core.domain.model.ExtractionMethod
import com.appname.notes.core.domain.model.IndexStatus
import com.appname.notes.core.domain.model.PageText
import com.appname.notes.core.domain.model.TextSource
import com.appname.notes.feature.attachments.AttachmentFileStore
import com.appname.notes.feature.attachments.decodeImage
import com.appname.notes.feature.pdf.PdfAnnotationWriter
import com.appname.notes.feature.pdf.PdfFileStore
import com.appname.notes.feature.pdf.renderPdfPage
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException

/** What extracting one source produced. */
data class Extraction(val pages: List<PageText>, val status: IndexStatus)

/**
 * Gets the text out of a document (FR-011), on the device:
 * - PDFs: the text layer first (exact and fast: slides, books, exported notes). Pages without one (scans,
 *   photos of pages) are read with OCR, up to [maxOcrPages] per document to keep battery use bounded (NFR-08).
 * - Photos: OCR.
 *
 * The stored originals are only read. Blocking: call from a background thread.
 */
class DocumentTextExtractor(
    private val attachments: AttachmentFileStore,
    private val pdfs: PdfFileStore,
    private val ocr: TextRecognizerEngine,
    @Suppress("unused") private val pdfBoxReady: PdfAnnotationWriter, // its construction initialises PdfBox
    private val logger: AppLogger,
    private val maxPages: Int = MAX_PAGES,
    private val maxOcrPages: Int = MAX_OCR_PAGES,
) {
    /** @throws OcrUnavailableException when OCR was needed and can't run (the caller records UNAVAILABLE). */
    fun extract(source: TextSource): Extraction = when (source) {
        is TextSource.PdfNoteSource -> pdf(pdfs.file(source.fileName))
        is TextSource.AttachmentSource -> when (source.kind) {
            AttachmentKind.IMAGE -> image(attachments.file(source.fileName))
            AttachmentKind.PDF -> pdf(attachments.file(source.fileName))
            AttachmentKind.FILE -> Extraction(emptyList(), IndexStatus.EMPTY)
        }
    }

    private fun image(file: File): Extraction {
        if (!file.isFile) return Extraction(emptyList(), IndexStatus.FAILED)
        val bitmap = decodeImage(file, OCR_PX) ?: return Extraction(emptyList(), IndexStatus.FAILED)
        val text = try { ocr.recognize(bitmap) } finally { bitmap.recycle() }
        return result(listOf(PageText(0, text.trim(), ExtractionMethod.OCR)))
    }

    private fun pdf(file: File): Extraction {
        if (!file.isFile) return Extraction(emptyList(), IndexStatus.FAILED)
        val layer = textLayer(file) // null when PdfBox can't read the file: then every page is OCR'd
        val pageCount = layer?.size ?: rendererPageCount(file) ?: return Extraction(emptyList(), IndexStatus.FAILED)
        val pages = ArrayList<PageText>()
        var ocrBudget = maxOcrPages
        for (i in 0 until minOf(pageCount, maxPages)) {
            val text = layer?.getOrNull(i)?.trim().orEmpty()
            if (text.count { !it.isWhitespace() } >= MIN_TEXT_LAYER_CHARS) {
                pages += PageText(i, text, ExtractionMethod.PDF_TEXT)
            } else if (ocrBudget > 0) {
                ocrBudget--
                val bitmap = renderPdfPage(file, i, PDF_OCR_WIDTH_PX, compact = false) ?: continue
                val read = try { ocr.recognize(bitmap) } finally { bitmap.recycle() }
                pages += PageText(i, read.trim(), ExtractionMethod.OCR)
            }
        }
        return result(pages)
    }

    /** Text of every page from the PDF's text layer, or null if PdfBox can't open it. */
    private fun textLayer(file: File): List<String>? = try {
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            val stripper = PDFTextStripper()
            List(minOf(doc.numberOfPages, maxPages)) { i ->
                stripper.startPage = i + 1
                stripper.endPage = i + 1
                stripper.getText(doc)
            }
        }
    } catch (e: IOException) {
        logger.warn(TAG, "No text layer read", e)
        null
    } catch (e: RuntimeException) { // malformed content streams
        logger.warn(TAG, "No text layer read", e)
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun rendererPageCount(file: File): Int? = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    private fun result(pages: List<PageText>): Extraction {
        val kept = pages.filter { it.text.isNotBlank() }
        return Extraction(kept, if (kept.isEmpty()) IndexStatus.EMPTY else IndexStatus.DONE)
    }

    private companion object {
        const val TAG = "DocumentText"
        const val MAX_PAGES = 2000
        const val MAX_OCR_PAGES = 40
        /** Fewer visible characters than this on a page = no real text layer (e.g. only a page number). */
        const val MIN_TEXT_LAYER_CHARS = 20
        /** Long side for photo OCR: enough for small print without decoding a 12 MP photo whole. */
        const val OCR_PX = 2000
        /** Width for PDF page OCR: ~190 dpi on A4, readable for 9 pt text; ~13 MB per page while it runs. */
        const val PDF_OCR_WIDTH_PX = 1600
    }
}
