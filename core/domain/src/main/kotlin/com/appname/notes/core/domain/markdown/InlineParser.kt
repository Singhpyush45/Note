package com.appname.notes.core.domain.markdown

import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.SpanType
import com.appname.notes.core.domain.document.StyleSpan
import com.appname.notes.core.domain.editor.normalizeSpans

/**
 * Inline Markdown parser following CommonMark's "delimiter run" algorithm for `*`, `_` and GFM `~~`,
 * plus code spans, links and backslash escapes.
 *
 * How it works, briefly:
 * 1. Split the line into items: plain text, code spans, links (parsed recursively), and delimiter runs
 *    like `**` with flags saying whether each run can open and/or close emphasis.
 * 2. Walk closers left to right; pair each with the nearest compatible opener before it. Using 2
 *    characters from both makes bold, 1 makes italic. Leftover characters become literal text.
 * 3. Render items to text and turn each pair into a span covering the text between them.
 */
internal class InlineParser(private val src: String) {

    private sealed interface Item

    private class TextItem(val text: String, val spans: List<StyleSpan> = emptyList()) : Item

    private class Delim(val char: Char, var count: Int, val canOpen: Boolean, val canClose: Boolean) : Item {
        val originalCount = count
        var active = true
    }

    private data class Pair(val opener: Int, val closer: Int, val type: SpanType)

    private val escapable = setOf('\\', '*', '_', '`', '[', ']', '~', '#', '>', '-', '+', '.', ')', '(', '!')

    fun parse(): RichText {
        val items = tokenize()
        val pairs = matchDelimiters(items)
        return render(items, pairs)
    }

    private fun tokenize(): List<Item> {
        val items = ArrayList<Item>()
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) { items += TextItem(text.toString()); text.clear() }
        }
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\\' && i + 1 < src.length && src[i + 1] in escapable -> { text.append(src[i + 1]); i += 2 }
                c == '`' -> {
                    val close = src.indexOf('`', i + 1)
                    if (close > i + 1) {
                        flush()
                        val code = src.substring(i + 1, close)
                        items += TextItem(code, listOf(StyleSpan(0, code.length, SpanType.CODE)))
                        i = close + 1
                    } else { text.append(c); i++ }
                }
                c == '[' -> {
                    val link = parseLink(i)
                    if (link != null) {
                        flush()
                        items += link.first
                        i = link.second
                    } else { text.append(c); i++ }
                }
                c == '*' || c == '_' || c == '~' -> {
                    var j = i
                    while (j < src.length && src[j] == c) j++
                    val count = j - i
                    if (c == '~' && count != 2) {
                        text.append(src, i, j)
                    } else {
                        flush()
                        val before = if (i == 0) ' ' else src[i - 1]
                        val after = if (j >= src.length) ' ' else src[j]
                        val leftFlanking = !after.isWhitespace() && (!after.isPunct() || before.isWhitespace() || before.isPunct())
                        val rightFlanking = !before.isWhitespace() && (!before.isPunct() || after.isWhitespace() || after.isPunct())
                        val (canOpen, canClose) = if (c == '_') {
                            (leftFlanking && (!rightFlanking || before.isPunct())) to (rightFlanking && (!leftFlanking || after.isPunct()))
                        } else {
                            leftFlanking to rightFlanking
                        }
                        items += Delim(c, count, canOpen, canClose)
                    }
                    i = j
                }
                else -> { text.append(c); i++ }
            }
        }
        flush()
        return items
    }

    /** `[text](url)` starting at [at]; returns the item and the index after it. */
    private fun parseLink(at: Int): kotlin.Pair<TextItem, Int>? {
        var depth = 0
        var i = at
        var closeBracket = -1
        while (i < src.length) {
            when (src[i]) {
                '\\' -> i++
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) { closeBracket = i; break } }
            }
            i++
        }
        if (closeBracket < 0 || !src.startsWith("(", closeBracket + 1)) return null
        val closeParen = src.indexOf(')', closeBracket + 2)
        if (closeParen < 0) return null
        val inner = InlineParser(src.substring(at + 1, closeBracket)).parse()
        if (inner.text.isEmpty()) return null
        val url = src.substring(closeBracket + 2, closeParen).trim().replace("%29", ")").replace("%20", " ")
        val spans = inner.spans + StyleSpan(0, inner.text.length, SpanType.LINK, url)
        return TextItem(inner.text, spans) to closeParen + 1
    }

    private fun matchDelimiters(items: List<Item>): List<Pair> {
        val pairs = ArrayList<Pair>()
        for (closerIndex in items.indices) {
            val closer = items[closerIndex] as? Delim ?: continue
            if (!closer.canClose) continue
            var openerIndex = closerIndex - 1
            while (closer.count > 0 && openerIndex >= 0) {
                val opener = items[openerIndex] as? Delim
                if (opener == null || !opener.active || opener.count == 0 || opener.char != closer.char || !opener.canOpen) {
                    openerIndex--
                    continue
                }
                // CommonMark "rule of 3": avoids pairing e.g. `*foo**` wrongly.
                val ruleOf3 = (opener.canClose || closer.canOpen) &&
                    (opener.originalCount + closer.originalCount) % 3 == 0 &&
                    !(opener.originalCount % 3 == 0 && closer.originalCount % 3 == 0)
                if (closer.char != '~' && ruleOf3) {
                    openerIndex--
                    continue
                }
                val use = if (closer.char == '~') 2 else if (opener.count >= 2 && closer.count >= 2) 2 else 1
                val type = when {
                    closer.char == '~' -> SpanType.STRIKETHROUGH
                    use == 2 -> SpanType.BOLD
                    else -> SpanType.ITALIC
                }
                pairs += Pair(openerIndex, closerIndex, type)
                opener.count -= use
                closer.count -= use
                // Delimiters between the pair can no longer match anything outside it.
                for (k in openerIndex + 1 until closerIndex) (items[k] as? Delim)?.active = false
                if (opener.count > 0) continue // same opener may pair again (e.g. ***x***)
                openerIndex--
            }
        }
        return pairs
    }

    private fun render(items: List<Item>, pairs: List<Pair>): RichText {
        val out = StringBuilder()
        val spans = ArrayList<StyleSpan>()
        val startAfter = IntArray(items.size)
        val endBefore = IntArray(items.size)
        items.forEachIndexed { index, item ->
            endBefore[index] = out.length
            when (item) {
                is TextItem -> {
                    val base = out.length
                    out.append(item.text)
                    item.spans.forEach { spans += it.copy(start = it.start + base, end = it.end + base) }
                }
                // Unused delimiter characters are just text.
                is Delim -> repeat(item.count) { out.append(item.char) }
            }
            startAfter[index] = out.length
        }
        for (pair in pairs) {
            val start = startAfter[pair.opener]
            val end = endBefore[pair.closer]
            if (start < end) spans += StyleSpan(start, end, pair.type)
        }
        return RichText(out.toString(), normalizeSpans(spans))
    }

    private fun Char.isPunct(): Boolean = !isLetterOrDigit() && !isWhitespace()
}
