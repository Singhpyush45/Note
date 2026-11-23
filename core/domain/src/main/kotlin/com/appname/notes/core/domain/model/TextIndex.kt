package com.appname.notes.core.domain.model

/**
 * Text found in a note's documents (FR-011, SRS Journey 4): the pages of a PDF note, attached PDFs and
 * photos/scans. Extracted on the device in the background and searchable like note text.
 */
sealed interface TextSource {
    val noteId: NoteId
    /** Stable key: one index entry (and job) per source. */
    val key: String

    /** An attached photo or PDF. */
    data class AttachmentSource(val attachmentId: AttachmentId, override val noteId: NoteId, val kind: AttachmentKind, val fileName: String) : TextSource {
        override val key: String get() = "a:${attachmentId.value}"
    }

    /** The document of a PDF note (ADR-0009 Part B). */
    data class PdfNoteSource(override val noteId: NoteId, val fileName: String) : TextSource {
        override val key: String get() = "p:${noteId.value}"
    }
}

/** How a page's text was obtained. Stored by name. */
enum class ExtractionMethod { PDF_TEXT, OCR }

/** Text of one page (0-based; photos have one page). */
data class PageText(val page: Int, val text: String, val method: ExtractionMethod)

/** Outcome for a source. FAILED and UNAVAILABLE are retried a few times later. Stored by name. */
enum class IndexStatus { DONE, EMPTY, FAILED, UNAVAILABLE }

/** Where a search match is when it isn't in the note's own text. */
data class HitLocation(
    /** Attached file the text is in; null for the PDF of a PDF note. */
    val attachmentId: AttachmentId?,
    val attachmentName: String?,
    /** 0-based page; null for photos. */
    val page: Int?,
)
