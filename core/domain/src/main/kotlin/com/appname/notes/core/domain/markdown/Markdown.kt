package com.appname.notes.core.domain.markdown

import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.SpanType
import com.appname.notes.core.domain.document.StyleSpan
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.core.domain.editor.normalizeSpans

/** Features a target format couldn't represent — shown to the user before exporting (FR-007). */
/** Things a Markdown file can't carry; the user is told before exporting (FR-007). */
enum class LostFeature { UNDERLINE, UNSUPPORTED_CONTENT, ATTACHMENTS }

data class ExportResult(val text: String, val lost: Set<LostFeature>)

/**
 * Markdown export/import for the subset the editor supports (ADR-0007).
 * Export → import of a note gives back the same blocks and styles, except underline.
 */
object Markdown {

    // ---------------------------------------------------------------- export

    fun export(title: String, document: NoteDocument): ExportResult {
        val lost = mutableSetOf<LostFeature>()
        if (!document.isFullyEditable) lost += LostFeature.UNSUPPORTED_CONTENT
        val out = StringBuilder()
        if (title.isNotBlank()) out.append("# ").append(escapeInline(title.trim())).append("\n\n")
        var number = 0
        var previousKind: BlockKind? = null
        for (block in document.textBlocks) {
            if (block.text.spans.any { it.style == SpanType.UNDERLINE }) lost += LostFeature.UNDERLINE
            number = if (block.kind == BlockKind.NUMBERED) number + 1 else 0
            // Lists stay tight; everything else is separated by a blank line so other apps keep paragraphs apart.
            if (previousKind != null && !(previousKind.isList && block.kind.isList)) out.append('\n')
            val body = inline(block.text)
            when (block.kind) {
                BlockKind.PARAGRAPH -> out.append(escapeLineStart(body))
                BlockKind.HEADING1 -> out.append("## ").append(body) // "#" is reserved for the note title
                BlockKind.HEADING2 -> out.append("### ").append(body)
                BlockKind.HEADING3 -> out.append("#### ").append(body)
                BlockKind.BULLET -> out.append("- ").append(body)
                BlockKind.NUMBERED -> out.append(number).append(". ").append(body)
                BlockKind.CHECKLIST -> out.append(if (block.checked) "- [x] " else "- [ ] ").append(body)
                BlockKind.QUOTE -> out.append("> ").append(body)
            }
            out.append('\n')
            previousKind = block.kind
        }
        return ExportResult(out.toString(), lost)
    }

    /** Plain text for sharing: list markers as characters, no Markdown syntax. */
    fun plainText(title: String, document: NoteDocument): String = buildString {
        if (title.isNotBlank()) append(title.trim()).append("\n\n")
        var number = 0
        for (block in document.textBlocks) {
            number = if (block.kind == BlockKind.NUMBERED) number + 1 else 0
            append(
                when (block.kind) {
                    BlockKind.BULLET -> "• "
                    BlockKind.NUMBERED -> "$number. "
                    BlockKind.CHECKLIST -> if (block.checked) "☑ " else "☐ "
                    BlockKind.QUOTE -> "“"
                    else -> ""
                },
            )
            append(block.text.text)
            if (block.kind == BlockKind.QUOTE) append("”")
            append('\n')
        }
    }.trimEnd() + "\n"

    private val markerOrder = listOf(SpanType.LINK, SpanType.BOLD, SpanType.ITALIC, SpanType.STRIKETHROUGH, SpanType.CODE)

    private fun open(span: StyleSpan) = when (span.style) {
        SpanType.LINK -> "["
        SpanType.BOLD -> "**"
        SpanType.ITALIC -> "*"
        SpanType.STRIKETHROUGH -> "~~"
        SpanType.CODE -> "`"
        SpanType.UNDERLINE -> ""
    }

    private fun close(span: StyleSpan) = when (span.style) {
        SpanType.LINK -> "](${span.url.orEmpty().replace(")", "%29").replace(" ", "%20")})"
        else -> open(span)
    }

    /** Writes text with markers; overlapping styles are closed and reopened so the output stays well-formed. */
    private fun inline(rich: RichText): String {
        val text = rich.text
        val spans = rich.spans.filter { it.style != SpanType.UNDERLINE && it.start < it.end && it.end <= text.length }
        if (spans.isEmpty()) return escapeInline(text)
        val boundaries = (spans.flatMap { listOf(it.start, it.end) } + 0 + text.length).toSortedSet().toList()
        val out = StringBuilder()
        var openStack = emptyList<StyleSpan>()
        for (i in 0 until boundaries.size - 1) {
            val from = boundaries[i]
            val to = boundaries[i + 1]
            val active = spans.filter { it.start <= from && it.end >= to }
                .sortedBy { markerOrder.indexOf(it.style) }
            // Keep the longest common prefix of what's open, close the rest, open the new ones.
            var keep = 0
            while (keep < openStack.size && keep < active.size && openStack[keep] == active[keep]) keep++
            for (span in openStack.drop(keep).asReversed()) out.append(close(span))
            for (span in active.drop(keep)) out.append(open(span))
            openStack = active
            val segment = text.substring(from, to)
            out.append(if (active.any { it.style == SpanType.CODE }) segment.replace("`", "'") else escapeInline(segment))
        }
        for (span in openStack.asReversed()) out.append(close(span))
        return out.toString()
    }

    private val inlineSpecial = setOf('\\', '*', '_', '`', '[', ']', '~')

    private fun escapeInline(text: String): String = buildString {
        for (c in text) {
            if (c in inlineSpecial) append('\\')
            append(c)
        }
    }

    private val symbolStart = Regex("""^(#|>|[-+*] )""")
    private val numberStart = Regex("""^(\d+)([.)] )""")

    /** Stops a plain paragraph like "1. not a list" from turning into a list when re-imported. */
    private fun escapeLineStart(line: String): String = when {
        symbolStart.containsMatchIn(line) -> "\\$line"
        numberStart.containsMatchIn(line) -> numberStart.replaceFirst(line, "$1\\\\$2")
        else -> line
    }

    // ---------------------------------------------------------------- import

    data class Imported(val title: String, val document: NoteDocument)

    private val headingRegex = Regex("""^(#{1,6})\s+(.*)$""")
    private val checklistRegex = Regex("""^\s*[-*+]\s+\[([ xX])]\s?(.*)$""")
    private val bulletRegex = Regex("""^\s*[-*+]\s+(.*)$""")
    private val numberedRegex = Regex("""^\s*\d+[.)]\s+(.*)$""")
    private val quoteRegex = Regex("""^\s*>\s?(.*)$""")

    /**
     * Parses Markdown into a note. A first-line `# Title` becomes the title (matching [export]);
     * otherwise [fallbackTitle] (usually the file name) is used. Blank lines separate blocks and are dropped.
     */
    fun import(markdown: String, fallbackTitle: String, newId: () -> String): Imported {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var title = fallbackTitle
        val blocks = ArrayList<TextBlock>()
        var index = 0
        val firstContent = lines.indexOfFirst { it.isNotBlank() }
        if (firstContent >= 0) {
            val m = headingRegex.matchEntire(lines[firstContent].trim())
            if (m != null && m.groupValues[1].length == 1) {
                title = parseInline(m.groupValues[2].trim()).text
                index = firstContent + 1
            }
        }
        while (index < lines.size) {
            val raw = lines[index++]
            if (raw.isBlank()) continue
            val line = raw.trimEnd()
            val (kind, content) = classify(line)
            val checked = kind == BlockKind.CHECKLIST && checklistRegex.matchEntire(line)!!.groupValues[1].isNotBlank()
            blocks += TextBlock(id = newId(), kind = kind, text = parseInline(content), checked = checked)
        }
        if (blocks.isEmpty()) blocks += TextBlock(id = newId())
        return Imported(title.trim(), NoteDocument(blocks = blocks))
    }

    private fun classify(line: String): Pair<BlockKind, String> {
        headingRegex.matchEntire(line.trim())?.let { m ->
            val kind = when (m.groupValues[1].length) {
                1, 2 -> BlockKind.HEADING1
                3 -> BlockKind.HEADING2
                else -> BlockKind.HEADING3
            }
            return kind to m.groupValues[2].trim()
        }
        checklistRegex.matchEntire(line)?.let { return BlockKind.CHECKLIST to it.groupValues[2] }
        bulletRegex.matchEntire(line)?.let { return BlockKind.BULLET to it.groupValues[1] }
        numberedRegex.matchEntire(line)?.let { return BlockKind.NUMBERED to it.groupValues[1] }
        quoteRegex.matchEntire(line)?.let { return BlockKind.QUOTE to it.groupValues[1] }
        return BlockKind.PARAGRAPH to line.trim()
    }

    /** Inline Markdown → text + spans. Unmatched markers are kept as literal characters. */
    fun parseInline(source: String): RichText = InlineParser(source).parse()
}
