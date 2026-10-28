package com.appname.notes.ui.common

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withScale
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.core.ink.model.ImageItem
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.template.TemplateLayout
import com.appname.notes.feature.ink.InkImages
import com.appname.notes.feature.ink.InkPaints
import com.appname.notes.feature.ink.InkPaths
import com.appname.notes.feature.templates.TemplateRenderer

/** What a library card needs to draw a handwritten note's first page. */
class InkThumbnail(
    val width: Float,
    val height: Float,
    val template: PageTemplate,
    val items: List<InkItem>,
    /** For PDF notes: the PDF page under the ink, drawn over the whole page. */
    val pdfPage: Bitmap? = null,
    /** The page's template design (owner brief §4), if it was made from one. */
    val layout: TemplateLayout? = null,
) {
    /** What is printed on the paper: the template design, or the plain background as a design. Null for PDF pages. */
    val design: TemplateLayout? = if (pdfPage != null) null else layout?.resized(width, height) ?: TemplateLayout.basic(template, width, height)

    /**
     * Shows the page from the top down to just below its ink (at least a third of the page, half for a template
     * so its design is recognisable); a PDF page whole.
     */
    val shownHeight: Float = if (pdfPage != null) height else run {
        val bottom = items.maxOfOrNull { it.bounds().bottom.toFloat() } ?: 0f
        (bottom + 24f).coerceIn(if (layout != null) height / 2 else height / 3, height)
    }
}

/** First-page preview for a handwritten note card. Reloads when the note changes or async page photos finish decoding. */
@Composable
fun InkThumbnailView(note: NoteSummary, load: suspend (NoteSummary) -> InkThumbnail?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember(context) { InkImages.init(context); true }
    var imageTick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val listener: () -> Unit = { imageTick++ }
        InkImages.addListener(listener)
        onDispose { InkImages.removeListener(listener) }
    }
    val thumb by produceState<InkThumbnail?>(initialValue = null, note.id, note.updatedAt, note.attachmentCount) {
        value = load(note)
    }
    val t = thumb
    val ratio = if (t == null) 1.6f else (t.width / t.shownHeight).coerceIn(0.6f, 2.5f)
    val outline = MaterialTheme.colorScheme.outlineVariant
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
    val templateRenderer = remember { TemplateRenderer() }
    val styled = remember { com.appname.notes.feature.ink.StyledInk() }
    val darkPaper = com.appname.notes.feature.ink.LocalDarkPaper.current
    val darkLayer = remember { com.appname.notes.feature.ink.DarkPaper.layerPaint() }
    val pageRect = remember { RectF() }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, outline, MaterialTheme.shapes.small)
            .testTag("ink_thumbnail_${note.id.value}"),
    ) {
        // Read imageTick so the thumbnail canvas redraws whenever an async photo finishes decoding.
        @Suppress("UNUSED_EXPRESSION")
        imageTick
        val thumbnail = t
        val paper = thumbnail?.design?.paperColor ?: InkPaints.PAPER
        drawRect(androidx.compose.ui.graphics.Color(if (darkPaper) com.appname.notes.feature.ink.DarkPaper.map(paper) else paper))
        if (thumbnail == null) return@Canvas
        val scale = size.width / thumbnail.width
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            // 1. Paper template and PDF page: mapped through DarkPaper in dark mode.
            val pageLayer = if (darkPaper) canvas.saveLayer(0f, 0f, size.width, size.height, darkLayer) else -1
            canvas.withScale(scale, scale) {
                clipRect(0f, 0f, thumbnail.width, thumbnail.shownHeight)
                thumbnail.design?.let { templateRenderer.drawDesign(this, it, pxPerUnit = scale) }
                thumbnail.pdfPage?.let { pageRect.set(0f, 0f, thumbnail.width, thumbnail.height); drawBitmap(it, null, pageRect, bitmapPaint) }
            }
            if (pageLayer >= 0) canvas.restoreToCount(pageLayer)

            // 2. Embedded photos (pass 0): drawn outside DarkPaper's colour filter so their true colours are preserved.
            val images = thumbnail.items.filterIsInstance<ImageItem>()
            if (images.isNotEmpty()) {
                canvas.withScale(scale, scale) {
                    clipRect(0f, 0f, thumbnail.width, thumbnail.shownHeight)
                    for (item in images) {
                        InkPaths.drawItem(this, item, paint, styled, syncImages = false)
                    }
                }
            }

            // 3. Ink strokes, highlighters, shapes, text and audio chips: mapped through DarkPaper in dark mode.
            val nonImages = thumbnail.items.filterNot { it is ImageItem }
            if (nonImages.isNotEmpty()) {
                val inkLayer = if (darkPaper) canvas.saveLayer(0f, 0f, size.width, size.height, darkLayer) else -1
                canvas.withScale(scale, scale) {
                    clipRect(0f, 0f, thumbnail.width, thumbnail.shownHeight)
                    for (pass in com.appname.notes.feature.ink.InkLayers.PASSES) {
                        for (item in nonImages) {
                            if (com.appname.notes.feature.ink.InkLayers.of(item) != pass) continue
                            paint.set(InkPaints.inkPaint(item.brush))
                            InkPaths.drawItem(this, item, paint, styled, syncImages = false)
                        }
                    }
                }
                if (inkLayer >= 0) canvas.restoreToCount(inkLayer)
            }
        }
    }
}
