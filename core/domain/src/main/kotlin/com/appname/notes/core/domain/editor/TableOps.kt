package com.appname.notes.core.domain.editor

import com.appname.notes.core.domain.document.BlockKind

/*
 * Tables in typed notes (8c). A table is a run of neighbouring TABLE_ROW lines; each line's cells are separated by
 * tab characters ('\t'), so a table is still plain text the field can edit, search and undo. [LineDecorations]
 * draws the rows as an aligned grid. These functions add and remove rows and columns and move between cells.
 */

const val CELL_SEPARATOR = '\t'

/** The lines of the table that [line] is in (empty if it isn't a table row). */
fun EditorState.tableLines(line: Int): IntRange {
    if (paragraphs.getOrNull(line)?.kind != BlockKind.TABLE_ROW) return IntRange.EMPTY
    var a = line
    while (a > 0 && paragraphs[a - 1].kind == BlockKind.TABLE_ROW) a--
    var b = line
    while (b < lineCount - 1 && paragraphs[b + 1].kind == BlockKind.TABLE_ROW) b++
    return a..b
}

/** Whether the cursor is in a table. */
val EditorState.inTable: Boolean get() = paragraphs[lineOf(selection.min)].kind == BlockKind.TABLE_ROW

private fun EditorState.cellCount(line: Int) = lineText(line).count { it == CELL_SEPARATOR } + 1

private fun EditorState.columns(lines: IntRange) = lines.maxOf { cellCount(it) }

/** Which cell of its row [offset] is in. */
private fun EditorState.cellIndexAt(offset: Int): Int {
    val line = lineOf(offset)
    return text.substring(lineStart(line), offset).count { it == CELL_SEPARATOR }
}

/**
 * Replaces `text[start, end)` by [inserted] without the typing rules: lines made by newlines in [inserted] get
 * [newKind]; styles are moved, not extended over the new text.
 */
internal fun EditorState.rawReplace(start: Int, end: Int, inserted: String, newKind: BlockKind, newSelection: Selection, newId: () -> String): EditorState {
    val removed = text.substring(start, end)
    val line = lineOf(start)
    val removedNewlines = removed.count { it == '\n' }
    val insertedNewlines = inserted.count { it == '\n' }
    val metas = listOf(paragraphs[line]) + List(insertedNewlines) { ParagraphMeta(newId(), newKind) }
    val newParagraphs = paragraphs.subList(0, line) + metas + paragraphs.subList(line + removedNewlines + 1, paragraphs.size)
    val newText = text.replaceRange(start, end, inserted)
    return EditorState(
        text = newText,
        paragraphs = newParagraphs,
        spans = normalizeSpans(shiftSpans(spans, start, end, inserted.length, pureInsert = false)),
        selection = newSelection.coerceIn(newText.length),
        composition = null,
        pendingStyles = null,
    )
}

/** Enter, and deleting a cell separator, inside a table. Null: not a table key, handle normally. */
internal fun EditorState.tableKey(line: Int, removed: String, inserted: String, start: Int, oldEnd: Int, newId: () -> String): EditResult? {
    if (removed.isEmpty() && inserted == "\n") {
        val rows = tableLines(line)
        val empty = lineText(line).all { it == CELL_SEPARATOR }
        if (empty && line == rows.last && rows.first != rows.last) {
            // Enter in an empty last row: the row becomes a normal line under the table.
            val s = rawReplace(lineStart(line), lineEnd(line), "", BlockKind.PARAGRAPH, Selection(lineStart(line)), newId)
            return EditResult(s.copy(paragraphs = s.paragraphs.replaceAt(line, s.paragraphs[line].copy(kind = BlockKind.PARAGRAPH))), EditKind.STRUCTURE)
        }
        // Enter anywhere in a row: a new empty row below it (the row's text isn't split).
        val at = lineEnd(line)
        val row = "\n" + CELL_SEPARATOR.toString().repeat(columns(rows) - 1)
        return EditResult(rawReplace(at, at, row, BlockKind.TABLE_ROW, Selection(at + 1), newId), EditKind.STRUCTURE)
    }
    if (removed == CELL_SEPARATOR.toString() && inserted.isEmpty() && oldEnd == start + 1) {
        // Backspace at the start of a cell: go to the end of the cell before instead of merging the two.
        return EditResult(copy(selection = Selection(start), composition = null), EditKind.NONE)
    }
    return null
}

/** Inserts a [rows] × [cols] table at the cursor's line (on an empty line, it takes that line's place). */
fun EditorState.insertTable(rows: Int, cols: Int, newId: () -> String): EditResult {
    val r = rows.coerceIn(1, MAX_TABLE)
    val c = cols.coerceIn(1, MAX_TABLE)
    val row = CELL_SEPARATOR.toString().repeat(c - 1)
    val line = lineOf(selection.max)
    val meta = paragraphs[line]
    val body = List(r) { row }.joinToString("\n")
    var s: EditorState
    val first: Int
    if (lineText(line).isEmpty() && meta.kind == BlockKind.PARAGRAPH) {
        val at = lineStart(line)
        s = rawReplace(at, at, body, BlockKind.TABLE_ROW, Selection(at), newId)
        s = s.copy(paragraphs = s.paragraphs.replaceAt(line, meta.copy(kind = BlockKind.TABLE_ROW)))
        first = line
    } else {
        val at = lineEnd(line)
        s = rawReplace(at, at, "\n" + body, BlockKind.TABLE_ROW, Selection(at + 1), newId)
        first = line + 1
    }
    // Always a line under the table, so writing can go on after it.
    val last = first + r - 1
    if (last == s.lineCount - 1 || s.paragraphs[last + 1].kind == BlockKind.TABLE_ROW) {
        val at = s.lineEnd(last)
        val keep = s.selection
        s = s.rawReplace(at, at, "\n", BlockKind.PARAGRAPH, keep, newId)
    }
    return EditResult(s, EditKind.STRUCTURE)
}

/** Adds an empty row under the cursor's row. */
fun EditorState.tableAddRow(newId: () -> String): EditResult {
    val line = lineOf(selection.max)
    val rows = tableLines(line)
    if (rows.isEmpty()) return EditResult(this, EditKind.NONE)
    val at = lineEnd(line)
    val row = "\n" + CELL_SEPARATOR.toString().repeat(columns(rows) - 1)
    return EditResult(rawReplace(at, at, row, BlockKind.TABLE_ROW, Selection(at + 1), newId), EditKind.STRUCTURE)
}

/** Adds an empty column at the right of the table. */
fun EditorState.tableAddColumn(newId: () -> String): EditResult {
    val rows = tableLines(lineOf(selection.max))
    if (rows.isEmpty()) return EditResult(this, EditKind.NONE)
    var s = this
    for (l in rows.reversed()) {
        val at = s.lineEnd(l)
        val sel = s.selection
        fun shift(o: Int) = if (o > at) o + 1 else o
        s = s.rawReplace(at, at, CELL_SEPARATOR.toString(), BlockKind.TABLE_ROW, Selection(shift(sel.start), shift(sel.end)), newId)
    }
    return EditResult(s, EditKind.STRUCTURE)
}

/** Removes the cursor's row; the last row removes the table. */
fun EditorState.tableDeleteRow(newId: () -> String): EditResult {
    val line = lineOf(selection.max)
    val rows = tableLines(line)
    if (rows.isEmpty()) return EditResult(this, EditKind.NONE)
    if (rows.first == rows.last) return deleteTable(rows, newId)
    val s = if (line > 0) {
        val removed = rawReplace(lineEnd(line - 1), lineEnd(line), "", BlockKind.TABLE_ROW, Selection(0), newId)
        // The cursor goes to the row that took its place, or the row above when it was the last.
        val target = if (line < removed.lineCount && removed.paragraphs[line].kind == BlockKind.TABLE_ROW) removed.lineStart(line) else removed.lineStart(line - 1)
        removed.copy(selection = Selection(target))
    } else {
        // First line of the note: remove it with the newline after it; the next row's block info moves up.
        val next = paragraphs[1]
        rawReplace(0, lineStart(1), "", BlockKind.TABLE_ROW, Selection(0), newId).let { it.copy(paragraphs = it.paragraphs.replaceAt(0, next)) }
    }
    return EditResult(s, EditKind.STRUCTURE)
}

/** Removes the cursor's column; the last column removes the table. */
fun EditorState.tableDeleteColumn(newId: () -> String): EditResult {
    val line = lineOf(selection.max)
    val rows = tableLines(line)
    if (rows.isEmpty()) return EditResult(this, EditKind.NONE)
    if (columns(rows) <= 1) return deleteTable(rows, newId)
    val c = cellIndexAt(selection.max)
    var s = this
    for (l in rows.reversed()) {
        val cells = s.lineText(l).split(CELL_SEPARATOR)
        if (c >= cells.size) continue
        val base = s.lineStart(l)
        val cellStart = base + cells.take(c).sumOf { it.length + 1 }
        val cellEnd = cellStart + cells[c].length
        val (from, to) = if (c < cells.size - 1) cellStart to cellEnd + 1 else cellStart - 1 to cellEnd
        s = s.rawReplace(from, to, "", BlockKind.TABLE_ROW, Selection(from.coerceAtLeast(base)), newId)
    }
    return EditResult(s.copy(selection = Selection(s.lineStart(line))), EditKind.STRUCTURE)
}

private fun EditorState.deleteTable(rows: IntRange, newId: () -> String): EditResult {
    // The rows' text goes; one empty line stays where the table was.
    val from = lineStart(rows.first)
    val to = lineEnd(rows.last)
    var s = rawReplace(from, to, "", BlockKind.PARAGRAPH, Selection(from), newId)
    s = s.copy(paragraphs = s.paragraphs.replaceAt(rows.first, s.paragraphs[rows.first].copy(kind = BlockKind.PARAGRAPH)))
    return EditResult(s, EditKind.STRUCTURE)
}

/** Moves the cursor to the next cell (the next row's first cell after the last one; a new row after the table's end). */
fun EditorState.tableNextCell(newId: () -> String): EditResult {
    val o = selection.max
    val line = lineOf(o)
    val rows = tableLines(line)
    if (rows.isEmpty()) return EditResult(this, EditKind.NONE)
    val end = lineEnd(line)
    val tab = text.indexOf(CELL_SEPARATOR, o).takeIf { it in o until end }
    return when {
        tab != null -> EditResult(copy(selection = Selection(tab + 1), composition = null), EditKind.NONE)
        line < rows.last -> EditResult(copy(selection = Selection(lineStart(line + 1)), composition = null), EditKind.NONE)
        else -> tableAddRow(newId)
    }
}

internal const val MAX_TABLE = 20
