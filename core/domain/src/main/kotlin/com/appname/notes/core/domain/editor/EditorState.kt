package com.appname.notes.core.domain.editor

import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.SpanType
import com.appname.notes.core.domain.document.StyleSpan
import com.appname.notes.core.domain.document.TextBlock

/** A cursor or selection in the editor text. [start] may be greater than [end] (backwards selection). */
data class Selection(val start: Int, val end: Int = start) {
    val min: Int get() = minOf(start, end)
    val max: Int get() = maxOf(start, end)
    val collapsed: Boolean get() = start == end

    fun coerceIn(length: Int) = Selection(start.coerceIn(0, length), end.coerceIn(0, length))
}

/** Per-line block information; one entry per line of [EditorState.text]. */
data class ParagraphMeta(
    val id: String,
    val kind: BlockKind = BlockKind.PARAGRAPH,
    val checked: Boolean = false,
)

/** Styles toggled while the cursor is collapsed; they apply to the next typed text at [at]. */
data class PendingStyles(val at: Int, val add: Set<SpanType> = emptySet(), val remove: Set<SpanType> = emptySet())

/**
 * The whole editable note body as one string (lines = blocks) plus structure.
 *
 * Immutable: every edit returns a new state, so undo is just keeping old states.
 * Invariants (checked by [isValid]): `paragraphs.size == lines`, spans are inside the text, non-empty,
 * and merged (no two same-style spans touch or overlap).
 */
data class EditorState(
    val text: String,
    val paragraphs: List<ParagraphMeta>,
    val spans: List<StyleSpan> = emptyList(),
    val selection: Selection = Selection(0),
    /** IME composing region (the underlined word while typing); passed through untouched. */
    val composition: Selection? = null,
    val pendingStyles: PendingStyles? = null,
) {
    /** Start offset of every line. */
    val lineStarts: IntArray by lazy(LazyThreadSafetyMode.NONE) {
        val starts = ArrayList<Int>(paragraphs.size)
        starts += 0
        text.forEachIndexed { index, c -> if (c == '\n') starts += index + 1 }
        starts.toIntArray()
    }

    val lineCount: Int get() = lineStarts.size

    fun lineOf(offset: Int): Int {
        val starts = lineStarts
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun lineStart(line: Int): Int = lineStarts[line]

    fun lineEnd(line: Int): Int = if (line + 1 < lineStarts.size) lineStarts[line + 1] - 1 else text.length

    fun lineText(line: Int): String = text.substring(lineStart(line), lineEnd(line))

    fun isValid(): Boolean =
        paragraphs.size == lineCount &&
            spans.all { it.start >= 0 && it.end <= text.length && it.start < it.end } &&
            spans == normalizeSpans(spans)

    fun toDocument(): NoteDocument = NoteDocument(
        blocks = paragraphs.mapIndexed { line, meta ->
            val start = lineStart(line)
            val end = lineEnd(line)
            val lineSpans = spans.mapNotNull { span ->
                val s = maxOf(span.start, start)
                val e = minOf(span.end, end)
                if (s < e) span.copy(start = s - start, end = e - start) else null
            }
            TextBlock(
                id = meta.id,
                kind = meta.kind,
                text = RichText(text.substring(start, end), lineSpans),
                checked = meta.kind == BlockKind.CHECKLIST && meta.checked,
            )
        },
    )

    companion object {
        /** Builds editor state from the text blocks of [document]. Use only when it's fully editable. */
        fun fromDocument(document: NoteDocument, newId: () -> String): EditorState {
            val metas = ArrayList<ParagraphMeta>()
            val spans = ArrayList<StyleSpan>()
            val builder = StringBuilder()
            for (block in document.textBlocks) {
                // Defensive: split any block that contains newlines so one block == one line.
                val pieces = block.text.text.split('\n')
                var pieceOffset = 0
                pieces.forEachIndexed { i, piece ->
                    if (metas.isNotEmpty()) builder.append('\n')
                    val lineStart = builder.length
                    builder.append(piece)
                    metas += ParagraphMeta(
                        id = if (i == 0) block.id else newId(),
                        kind = block.kind,
                        checked = block.checked,
                    )
                    for (span in block.text.spans) {
                        val s = maxOf(span.start, pieceOffset)
                        val e = minOf(span.end, pieceOffset + piece.length)
                        if (s < e) spans += span.copy(start = lineStart + s - pieceOffset, end = lineStart + e - pieceOffset)
                    }
                    pieceOffset += piece.length + 1
                }
            }
            if (metas.isEmpty()) metas += ParagraphMeta(newId())
            return EditorState(text = builder.toString(), paragraphs = metas, spans = normalizeSpans(spans))
        }
    }
}

/** Sorts spans, drops empty ones and merges touching/overlapping spans of the same style. */
fun normalizeSpans(spans: List<StyleSpan>): List<StyleSpan> {
    val result = ArrayList<StyleSpan>(spans.size)
    spans.filter { it.start < it.end }
        .groupBy { it.style to it.url }
        .values
        .forEach { group ->
            val sorted = group.sortedBy { it.start }
            var current = sorted.first()
            for (span in sorted.drop(1)) {
                current = if (span.start <= current.end) {
                    current.copy(end = maxOf(current.end, span.end))
                } else {
                    result += current
                    span
                }
            }
            result += current
        }
    return result.sortedWith(compareBy<StyleSpan>({ it.start }, { it.end }, { it.style.ordinal }, { it.url }))
}
