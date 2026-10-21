package com.appname.notes.core.domain.editor

import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.SpanType
import com.appname.notes.core.domain.document.StyleSpan

/*
 * Pure editing functions. The text field reports "the text is now X"; these functions work out what
 * changed and keep the block structure (one block per line) and the inline styles in step.
 */

/** What kind of change an edit was — used by undo history to group keystrokes. */
enum class EditKind { TYPING, DELETING, FORMAT, STRUCTURE, NONE }

data class EditResult(val state: EditorState, val kind: EditKind)

/**
 * Applies a change reported by the text field.
 *
 * Rules on top of plain text editing:
 * - Enter in an **empty** list item or quote turns it into a normal paragraph (leaves the list).
 * - Backspace at the very start of a list item / heading / quote removes that formatting first.
 * - Enter inside a list continues the list; after a heading the new line is a paragraph.
 * - Typed text continues the styles of the character before it (except links), or of the replaced text.
 */
fun EditorState.applyTextChange(
    newText: String,
    newSelection: Selection,
    newComposition: Selection?,
    newId: () -> String,
): EditResult {
    val sel = newSelection.coerceIn(newText.length)
    val comp = newComposition?.coerceIn(newText.length)?.takeIf { !it.collapsed }
    if (newText == text) {
        val pending = pendingStyles?.takeIf { sel.collapsed && sel.start == it.at }
        return EditResult(copy(selection = sel, composition = comp, pendingStyles = pending), EditKind.NONE)
    }

    // --- 1. Find the single replaced region: text[start, oldEnd) became newText[start, newEnd).
    val oldLen = text.length
    val newLen = newText.length
    var prefix = 0
    val maxPrefix = minOf(oldLen, newLen)
    while (prefix < maxPrefix && text[prefix] == newText[prefix]) prefix++
    // When the user typed a repeated character, the cursor tells us where the insertion really was.
    if (sel.collapsed && newLen > oldLen) prefix = minOf(prefix, maxOf(0, sel.start - (newLen - oldLen)))
    var suffix = 0
    val maxSuffix = minOf(oldLen, newLen) - prefix
    while (suffix < maxSuffix && text[oldLen - 1 - suffix] == newText[newLen - 1 - suffix]) suffix++
    val start = prefix
    val oldEnd = oldLen - suffix
    val newEnd = newLen - suffix
    val removed = text.substring(start, oldEnd)
    val inserted = newText.substring(start, newEnd)

    // --- 2. Special keys.
    val line = lineOf(start)
    val meta = paragraphs[line]
    if (removed.isEmpty() && inserted == "\n" && lineText(line).isEmpty() &&
        (meta.kind.isList || meta.kind == BlockKind.QUOTE)
    ) {
        // Enter on an empty list item: leave the list instead of adding another empty item.
        return EditResult(
            copy(
                paragraphs = paragraphs.replaceAt(line, meta.copy(kind = BlockKind.PARAGRAPH, checked = false)),
                selection = Selection(start),
                composition = null,
                pendingStyles = null,
            ),
            EditKind.STRUCTURE,
        )
    }
    if (removed == "\n" && inserted.isEmpty() && selection.collapsed && selection.start == oldEnd) {
        val nextLine = line + 1
        val nextMeta = paragraphs[nextLine]
        if (nextMeta.kind != BlockKind.PARAGRAPH) {
            // Backspace at the start of a formatted line: drop the formatting, keep the text where it is.
            return EditResult(
                copy(
                    paragraphs = paragraphs.replaceAt(nextLine, nextMeta.copy(kind = BlockKind.PARAGRAPH, checked = false)),
                    selection = Selection(oldEnd),
                    composition = null,
                    pendingStyles = null,
                ),
                EditKind.STRUCTURE,
            )
        }
    }

    // --- 3. Paragraph metadata.
    val removedNewlines = removed.count { it == '\n' }
    val insertedNewlines = inserted.count { it == '\n' }
    val newMetas: List<ParagraphMeta> = when {
        insertedNewlines == 0 -> listOf(meta)
        removed.isEmpty() && inserted == "\n" && start == lineStart(line) && lineText(line).isNotEmpty() ->
            // Enter at the start of a non-empty line: open an empty line above, keep this one as is.
            listOf(ParagraphMeta(newId(), continuationAbove(meta.kind)), meta)
        else -> buildList {
            add(meta)
            repeat(insertedNewlines) { add(ParagraphMeta(newId(), continuation(meta.kind))) }
        }
    }
    val newParagraphs = paragraphs.subList(0, line) + newMetas +
        paragraphs.subList(line + removedNewlines + 1, paragraphs.size)

    // --- 4. Inline styles.
    val newSpans = shiftSpans(spans, start, oldEnd, inserted.length, removed.isEmpty())
        .let { applyPending(it, start, inserted.length, removed.isEmpty()) }

    val kind = when {
        insertedNewlines > 0 || removedNewlines > 0 -> EditKind.STRUCTURE
        inserted.isEmpty() -> EditKind.DELETING
        else -> EditKind.TYPING
    }
    return EditResult(
        EditorState(
            text = newText,
            paragraphs = newParagraphs,
            spans = normalizeSpans(newSpans),
            selection = sel,
            composition = comp,
            pendingStyles = null,
        ),
        kind,
    )
}

private fun EditorState.applyPending(spans: List<StyleSpan>, start: Int, insertedLength: Int, pureInsert: Boolean): List<StyleSpan> {
    val pending = pendingStyles ?: return spans
    if (!pureInsert || pending.at != start || insertedLength == 0) return spans
    val end = start + insertedLength
    var result = spans
    for (type in pending.remove) result = removeRange(result, type, start, end)
    for (type in pending.add) result = result + StyleSpan(start, end, type)
    return result
}

/** Moves span boundaries for a replacement of `[start, oldEnd)` by [insertedLength] characters. */
internal fun shiftSpans(
    spans: List<StyleSpan>,
    start: Int,
    oldEnd: Int,
    insertedLength: Int,
    pureInsert: Boolean,
): List<StyleSpan> {
    val delta = insertedLength - (oldEnd - start)
    val insertedEnd = start + insertedLength
    return spans.mapNotNull { span ->
        val inherits = insertedLength > 0 && if (pureInsert) {
            // The character just before the cursor carries its style forward (links don't grow at their end,
            // but typing *inside* a link stays inside it — handled by the end mapping below).
            span.style.extendsWhileTyping && span.start < start && span.end >= start
        } else {
            // Typing over a selection keeps the style of the replaced text's first character.
            span.start <= start && span.end > start
        }
        val s = when {
            span.start < start -> span.start
            span.start == start -> if (inherits) start else insertedEnd
            span.start < oldEnd -> insertedEnd // began inside the removed text
            else -> span.start + delta
        }
        var e = when {
            span.end <= start -> span.end
            span.end < oldEnd -> start // ended inside the removed text
            else -> span.end + delta
        }
        if (inherits) e = maxOf(e, insertedEnd)
        if (s < e) span.copy(start = s, end = e) else null
    }
}

private fun continuation(kind: BlockKind): BlockKind = when {
    kind.isList || kind == BlockKind.QUOTE -> kind
    else -> BlockKind.PARAGRAPH
}

private fun continuationAbove(kind: BlockKind): BlockKind =
    if (kind.isList || kind == BlockKind.QUOTE) kind else BlockKind.PARAGRAPH

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }

// ---------------------------------------------------------------------------------------------
// Inline styles
// ---------------------------------------------------------------------------------------------

/** Styles active at the cursor (what the next typed character would get) or over the whole selection. */
fun EditorState.activeStyles(): Set<SpanType> {
    val result = mutableSetOf<SpanType>()
    if (selection.collapsed) {
        val at = selection.start
        for (span in spans) {
            val coversPrevious = span.start < at && span.end >= at
            val insideLink = span.style == SpanType.LINK && span.start < at && at < span.end
            if ((coversPrevious && span.style.extendsWhileTyping) || insideLink) result += span.style
        }
        pendingStyles?.takeIf { it.at == at }?.let {
            result += it.add
            result -= it.remove
        }
    } else {
        for (type in SpanType.entries) if (isFullyStyled(type, selection.min, selection.max)) result += type
    }
    return result
}

/** The link under the cursor/selection start, if any. */
fun EditorState.linkAtSelection(): StyleSpan? {
    val at = selection.min
    return spans.firstOrNull { it.style == SpanType.LINK && it.start <= at && (at < it.end || (at == it.end && !selection.collapsed)) }
        ?: spans.firstOrNull { it.style == SpanType.LINK && it.start < at && at <= it.end }
}

private fun EditorState.isFullyStyled(type: SpanType, from: Int, to: Int): Boolean {
    if (from >= to) return false
    var covered = from
    for (span in spans.filter { it.style == type }.sortedBy { it.start }) {
        if (span.start > covered) break
        covered = maxOf(covered, span.end)
        if (covered >= to) return true
    }
    return covered >= to
}

/** Bold/italic/… button: styles the selection, or arms the style for the next typed text. */
fun EditorState.toggleStyle(type: SpanType): EditResult {
    require(type != SpanType.LINK) { "Use setLink/removeLink for links" }
    if (selection.collapsed) {
        val at = selection.start
        val active = type in activeStyles()
        val current = pendingStyles?.takeIf { it.at == at } ?: PendingStyles(at)
        val updated = if (active) {
            current.copy(add = current.add - type, remove = current.remove + type)
        } else {
            current.copy(add = current.add + type, remove = current.remove - type)
        }
        return EditResult(copy(pendingStyles = updated), EditKind.NONE)
    }
    val from = selection.min
    val to = selection.max
    val newSpans = if (isFullyStyled(type, from, to)) {
        removeRange(spans, type, from, to)
    } else {
        spans + StyleSpan(from, to, type)
    }
    return EditResult(copy(spans = normalizeSpans(newSpans), composition = null), EditKind.FORMAT)
}

/** Turns the selection into a link to [url] (replacing any link already there). */
fun EditorState.setLink(url: String): EditResult {
    if (selection.collapsed) return EditResult(this, EditKind.NONE)
    val from = selection.min
    val to = selection.max
    val withoutLinks = removeRange(spans, SpanType.LINK, from, to)
    return EditResult(
        copy(spans = normalizeSpans(withoutLinks + StyleSpan(from, to, SpanType.LINK, url)), composition = null),
        EditKind.FORMAT,
    )
}

/** Removes the whole link under the cursor or the links inside the selection. */
fun EditorState.removeLink(): EditResult {
    val target = if (selection.collapsed) linkAtSelection() else null
    val newSpans = when {
        target != null -> spans - target
        !selection.collapsed -> removeRange(spans, SpanType.LINK, selection.min, selection.max)
        else -> return EditResult(this, EditKind.NONE)
    }
    return EditResult(copy(spans = normalizeSpans(newSpans)), EditKind.FORMAT)
}

/** Removes [type] (any URL for links) from `[from, to)`, splitting spans that stick out on both sides. */
internal fun removeRange(spans: List<StyleSpan>, type: SpanType, from: Int, to: Int): List<StyleSpan> =
    spans.flatMap { span ->
        if (span.style != type || !span.overlaps(from, to)) {
            listOf(span)
        } else {
            buildList {
                if (span.start < from) add(span.copy(end = from))
                if (span.end > to) add(span.copy(start = to))
            }
        }
    }

// ---------------------------------------------------------------------------------------------
// Block kinds
// ---------------------------------------------------------------------------------------------

/** Lines touched by the selection. */
fun EditorState.selectedLines(): IntRange = lineOf(selection.min)..lineOf(selection.max)

/** Kind of the line holding the cursor (or the selection start). */
fun EditorState.currentKind(): BlockKind = paragraphs[lineOf(selection.min)].kind

/** Heading/list/quote buttons: applies [kind] to the selected lines, or reverts them to paragraphs. */
fun EditorState.toggleKind(kind: BlockKind): EditResult {
    val lines = selectedLines()
    val allAlready = lines.all { paragraphs[it].kind == kind }
    val target = if (allAlready) BlockKind.PARAGRAPH else kind
    val updated = paragraphs.toMutableList()
    for (line in lines) {
        val old = updated[line]
        updated[line] = old.copy(kind = target, checked = if (target == BlockKind.CHECKLIST) old.checked else false)
    }
    return EditResult(copy(paragraphs = updated, composition = null), EditKind.FORMAT)
}

/** Ticks/unticks a checklist item. Does nothing for other kinds. */
fun EditorState.toggleChecked(line: Int): EditResult {
    val meta = paragraphs.getOrNull(line) ?: return EditResult(this, EditKind.NONE)
    if (meta.kind != BlockKind.CHECKLIST) return EditResult(this, EditKind.NONE)
    return EditResult(copy(paragraphs = paragraphs.replaceAt(line, meta.copy(checked = !meta.checked))), EditKind.FORMAT)
}
