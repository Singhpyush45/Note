package com.appname.notes.feature.pdf

import android.content.Context
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.pdf.PdfInkOps
import com.appname.notes.core.ink.pdf.PdfOp
import com.appname.notes.core.ink.pdf.PdfPageBox
import com.appname.notes.core.ink.pdf.PdfPageMapping
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.OutputStream

/**
 * Reads page geometry from PDFs and writes annotated copies with PdfBox-Android (ADR-0009 Part B).
 *
 * Export is "flattened": ink is appended to each page as vector drawing commands, so every PDF viewer shows
 * it in the right place, but other apps see it as part of the page rather than as editable annotations
 * (SRS 7.3 allows this when documented). The app keeps the editable ink separately.
 *
 * Blocking I/O: call from a background thread.
 */
class PdfAnnotationWriter(context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    /** Crop box and rotation of every page, as Android's PdfRenderer displays them. */
    fun readPageBoxes(pdf: File): List<PdfPageBox> = open(pdf).use { doc ->
        doc.pages.map { it.box() }
    }

    /**
     * Writes a copy of [source] with [ink] (by 0-based page index) drawn on top to [out].
     * [source] is only read, never changed (PDF-05).
     */
    fun writeAnnotatedCopy(source: File, ink: Map<Int, List<InkItem>>, out: OutputStream) =
        writeAnnotatedCopy(source, ink.entries.sortedBy { it.key }.map { (i, items) -> AnnotatedPage.FromPdf(i, items) }, out)

    /** One page of the exported copy, in the note's order. */
    sealed interface AnnotatedPage {
        val items: List<InkItem>

        /** Page [index] of the original document, with [items] on top. */
        data class FromPdf(val index: Int, override val items: List<InkItem>) : AnnotatedPage

        /** A page added in the app (blank, [width] × [height] page units), with [items]. */
        data class Added(val width: Float, val height: Float, override val items: List<InkItem>) : AnnotatedPage
    }

    /**
     * Writes a copy of [source] laid out as [pages] (original pages, and pages added in the app in between), with
     * each page's ink, photos, text boxes and recording chips on top. [source] is only read (PDF-05).
     */
    fun writeAnnotatedCopy(source: File, pages: List<AnnotatedPage>, out: OutputStream) {
        open(source).use { doc ->
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true // the copy is the user's own
            val originals = (0 until doc.numberOfPages).map { doc.getPage(it) }
            var previous: PDPage? = null
            val alphaStates = HashMap<Float, PDExtendedGraphicsState>()
            for (entry in pages) {
                val page = when (entry) {
                    is AnnotatedPage.FromPdf -> originals.getOrNull(entry.index) ?: continue
                    is AnnotatedPage.Added -> {
                        val added = PDPage(PDRectangle(entry.width * POINTS_PER_UNIT, entry.height * POINTS_PER_UNIT))
                        val after = previous
                        if (after == null) doc.pages.insertBefore(added, originals.first()) else doc.pages.insertAfter(added, after)
                        added
                    }
                }
                previous = page
                val items = entry.items
                if (items.isEmpty()) continue
                val box = page.box()
                // APPEND + resetContext: the page's own drawing is wrapped in q…Q, so its state can't leak into ours.
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    val m = PdfPageMapping.unitsToUser(box)
                    cs.transform(Matrix(m[0].toFloat(), m[1].toFloat(), m[2].toFloat(), m[3].toFloat(), m[4].toFloat(), m[5].toFloat()))
                    cs.setLineJoinStyle(ROUND)
                    // Photos first (under the writing), as on screen; then text boxes and chips, as pictures.
                    for (item in items) if (item is com.appname.notes.core.ink.model.ImageItem) placeImage(doc, cs, item)
                    for (op in PdfInkOps.forItems(items)) {
                        when (op) {
                            is PdfOp.Style -> {
                                cs.setStrokingColor(op.r, op.g, op.b)
                                cs.setLineWidth(op.width)
                                cs.setLineCapStyle(if (op.roundCaps) ROUND else PROJECTING_SQUARE)
                                val state = alphaStates.getOrPut(op.alpha) {
                                    PDExtendedGraphicsState().apply { strokingAlphaConstant = op.alpha }
                                }
                                cs.setGraphicsStateParameters(state)
                            }
                            is PdfOp.MoveTo -> cs.moveTo(op.x, op.y)
                            is PdfOp.LineTo -> cs.lineTo(op.x, op.y)
                            is PdfOp.CurveTo -> cs.curveTo(op.x1, op.y1, op.x2, op.y2, op.x3, op.y3)
                            PdfOp.Close -> cs.closePath()
                            PdfOp.Stroke -> cs.stroke()
                        }
                    }
                    for (item in items) {
                        if (item is com.appname.notes.core.ink.model.TextItem || item is com.appname.notes.core.ink.model.AudioItem) placeRendered(doc, cs, item)
                    }
                }
            }
            doc.save(out)
        }
    }

    /** A photo at its place (turned, mirrored), from its stored file at about 3 pixels per page unit. */
    private fun placeImage(doc: PDDocument, cs: PDPageContentStream, item: com.appname.notes.core.ink.model.ImageItem) {
        val p = item.place
        val px = (maxOf(p.width, p.height) * PX_PER_UNIT).toInt().coerceIn(64, MAX_IMAGE_PX)
        val bmp = com.appname.notes.feature.ink.InkImages.sync(item.fileName, px) ?: return
        // A cut-out (transparent background) keeps its transparency; JPEG has none.
        val img = if (bmp.hasAlpha()) {
            com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, bmp)
        } else {
            com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(doc, bmp, JPEG_QUALITY)
        }
        draw(cs, img, p, mirrored = item.flipped)
    }

    /** Text boxes and recording chips: drawn exactly as on screen into a picture, so every font and script shows. */
    private fun placeRendered(doc: PDDocument, cs: PDPageContentStream, item: InkItem) {
        val p = when (item) {
            is com.appname.notes.core.ink.model.TextItem -> item.place
            is com.appname.notes.core.ink.model.AudioItem -> item.place
            else -> return
        }
        val w = (p.width * PX_PER_UNIT).toInt().coerceIn(1, MAX_IMAGE_PX)
        val h = (p.height * PX_PER_UNIT).toInt().coerceIn(1, MAX_IMAGE_PX)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.scale(w / p.width, h / p.height)
        c.translate(-(p.cx - p.width / 2), -(p.cy - p.height / 2))
        val flat = p.copy(rotationDeg = 0f)
        when (item) {
            is com.appname.notes.core.ink.model.TextItem -> com.appname.notes.feature.ink.PageObjectPainter.drawText(c, item.copy(place = flat))
            is com.appname.notes.core.ink.model.AudioItem -> com.appname.notes.feature.ink.PageObjectPainter.drawAudio(c, item.copy(place = flat))
            else -> Unit
        }
        val img = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, bmp)
        draw(cs, img, p, mirrored = false)
        bmp.recycle()
    }

    /** Draws [img] filling [p] in page-unit space (y down): centred, turned, upright (PDF images are y up). */
    private fun draw(cs: PDPageContentStream, img: com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject, p: com.appname.notes.core.ink.model.Placement, mirrored: Boolean) {
        cs.saveGraphicsState()
        cs.transform(Matrix.getTranslateInstance(p.cx, p.cy))
        cs.transform(Matrix.getRotateInstance(Math.toRadians(p.rotationDeg.toDouble()), 0f, 0f))
        val sx = if (mirrored) -p.width else p.width
        cs.drawImage(img, Matrix(sx, 0f, 0f, -p.height, -sx / 2, p.height / 2))
        cs.restoreGraphicsState()
    }

    private fun open(file: File): PDDocument =
        // Temp-file buffering keeps big PDFs out of the Java heap (PDF-06).
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())

    private fun PDPage.box(): PdfPageBox {
        val c = cropBox
        return PdfPageBox(
            c.lowerLeftX.toDouble(),
            c.lowerLeftY.toDouble(),
            c.upperRightX.toDouble(),
            c.upperRightY.toDouble(),
            rotation,
        )
    }

    private companion object {
        const val POINTS_PER_UNIT = 0.75f
        const val PX_PER_UNIT = 3f
        const val MAX_IMAGE_PX = 3000
        const val JPEG_QUALITY = 0.88f
        const val ROUND = 1
        const val PROJECTING_SQUARE = 2
    }
}
