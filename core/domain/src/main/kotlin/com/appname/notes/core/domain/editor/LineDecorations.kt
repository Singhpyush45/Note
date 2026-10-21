package com.appname.notes.core.domain.editor

import com.appname.notes.core.domain.document.BlockKind

/**
 * Visual line prefixes ("• ", "3. ", "☐ ") that are *drawn* but are not part of the editable text,
 * plus the offset arithmetic between the stored text and the drawn text.
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
    val originalLength: Int,
    val transformedLength: Int,
) {
    fun originalToTransformed(offset: Int): Int {
        val o = offset.coerceIn(0, originalLength)
        val line = findLine(originalStarts, o)
        return transformedStarts[line] + prefixes[line].length + (o - originalStarts[line])
    }

    fun transformedToOriginal(offset: Int): Int {
        val t = offset.coerceIn(0, transformedLength)
        val line = findLine(transformedStarts, t)
        val column = (t - transformedStarts[line] - prefixes[line].length).coerceIn(0, lineLengths[line])
        return originalStarts[line] + column
    }

    /** Transformed range `[start, end)` of the prefix of [line] — e.g. where a checkbox is drawn. */
    fun prefixRange(line: Int): IntRange = transformedStarts[line] until transformedStarts[line] + prefixes[line].length

    /** Transformed start of [line]'s own text (after its prefix). */
    fun contentStart(line: Int): Int = transformedStarts[line] + prefixes[line].length

    /** Transformed end of [line]'s text. */
    fun contentEnd(line: Int): Int = contentStart(line) + lineLengths[line]

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
                    else -> ""
                }
            }
            val originalStarts = state.lineStarts
            val lineLengths = IntArray(count) { state.lineEnd(it) - originalStarts[it] }
            val transformedStarts = IntArray(count)
            var t = 0
            for (i in 0 until count) {
                transformedStarts[i] = t
                t += prefixes[i].length + lineLengths[i] + if (i < count - 1) 1 else 0
            }
            return LineDecorations(prefixes, originalStarts, lineLengths, transformedStarts, state.text.length, t)
        }

        /** The displayed text: each line's prefix followed by its text. */
        fun displayText(state: EditorState, decorations: LineDecorations): String = buildString {
            for (i in 0 until state.lineCount) {
                if (i > 0) append('\n')
                append(decorations.prefixes[i])
                append(state.lineText(i))
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
