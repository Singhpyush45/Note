package com.appname.notes.core.domain.model

import com.appname.notes.core.domain.document.NoteDocument
import java.time.Instant

@JvmInline value class NoteId(val value: String)

@JvmInline value class NotebookId(val value: String)

@JvmInline value class TagId(val value: String)

/**
 * What a note mainly is. Stored by name: never rename an entry.
 * - TEXT: block document (ADR-0007).
 * - INK: handwritten pages (ADR-0009); its title is still searchable text.
 * - PDF: an imported PDF (kept unchanged) with handwriting on its pages (ADR-0009 Part B).
 */
enum class NoteType { TEXT, INK, PDF }

data class Notebook(
    val id: NotebookId,
    val title: String,
    val sortOrder: Int,
    val noteCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Tag(val id: TagId, val name: String, val noteCount: Int = 0)

/** Light-weight note for lists and search results (no full document). */
data class NoteSummary(
    val id: NoteId,
    val title: String,
    val preview: String,
    val type: NoteType,
    val notebookId: NotebookId?,
    val notebookTitle: String?,
    val tags: List<Tag>,
    val isPinned: Boolean,
    val isFavourite: Boolean,
    val isArchived: Boolean,
    val trashedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Number of attached files (FR-009). */
    val attachmentCount: Int = 0,
    /** Stored file name of the first attached image, for the card preview. */
    val coverImage: String? = null,
    /** When the note's next reminder goes off (FR-010). */
    val nextReminderAt: Instant? = null,
)

/** A full note, as opened in the editor. */
data class Note(
    val id: NoteId,
    val title: String,
    val document: NoteDocument,
    val type: NoteType,
    val notebookId: NotebookId?,
    val tags: List<Tag>,
    val isPinned: Boolean,
    val isFavourite: Boolean,
    val isArchived: Boolean,
    val trashedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revision: Long,
)

/** Which notes a list shows. */
sealed interface NoteCollection {
    /** Everything except archived and trashed notes. */
    data object All : NoteCollection
    data object Favourites : NoteCollection
    data object Archive : NoteCollection
    data object Trash : NoteCollection
    data class InNotebook(val id: NotebookId) : NoteCollection
    data class WithTag(val id: TagId) : NoteCollection
}

/** List order. Pinned notes always come first (except in Trash). */
enum class NoteSort { MODIFIED, CREATED, TITLE }

enum class ModifiedWithin { ANY, TODAY, WEEK, MONTH }

data class SearchFilters(
    val notebookId: NotebookId? = null,
    val tagId: TagId? = null,
    val type: NoteType? = null,
    val modifiedWithin: ModifiedWithin = ModifiedWithin.ANY,
    val includeArchived: Boolean = false,
)

data class SearchHit(
    val note: NoteSummary,
    /** Excerpt around the match; matched words are wrapped in [HIGHLIGHT_START]/[HIGHLIGHT_END]. */
    val snippet: String,
    /** Set when the match is in a document (a PDF page, an attached file) rather than the note's text. */
    val location: HitLocation? = null,
) {
    companion object {
        const val HIGHLIGHT_START = '\u0002'
        const val HIGHLIGHT_END = '\u0003'
    }
}

/** What to do with a notebook's notes when the notebook is deleted (FR-003: never silently destroy). */
enum class NotebookDeleteMode { KEEP_NOTES, TRASH_NOTES }
