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
    fun writeAnnotatedCopy(source: File, ink: Map<Int, List<InkItem>>, out: OutputStream) {
        open(source).use { doc ->
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true // the copy is the user's own
            val alphaStates = HashMap<Float, PDExtendedGraphicsState>()
            for ((index, items) in ink) {
                if (items.isEmpty() || index !in 0 until doc.numberOfPages) continue
                val page = doc.getPage(index)
                val box = page.box()
                // APPEND + resetContext: the page's own drawing is wrapped in q…Q, so its state can't leak into ours.
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    val m = PdfPageMapping.unitsToUser(box)
                    cs.transform(Matrix(m[0].toFloat(), m[1].toFloat(), m[2].toFloat(), m[3].toFloat(), m[4].toFloat(), m[5].toFloat()))
                    cs.setLineJoinStyle(ROUND)
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
                }
            }
            doc.save(out)
        }
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
        const val ROUND = 1
        const val PROJECTING_SQUARE = 2
    }
}
