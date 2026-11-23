package com.appname.notes.feature.ink

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.withClip
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.model.PaintOrder
import com.appname.notes.core.ink.template.TemplateLayout
import com.appname.notes.feature.templates.TemplateRenderer
import java.io.OutputStream

/** One page as it is exported: its own size and design, and its ink. */
data class ExportPage(
    val width: Float,
    val height: Float,
    val template: PageTemplate,
    val layout: TemplateLayout?,
    val items: List<InkItem>,
)

/**
 * Exports a handwritten note as a vector PDF (owner brief §5: page settings apply to export too). Each page
 * keeps its size (A4 portrait/landscape, long), paper colour, background or template, and its ink, in the
 * real colours (dark paper is display only). 1 page unit = 1/96 inch = 0.75 PDF points.
 */
object InkPdfWriter {

    private const val POINTS_PER_UNIT = 72f / 96f

    fun write(pages: List<ExportPage>, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val renderer = TemplateRenderer()
            val paper = Paint()
            val ink = Paint(Paint.ANTI_ALIAS_FLAG)
            val styled = StyledInk(cache = false)
            pages.forEachIndexed { i, p ->
                val w = (p.width * POINTS_PER_UNIT).toInt().coerceAtLeast(1)
                val h = (p.height * POINTS_PER_UNIT).toInt().coerceAtLeast(1)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
                val canvas = page.canvas
                canvas.scale(POINTS_PER_UNIT, POINTS_PER_UNIT)
                val design = p.layout?.resized(p.width, p.height) ?: TemplateLayout.basic(p.template, p.width, p.height)
                paper.color = design.paperColor
                canvas.drawRect(0f, 0f, p.width, p.height, paper)
                canvas.withClip(0f, 0f, p.width, p.height) {
                    renderer.drawDesign(this, design)
                    // Highlighter under everything else, as on screen.
                    val ordered = p.items.sortedWith(PaintOrder)
                    for (pass in com.appname.notes.feature.ink.InkLayers.PASSES) {
                        for (item in ordered) {
                            if (com.appname.notes.feature.ink.InkLayers.of(item) != pass) continue
                            ink.set(InkPaints.inkPaint(item.brush))
                            InkPaths.drawItem(this, item, ink, styled)
                        }
                    }
                }
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}
