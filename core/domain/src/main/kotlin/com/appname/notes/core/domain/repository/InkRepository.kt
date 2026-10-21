package com.appname.notes.core.domain.repository

import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.ink.edit.InkEdit
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.PageTemplate

/** A page of a handwritten note, without its ink. */
data class InkPageInfo(
    val id: String,
    val noteId: NoteId,
    /** 0-based position in the note. */
    val index: Int,
    val template: PageTemplate,
    val width: Float,
    val height: Float,
)

/**
 * A page with its ink, in paint order.
 * [unreadableItems] counts stored items this app version can't read (newer format or damaged). When it is
 * above zero the page must open read-only, so those items are never lost by overwriting (SRS 15.1).
 */
data class InkPageContent(
    val info: InkPageInfo,
    val items: List<InkItem>,
    val unreadableItems: Int,
) {
    val readOnly: Boolean get() = unreadableItems > 0
}

/**
 * Storage for handwritten notes (ADR-0009). Every change is saved incrementally as one [InkEdit] in one
 * transaction, so drawing a stroke writes one small row and a crash loses at most the stroke in progress.
 */
interface InkRepository {
    /** New handwritten note with one empty page. */
    suspend fun createInkNote(notebookId: NotebookId?, template: PageTemplate): AppResult<NoteId>

    /** All pages of the note in order, each with its ink. */
    suspend fun loadPages(noteId: NoteId): AppResult<List<InkPageContent>>

    /** Inserts a page at [index] (0 = first; ≥ page count = at the end). */
    suspend fun addPage(noteId: NoteId, index: Int, template: PageTemplate): AppResult<InkPageInfo>

    /** Deletes a page and its ink. The last page can't be deleted. */
    suspend fun deletePage(pageId: String): AppResult<Unit>

    suspend fun setTemplate(pageId: String, template: PageTemplate): AppResult<Unit>

    /** Saves one change on a page: deletes [InkEdit.removed], inserts [InkEdit.added]. */
    suspend fun commit(pageId: String, edit: InkEdit): AppResult<Unit>

    /** Ink of the first page, for list thumbnails. Empty when unreadable. */
    suspend fun firstPage(noteId: NoteId): AppResult<InkPageContent?>
}
