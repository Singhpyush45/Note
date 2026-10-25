package com.appname.notes.feature.editor

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.SpanType
import com.appname.notes.core.domain.editor.EditorState
import com.appname.notes.core.domain.editor.LineDecorations

/** Colours and sizes used to draw the note body. Built from the current Material theme. */
@Immutable
data class EditorStyles(
    val prefix: SpanStyle,
    val checkbox: SpanStyle,
    val heading1: SpanStyle,
    val heading2: SpanStyle,
    val heading3: SpanStyle,
    val quote: SpanStyle,
    val checkedLine: SpanStyle,
    val link: SpanStyle,
    val code: SpanStyle,
    val table: SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace),
    val tableBorder: SpanStyle = SpanStyle(),
) {
    companion object {
        @Composable
        fun fromTheme(colors: ColorScheme = MaterialTheme.colorScheme): EditorStyles = EditorStyles(
            prefix = SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold),
            checkbox = SpanStyle(color = colors.primary, fontSize = 20.sp),
            heading1 = SpanStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
            heading2 = SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            heading3 = SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
            quote = SpanStyle(fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant),
            checkedLine = SpanStyle(color = colors.onSurfaceVariant, textDecoration = TextDecoration.LineThrough),
            link = SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline),
            code = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceContainerHigh),
            table = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceContainerLow),
            tableBorder = SpanStyle(color = colors.outline),
        )
    }
}

/**
 * Draws list markers and styles without putting them in the editable text (ADR-0007).
 * The offset arithmetic is the unit-tested [LineDecorations].
 */
class EditorVisualTransformation(
    private val state: EditorState,
    private val styles: EditorStyles,
) : VisualTransformation {

    val decorations: LineDecorations = LineDecorations.of(state)

    override fun filter(text: AnnotatedString): TransformedText {
        // Safety net: if the field shows text we don't know about, draw it plainly rather than crash.
        if (text.text != state.text) return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(
            buildDisplayText(state, decorations, styles),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = decorations.originalToTransformed(offset)
                override fun transformedToOriginal(offset: Int) = decorations.transformedToOriginal(offset)
            },
        )
    }

    override fun equals(other: Any?): Boolean =
        other is EditorVisualTransformation && other.state == state && other.styles == styles

    override fun hashCode(): Int = 31 * state.hashCode() + styles.hashCode()
}

internal fun buildDisplayText(state: EditorState, decorations: LineDecorations, styles: EditorStyles): AnnotatedString =
    buildAnnotatedString {
        for (line in 0 until state.lineCount) {
            if (line > 0) append('\n')
            val meta = state.paragraphs[line]
            val prefix = decorations.prefixes[line]
            if (prefix.isNotEmpty()) {
                val prefixStyle = if (meta.kind == BlockKind.CHECKLIST) styles.checkbox else styles.prefix
                pushStyle(prefixStyle)
                append(prefix)
                pop()
            }
            val lineStyle: SpanStyle? = when {
                meta.kind == BlockKind.HEADING1 -> styles.heading1
                meta.kind == BlockKind.HEADING2 -> styles.heading2
                meta.kind == BlockKind.HEADING3 -> styles.heading3
                meta.kind == BlockKind.QUOTE -> styles.quote
                meta.kind == BlockKind.CHECKLIST && meta.checked -> styles.checkedLine
                else -> null
            }
            val start = state.lineStart(line)
            val end = state.lineEnd(line)
            val contentStart = length
            val drawn = decorations.drawnContent(line)
            if (drawn != null) {
                // A table row: aligned cells in a fixed-width font; the bars in the outline colour; the first row bold.
                append(drawn)
                addStyle(styles.table, contentStart - prefix.length, length)
                if (decorations.isHeaderRow(line)) addStyle(SpanStyle(fontWeight = FontWeight.Bold), contentStart, length)
                drawn.forEachIndexed { k, ch -> if (ch == '│') addStyle(styles.tableBorder, contentStart + k, contentStart + k + 1) }
                prefix.forEachIndexed { k, ch -> if (ch == '│') addStyle(styles.tableBorder, contentStart - prefix.length + k, contentStart - prefix.length + k + 1) }
                addInlineStyles(state, start, end, contentStart, styles, baseDecoration = null) { decorations.drawnColumn(line, it) }
                continue
            }
            append(state.text, start, end)
            if (lineStyle != null && end > start) addStyle(lineStyle, contentStart, length)
            addInlineStyles(state, start, end, contentStart, styles, baseDecoration = lineStyle?.textDecoration)
        }
    }

/**
 * Adds one merged style per run of characters, so overlapping underline + strikethrough
 * combine instead of one hiding the other.
 */
private fun AnnotatedString.Builder.addInlineStyles(
    state: EditorState,
    lineStart: Int,
    lineEnd: Int,
    outputStart: Int,
    styles: EditorStyles,
    baseDecoration: TextDecoration?,
    /** Where a stored column of the line is drawn (table rows are padded). */
    column: (Int) -> Int = { it },
) {
    val lineSpans = state.spans.filter { it.start < lineEnd && it.end > lineStart }
    if (lineSpans.isEmpty()) return
    val cuts = sortedSetOf(lineStart, lineEnd)
    lineSpans.forEach {
        cuts += it.start.coerceIn(lineStart, lineEnd)
        cuts += it.end.coerceIn(lineStart, lineEnd)
    }
    val points = cuts.toList()
    for (i in 0 until points.size - 1) {
        val from = points[i]
        val to = points[i + 1]
        val active = lineSpans.filter { it.start <= from && it.end >= to }.map { it.style }.toSet()
        if (active.isEmpty()) continue
        val decorations = buildList {
            baseDecoration?.let(::add)
            if (SpanType.UNDERLINE in active || SpanType.LINK in active) add(TextDecoration.Underline)
            if (SpanType.STRIKETHROUGH in active) add(TextDecoration.LineThrough)
        }
        val style = SpanStyle(
            fontWeight = if (SpanType.BOLD in active) FontWeight.Bold else null,
            fontStyle = if (SpanType.ITALIC in active) FontStyle.Italic else null,
            fontFamily = if (SpanType.CODE in active) styles.code.fontFamily else null,
            background = if (SpanType.CODE in active) styles.code.background else Color.Unspecified,
            color = if (SpanType.LINK in active) styles.link.color else Color.Unspecified,
            textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        )
        addStyle(style, outputStart + column(from - lineStart), outputStart + column(to - lineStart))
    }
}
