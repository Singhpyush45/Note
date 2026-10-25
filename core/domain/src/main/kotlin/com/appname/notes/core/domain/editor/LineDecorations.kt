package com.appname.notes.core.domain.editor

import com.appname.notes.core.domain.document.BlockKind

/**
 * Visual line prefixes ("• ", "3. ", "☐ ") that are *drawn* but are not part of the editable text,
 * plus the offset arithmetic between the stored text and the drawn text.
 *
 * Table rows (8c) are drawn as an aligned grid: "│ cell   │ cell │" — each cell padded to its column's width, the
 * tab between cells shown as a bar. Only the padding and bars are extra; the cells' text is the stored text.
 *
 * Compose's VisualTransformation needs exactly this mapping; it lives here so it can be unit-tested,
 * because a wrong mapping crashes the text field.
 */
class LineDecorations private constructor(
    /** Prefix shown before each line ("" for none). */
    val prefixes: List<String>,
    private val originalStarts: IntArray,
    private val lineLengths: IntArray,
    private val transformedStarts: IntArray,
    /** Per line: where each original column (0..length) is in the drawn line after its prefix; null = same place. */
    private val columnMaps: Array<IntArray?>,
    /** Per line: the drawn text after the prefix when it differs from the stored text (table rows). */
    private val drawn: Array<String?>,
    /** Per line: the row is the first of its table (drawn as a header). */
    private val headerRows: BooleanArray,
    val originalLength: Int,
    val transformedLength: Int,
) {
    fun originalToTransformed(offset: Int): Int {
        val o = offset.coerceIn(0, originalLength)
        val line = findLine(originalStarts, o)
        val column = o - originalStarts[line]
        return transformedStarts[line] + prefixes[line].length + (columnMaps[line]?.get(column) ?: column)
    }

    fun transformedToOriginal(offset: Int): Int {
        val t = offset.coerceIn(0, transformedLength)
        val line = findLine(transformedStarts, t)
        val rel = t - transformedStarts[line] - prefixes[line].length
        val map = columnMaps[line]
        val column = if (map == null) {
            rel.coerceIn(0, lineLengths[line])
        } else {
            // The last stored column drawn at or before this point (padding and bars belong to the cell before).
            var lo = 0
            var hi = map.size - 1
            if (rel <= map[0]) return originalStarts[line]
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (map[mid] <= rel) lo = mid else hi = mid - 1
            }
            lo
        }
        return originalStarts[line] + column
    }

    /** Transformed range `[start, end)` of the prefix of [line] — e.g. where a checkbox is drawn. */
    fun prefixRange(line: Int): IntRange = transformedStarts[line] until transformedStarts[line] + prefixes[line].length

    /** Transformed start of [line]'s own text (after its prefix). */
    fun contentStart(line: Int): Int = transformedStarts[line] + prefixes[line].length

    /** Transformed end of [line]'s drawn text. */
    fun contentEnd(line: Int): Int = contentStart(line) + (drawn[line]?.length ?: lineLengths[line])

    /** The drawn text of [line] after its prefix, or null when it is the stored text itself. */
    fun drawnContent(line: Int): String? = drawn[line]

    /** Where stored [column] of [line] is drawn, relative to the line's content start. */
    fun drawnColumn(line: Int, column: Int): Int = columnMaps[line]?.get(column.coerceIn(0, lineLengths[line])) ?: column

    /** Whether [line] is the first row of a table. */
    fun isHeaderRow(line: Int): Boolean = headerRows[line]

    /** Line whose prefix contains the transformed offset [t], or null. */
    fun lineWithPrefixAt(t: Int): Int? {
        val line = findLine(transformedStarts, t.coerceIn(0, transformedLength))
        return line.takeIf { prefixes[line].isNotEmpty() && t in prefixRange(line) }
    }

    val lineCount: Int get() = prefixes.size

    companion object {
        const val BULLET = "•  "
        const val UNCHECKED = "☐  "
        const val CHECKED = "☑  "
        const val QUOTE = "┃  "
        const val TABLE = "│ "

        /** The narrowest a table column is drawn (characters). */
        private const val MIN_CELL = 3

        fun of(state: EditorState): LineDecorations {
            val count = state.lineCount
            val prefixes = ArrayList<String>(count)
            var number = 0
            for (meta in state.paragraphs) {
                number = if (meta.kind == BlockKind.NUMBERED) number + 1 else 0
                prefixes += when (meta.kind) {
                    BlockKind.BULLET -> BULLET
                    BlockKind.NUMBERED -> "$number.  "
                    BlockKind.CHECKLIST -> if (meta.checked) CHECKED else UNCHECKED
                    BlockKind.QUOTE -> QUOTE
                    BlockKind.TABLE_ROW -> TABLE
                    else -> ""
                }
            }
            val originalStarts = state.lineStarts
            val lineLengths = IntArray(count) { state.lineEnd(it) - originalStarts[it] }
            val maps = arrayOfNulls<IntArray>(count)
            val drawn = arrayOfNulls<String>(count)
            val headers = BooleanArray(count)
            // Tables: runs of table rows, laid out together.
            var i = 0
            while (i < count) {
                if (state.paragraphs[i].kind != BlockKind.TABLE_ROW) {
                    i++
                    continue
                }
                var j = i
                while (j + 1 < count && state.paragraphs[j + 1].kind == BlockKind.TABLE_ROW) j++
                val rows = (i..j).map { state.lineText(it).split('\t') }
                val columns = rows.maxOf { it.size }
                val widths = IntArray(columns) { c -> maxOf(MIN_CELL, rows.maxOf { r -> r.getOrNull(c)?.length ?: 0 }) }
                for ((k, cells) in rows.withIndex()) {
                    val line = i + k
                    val (text, map) = layoutRow(cells, widths)
                    drawn[line] = text
                    maps[line] = map
                    headers[line] = k == 0 && i != j
                }
                i = j + 1
            }
            val transformedStarts = IntArray(count)
            var t = 0
            for (l in 0 until count) {
                transformedStarts[l] = t
                t += prefixes[l].length + (drawn[l]?.length ?: lineLengths[l]) + if (l < count - 1) 1 else 0
            }
            return LineDecorations(prefixes, originalStarts, lineLengths, transformedStarts, maps, drawn, headers, state.text.length, t)
        }

        /** One table row drawn: each cell padded to its column's width, " │ " between cells, " │" at the end. */
        private fun layoutRow(cells: List<String>, widths: IntArray): Pair<String, IntArray> {
            val length = cells.sumOf { it.length } + cells.size - 1
            val map = IntArray(length + 1)
            val out = StringBuilder()
            var o = 0
            for (c in widths.indices) {
                val real = c < cells.size
                if (c > 0) {
                    out.append(" │ ")
                    if (real) o++ // the tab before this cell (its place is the end of the cell before)
                }
                val cell = if (real) cells[c] else ""
                for (ch in cell) {
                    map[o] = out.length
                    out.append(ch)
                    o++
                }
                if (real) map[o] = out.length
                repeat(widths[c] - cell.length) { out.append(' ') }
            }
            out.append(" │")
            return out.toString() to map
        }

        /** The displayed text: each line's prefix followed by its (drawn) text. */
        fun displayText(state: EditorState, decorations: LineDecorations): String = buildString {
            for (i in 0 until state.lineCount) {
                if (i > 0) append('\n')
                append(decorations.prefixes[i])
                append(decorations.drawn[i] ?: state.lineText(i))
            }
        }

        private fun findLine(starts: IntArray, offset: Int): Int {
            var lo = 0
            var hi = starts.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (starts[mid] <= offset) lo = mid else hi = mid - 1
            }
            return lo
        }
    }
}
