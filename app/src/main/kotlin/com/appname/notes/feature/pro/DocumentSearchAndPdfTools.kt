package com.appname.notes.feature.pro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.feature.textindex.TextRecognizerEngine
import java.io.OutputStream
import kotlin.math.ceil

/**
 * Compact floating "Find in Note / Find in PDF" search bar with live match count and Prev/Next navigation.
 */
@Composable
fun InDocumentFindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentMatchIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 6.dp,
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("in_doc_find_bar"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (matchCount > 0) onNext() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag("in_doc_find_field"),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.find_in_note_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        )
                    }
                    inner()
                },
            )
            if (query.isNotBlank()) {
                Text(
                    text = if (matchCount > 0) "${currentMatchIndex + 1} / $matchCount" else "0 / 0",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (matchCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                IconButton(
                    onClick = onPrevious,
                    enabled = matchCount > 1,
                    modifier = Modifier.size(34.dp).testTag("in_doc_find_prev"),
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.find_prev_match), modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onNext,
                    enabled = matchCount > 1,
                    modifier = Modifier.size(34.dp).testTag("in_doc_find_next"),
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.find_next_match), modifier = Modifier.size(18.dp))
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(34.dp).testTag("in_doc_find_close"),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Renders selected handwriting [strokes] onto a high-contrast white bitmap and runs on-device ML Kit OCR
 * ([TextRecognizerEngine]) to convert handwritten ink into editable text.
 */
object HandwritingOcrConverter {
    fun recognizeStrokes(strokes: List<StrokeItem>, recognizer: TextRecognizerEngine): String? {
        if (strokes.isEmpty()) return null
        val box = strokes.map { it.bounds() }.reduce { a, b -> a.union(b) }
        val pad = 28f
        val rawW = (box.width.toFloat() + pad * 2).coerceAtLeast(64f)
        val rawH = (box.height.toFloat() + pad * 2).coerceAtLeast(48f)
        // Scale to a crisp OCR resolution (~2.2x page units, capped at 2048px)
        val scale = (1200f / maxOf(rawW, rawH)).coerceIn(1.2f, 2.8f)
        val bmpW = ceil(rawW * scale).toInt().coerceIn(64, 2048)
        val bmpH = ceil(rawH * scale).toInt().coerceIn(48, 2048)
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(AndroidColor.WHITE)
            canvas.save()
            canvas.scale(scale, scale)
            canvas.translate(-box.left.toFloat() + pad, -box.top.toFloat() + pad)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = AndroidColor.BLACK
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            for (s in strokes.sortedBy { it.z }) {
                paint.strokeWidth = s.brush.size.coerceIn(2.0f, 6.0f)
                val pts = s.points
                if (pts.size == 1) {
                    canvas.drawPoint(pts.x[0], pts.y[0], paint)
                } else if (pts.size > 1) {
                    path.reset()
                    path.moveTo(pts.x[0], pts.y[0])
                    for (i in 1 until pts.size) {
                        val prevX = pts.x[i - 1]
                        val prevY = pts.y[i - 1]
                        val curX = pts.x[i]
                        val curY = pts.y[i]
                        path.quadTo(prevX, prevY, (prevX + curX) * 0.5f, (prevY + curY) * 0.5f)
                    }
                    path.lineTo(pts.x[pts.size - 1], pts.y[pts.size - 1])
                    canvas.drawPath(path, paint)
                }
            }
            canvas.restore()
            runCatching { recognizer.recognize(bitmap).trim() }.getOrNull()?.takeIf { it.isNotBlank() }
        } finally {
            bitmap.recycle()
        }
    }
}

/**
 * Exports a Typed Note ([NoteDocument]) as a cleanly formatted, multi-page A4 PDF document.
 */
object TypedNotePdfExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val CONTENT_WIDTH = (PAGE_WIDTH - MARGIN * 2).toInt()

    fun export(title: String, document: NoteDocument, openStream: () -> OutputStream?): Boolean = runCatching {
        val pdf = PdfDocument()
        try {
            var pageNumber = 1
            var currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            var canvas = currentPage.canvas
            var y = MARGIN

            fun newPageIfNeeded(neededHeight: Float) {
                if (y + neededHeight > PAGE_HEIGHT - MARGIN) {
                    pdf.finishPage(currentPage)
                    pageNumber++
                    currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                    canvas = currentPage.canvas
                    y = MARGIN
                }
            }

            fun drawParagraph(text: String, sizePt: Float, bold: Boolean = false, italic: Boolean = false, color: Int = 0xFF1F1F1F.toInt(), indent: Float = 0f, spaceAfter: Float = 10f) {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = sizePt
                    this.color = color
                    typeface = Typeface.create(
                        Typeface.SANS_SERIF,
                        when {
                            bold && italic -> Typeface.BOLD_ITALIC
                            bold -> Typeface.BOLD
                            italic -> Typeface.ITALIC
                            else -> Typeface.NORMAL
                        },
                    )
                }
                val availW = (CONTENT_WIDTH - indent.toInt()).coerceAtLeast(100)
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, availW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1.15f)
                    .build()
                val h = layout.height.toFloat()
                newPageIfNeeded(h + spaceAfter)
                canvas.save()
                canvas.translate(MARGIN + indent, y)
                layout.draw(canvas)
                canvas.restore()
                y += h + spaceAfter
            }

            if (title.isNotBlank()) {
                drawParagraph(title.trim(), sizePt = 22f, bold = true, spaceAfter = 16f)
            }

            var numberIndex = 0
            for (block in document.blocks) {
                when (block) {
                    is TextBlock -> {
                        if (block.kind != BlockKind.NUMBERED) numberIndex = 0
                        val raw = block.text.text
                        when (block.kind) {
                            BlockKind.HEADING1 -> drawParagraph(raw, sizePt = 18f, bold = true, spaceAfter = 10f)
                            BlockKind.HEADING2 -> drawParagraph(raw, sizePt = 15f, bold = true, spaceAfter = 8f)
                            BlockKind.HEADING3 -> drawParagraph(raw, sizePt = 13f, bold = true, spaceAfter = 6f)
                            BlockKind.BULLET -> drawParagraph("•  $raw", sizePt = 11.5f, indent = 10f, spaceAfter = 6f)
                            BlockKind.NUMBERED -> {
                                numberIndex++
                                drawParagraph("$numberIndex.  $raw", sizePt = 11.5f, indent = 10f, spaceAfter = 6f)
                            }
                            BlockKind.CHECKLIST -> {
                                val box = if (block.checked) "☑" else "☐"
                                val col = if (block.checked) 0xFF5F6368.toInt() else 0xFF1F1F1F.toInt()
                                drawParagraph("$box  $raw", sizePt = 11.5f, color = col, indent = 8f, spaceAfter = 6f)
                            }
                            BlockKind.QUOTE -> drawParagraph("“$raw”", sizePt = 11.5f, italic = true, color = 0xFF475569.toInt(), indent = 14f, spaceAfter = 8f)
                            BlockKind.TABLE_ROW -> {
                                val line = raw.split('\t').joinToString("  |  ")
                                drawParagraph(line, sizePt = 10.5f, indent = 6f, spaceAfter = 5f)
                            }
                            BlockKind.PARAGRAPH -> drawParagraph(raw, sizePt = 11.5f, spaceAfter = 8f)
                        }
                    }
                    else -> Unit
                }
            }

            pdf.finishPage(currentPage)
            val stream = openStream() ?: return@runCatching false
            stream.use { pdf.writeTo(it) }
            true
        } finally {
            pdf.close()
        }
    }.getOrDefault(false)
}
