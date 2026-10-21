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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withScale
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.feature.ink.InkPaints
import com.appname.notes.feature.ink.InkPaths

/** What a library card needs to draw a handwritten note's first page. */
class InkThumbnail(
    val width: Float,
    val height: Float,
    val template: PageTemplate,
    val items: List<InkItem>,
    /** For PDF notes: the PDF page under the ink, drawn over the whole page. */
    val pdfPage: Bitmap? = null,
) {
    /** Shows the page from the top down to just below its ink (at least a third of the page); a PDF page whole. */
    val shownHeight: Float = if (pdfPage != null) height else run {
        val bottom = items.maxOfOrNull { it.bounds().bottom.toFloat() } ?: 0f
        (bottom + 24f).coerceIn(height / 3, height)
    }
}

/** First-page preview for a handwritten note card. Reloads when the note changes. */
@Composable
fun InkThumbnailView(note: NoteSummary, load: suspend (NoteSummary) -> InkThumbnail?, modifier: Modifier = Modifier) {
    val thumb by produceState<InkThumbnail?>(initialValue = null, note.id, note.updatedAt) { value = load(note) }
    val t = thumb
    val ratio = if (t == null) 1.6f else (t.width / t.shownHeight).coerceIn(0.6f, 2.5f)
    val outline = MaterialTheme.colorScheme.outlineVariant
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val pageRect = remember { RectF() }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, outline, MaterialTheme.shapes.small)
            .testTag("ink_thumbnail_${note.id.value}"),
    ) {
        drawRect(androidx.compose.ui.graphics.Color(InkPaints.PAPER))
        val thumbnail = t ?: return@Canvas
        val scale = size.width / thumbnail.width
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            canvas.withScale(scale, scale) {
                clipRect(0f, 0f, thumbnail.width, thumbnail.shownHeight)
                thumbnail.pdfPage?.let { pageRect.set(0f, 0f, thumbnail.width, thumbnail.height); drawBitmap(it, null, pageRect, bitmapPaint) }
                for (pass in 0..1) {
                    for (item in thumbnail.items) {
                        if ((pass == 0) != (item.brush.tool == InkTool.HIGHLIGHTER)) continue
                        paint.set(InkPaints.inkPaint(item.brush))
                        InkPaths.drawItem(this, item, paint)
                    }
                }
            }
        }
    }
}
