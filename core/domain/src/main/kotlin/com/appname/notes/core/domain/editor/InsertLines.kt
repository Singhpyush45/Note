package com.appname.notes.core.domain.editor

import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.RichText

/**
 * Adds whole lines (each with its kind and styles) under the cursor's line, e.g. a clipped web page's title and
 * summary. The cursor goes to the end of the last added line. One undo step.
 */
fun EditorState.insertLinesAfterCursor(lines: List<Pair<BlockKind, RichText>>, newId: () -> String): EditResult {
    if (lines.isEmpty()) return EditResult(this, EditKind.NONE)
    val line = lineOf(selection.max)
    val at = lineEnd(line)
    val body = lines.joinToString("") { "\n" + it.second.text.replace('\n', ' ') }
    var s = rawReplace(at, at, body, BlockKind.PARAGRAPH, Selection(at + body.length), newId)
    var paragraphs = s.paragraphs
    val added = ArrayList(s.spans)
    var offset = at
    lines.forEachIndexed { k, (kind, rich) ->
        offset += 1 // the newline
        paragraphs = paragraphs.replaceAt(line + 1 + k, paragraphs[line + 1 + k].copy(kind = kind))
        rich.spans.forEach { sp -> added += sp.copy(start = sp.start + offset, end = sp.end + offset) }
        offset += rich.text.length
    }
    s = s.copy(paragraphs = paragraphs, spans = normalizeSpans(added))
    return EditResult(s, EditKind.STRUCTURE)
}
