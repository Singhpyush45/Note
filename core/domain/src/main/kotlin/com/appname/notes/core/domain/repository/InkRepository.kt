package com.appname.notes.core.domain.repository

import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.ink.edit.InkEdit
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.layout.PageArrangement
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.template.TemplateLayout

/** A page of a handwritten note, without its ink. */
data class InkPageInfo(
    val id: String,
    val noteId: NoteId,
    /** 0-based position in the note. */
    val index: Int,
    val template: PageTemplate,
    val width: Float,
    val height: Float,
    /** For PDF notes: which PDF page is drawn under the ink. */
    val pdfPageIndex: Int? = null,
    /** The page's template design (owner brief §4); null for a plain [template] background. */
    val layout: TemplateLayout? = null,
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
    /**
     * New handwritten note with one empty page: a plain [template] background, or the [layout] of a chosen
     * template (its size, paper and printed parts; [template] is then ignored).
     */
    suspend fun createInkNote(
        notebookId: NotebookId?,
        template: PageTemplate,
        layout: TemplateLayout? = null,
        arrangement: PageArrangement = PageArrangement.DEFAULT,
    ): AppResult<NoteId>

    /** How the note's pages are arranged (split/infinite, vertical/horizontal; owner brief §5). */
    suspend fun arrangement(noteId: NoteId): AppResult<PageArrangement>

    suspend fun setArrangement(noteId: NoteId, arrangement: PageArrangement): AppResult<Unit>

    /**
     * Gives every handwriting page (not PDF pages) the size [width] × [height]. A page never becomes smaller
     * than its ink, so nothing is cut off; designs move and stretch with the page. Returns the changed pages.
     */
    suspend fun resizePages(noteId: NoteId, width: Float, height: Float): AppResult<List<InkPageInfo>>

    /** Sets the design (background, paper colour, template) of the given pages; each is fitted to its page's size. */
    suspend fun setPageLayouts(noteId: NoteId, layouts: Map<String, TemplateLayout>): AppResult<List<InkPageInfo>>

    /** All pages of the note in order, each with its ink. */
    suspend fun loadPages(noteId: NoteId): AppResult<List<InkPageContent>>

    /** Inserts a page at [index] (0 = first; ≥ page count = at the end), plain or with a template [layout]. */
    suspend fun addPage(noteId: NoteId, index: Int, template: PageTemplate, layout: TemplateLayout? = null): AppResult<InkPageInfo>

    /** Deletes a page and its ink. The last page can't be deleted. */
    suspend fun deletePage(pageId: String): AppResult<Unit>

    /** Gives the page a plain background (removing any template design). */
    suspend fun setTemplate(pageId: String, template: PageTemplate): AppResult<Unit>

    /** Saves one change on a page: deletes [InkEdit.removed], inserts [InkEdit.added]. */
    suspend fun commit(pageId: String, edit: InkEdit): AppResult<Unit>

    /** Ink of the first page, for list thumbnails. Empty when unreadable. */
    suspend fun firstPage(noteId: NoteId): AppResult<InkPageContent?>
}
