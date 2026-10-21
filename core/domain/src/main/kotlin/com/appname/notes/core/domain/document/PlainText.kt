package com.appname.notes.core.domain.document

/** Plain text of the document (one line per text block) — used for search and previews. */
fun NoteDocument.toPlainText(): String = textBlocks.joinToString("\n") { it.text.text }

/**
 * Short preview for note cards: one line per non-empty block, with list markers so a checklist still
 * looks like a checklist ("☐ Milk", "• item", "2. step"). At most [maxChars] characters.
 */
fun NoteDocument.preview(maxChars: Int = 300): String {
    var number = 0
    return textBlocks.asSequence()
        .mapNotNull { block ->
            number = if (block.kind == BlockKind.NUMBERED) number + 1 else 0
            val text = block.text.text.trim()
            if (text.isEmpty()) return@mapNotNull null
            when (block.kind) {
                BlockKind.BULLET -> "• $text"
                BlockKind.NUMBERED -> "$number. $text"
                BlockKind.CHECKLIST -> if (block.checked) "☑ $text" else "☐ $text"
                else -> text
            }
        }
        .joinToString("\n")
        .take(maxChars)
}

/** True when the note has no visible content (used to discard notes the user never typed into). */
fun NoteDocument.isBlank(): Boolean = isFullyEditable && textBlocks.all { it.text.text.isBlank() }

/** Uses which inline styles / block kinds — e.g. to warn what an export can't represent. */
fun NoteDocument.usedSpanTypes(): Set<SpanType> =
    textBlocks.flatMapTo(mutableSetOf()) { block -> block.text.spans.map { it.style } }
