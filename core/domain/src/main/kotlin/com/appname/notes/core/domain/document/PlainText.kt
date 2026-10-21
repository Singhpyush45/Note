package com.appname.notes.core.domain.document

/** Plain text of the document (one line per text block) — used for search and previews. */
fun NoteDocument.toPlainText(): String = textBlocks.joinToString("\n") { it.text.text }

/** Short preview for cards: blank lines removed, whitespace collapsed, at most [maxChars]. */
fun NoteDocument.preview(maxChars: Int = 240): String =
    textBlocks.asSequence()
        .map { it.text.text.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
        .take(maxChars)

/** True when the note has no visible content (used to discard notes the user never typed into). */
fun NoteDocument.isBlank(): Boolean = isFullyEditable && textBlocks.all { it.text.text.isBlank() }

/** Uses which inline styles / block kinds — e.g. to warn what an export can't represent. */
fun NoteDocument.usedSpanTypes(): Set<SpanType> =
    textBlocks.flatMapTo(mutableSetOf()) { block -> block.text.spans.map { it.style } }
