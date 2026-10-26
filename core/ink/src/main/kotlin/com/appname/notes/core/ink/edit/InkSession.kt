package com.appname.notes.core.ink.edit

import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.PageTemplate

/**
 * The open handwritten note in memory: its pages, their ink, and the undo history.
 *
 * This is the source of truth while the editor is open. Every change is applied here first (instantly,
 * on the UI thread, so the screen never waits for the disk) and then reported through [onEdit] so the
 * caller can save exactly that change. Like a document object in a C++ editor, with the database as a
 * write-behind log.
 *
 * Not thread-safe: use from one thread (the main thread).
 */
class InkSession(pages: List<Page>, historyLimit: Int = UndoHistory.DEFAULT_LIMIT) {

    data class Page(
        val id: String,
        val template: PageTemplate,
        val width: Float,
        val height: Float,
        val items: PageItems,
        /** For PDF notes: the PDF page shown under the ink. */
        val pdfPageIndex: Int? = null,
        /** The page's template design (owner brief §4), drawn instead of the plain [template] background. */
        val layout: com.appname.notes.core.ink.template.TemplateLayout? = null,
    )

    /** Called after every applied change (including undo/redo), in order. */
    var onEdit: ((PageEdit) -> Unit)? = null

    /** Called after anything visible changed (ink, pages, undo state). */
    var onChanged: (() -> Unit)? = null

    var pages: List<Page> = pages
        private set

    /** Increments on every change; handy for observers and tests. */
    var version: Long = 0
        private set

    /** Each undo step is one or more page edits (moving ink to another page changes two pages at once). */
    private val history = UndoHistory<List<PageEdit>>(historyLimit, { it.all { e -> e.edit.isEmpty } }) { steps ->
        steps.asReversed().map { it.inverse() }
    }

    val canUndo: Boolean get() = history.canUndo && transaction == null
    val canRedo: Boolean get() = history.canRedo && transaction == null

    fun page(id: String): Page? = pages.firstOrNull { it.id == id }

    fun pageIndex(id: String): Int = pages.indexOfFirst { it.id == id }

    /** Applies and records one change. Returns false if it doesn't fit (nothing changes then). */
    fun apply(pageId: String, edit: InkEdit): Boolean {
        check(transaction == null) { "finish the gesture first" }
        if (edit.isEmpty) return true
        if (!replaceItems(pageId, edit)) return false
        val pe = PageEdit(pageId, edit)
        history.record(listOf(pe))
        changed(pe)
        return true
    }

    /**
     * Applies changes to several pages as one undoable step (e.g. ink moved from one page to the next). All or
     * nothing: if one doesn't fit, the pages are left as they were.
     */
    fun applyAll(edits: List<PageEdit>): Boolean {
        check(transaction == null) { "finish the gesture first" }
        val steps = edits.filterNot { it.edit.isEmpty }
        if (steps.isEmpty()) return true
        val before = pages
        for (pe in steps) {
            if (!replaceItems(pe.pageId, pe.edit)) {
                pages = before
                return false
            }
        }
        history.record(steps)
        version++
        steps.forEach { onEdit?.invoke(it) }
        notifyChanged()
        return true
    }

    fun undo(): Boolean = step(history.undo())

    fun redo(): Boolean = step(history.redo())

    private fun step(steps: List<PageEdit>?): Boolean {
        if (steps == null || transaction != null) return false
        val before = pages
        for (pe in steps) {
            if (!replaceItems(pe.pageId, pe.edit)) {
                // Can't happen unless the page changed outside the history; drop the history instead of guessing.
                pages = before
                history.clear()
                notifyChanged()
                return false
            }
        }
        version++
        steps.forEach { onEdit?.invoke(it) }
        notifyChanged()
        return true
    }

    // ------------------------------------------------------------------ gestures (eraser): many small steps, one undo

    private class Transaction(val pageId: String, val before: PageItems)

    private var transaction: Transaction? = null

    val inGesture: Boolean get() = transaction != null

    /** Starts a gesture whose small steps are shown live but saved and undone as one change. */
    fun beginGesture(pageId: String): Boolean {
        check(transaction == null) { "gesture already running" }
        val page = page(pageId) ?: return false
        transaction = Transaction(pageId, page.items)
        return true
    }

    /** One live step of the running gesture (not saved, not in history yet). */
    fun applyInGesture(edit: InkEdit): Boolean {
        val tx = checkNotNull(transaction) { "no gesture running" }
        if (edit.isEmpty) return true
        if (!replaceItems(tx.pageId, edit)) return false
        version++
        notifyChanged()
        return true
    }

    /** Ends the gesture: its net change becomes one saved, undoable edit. Returns that edit (or null). */
    fun endGesture(): PageEdit? {
        val tx = checkNotNull(transaction) { "no gesture running" }
        transaction = null
        val after = page(tx.pageId)?.items ?: return null
        val beforeIds = tx.before.items.associateBy { it.id }
        val afterIds = after.items.associateBy { it.id }
        val removed = tx.before.items.filter { afterIds[it.id] != it }
        val added = after.items.filter { beforeIds[it.id] != it }
        val edit = InkEdit(removed, added)
        if (edit.isEmpty) {
            notifyChanged()
            return null
        }
        val pe = PageEdit(tx.pageId, edit)
        history.record(listOf(pe))
        version++
        onEdit?.invoke(pe)
        notifyChanged()
        return pe
    }

    /** Throws the gesture away and restores the page as it was before it. */
    fun cancelGesture() {
        val tx = transaction ?: return
        transaction = null
        pages = pages.map { if (it.id == tx.pageId) it.copy(items = tx.before) else it }
        version++
        notifyChanged()
    }

    // ------------------------------------------------------------------ page structure (saved by the caller, not undoable)

    fun insertPage(index: Int, page: Page) {
        val i = index.coerceIn(0, pages.size)
        pages = pages.toMutableList().apply { add(i, page) }
        version++
        notifyChanged()
    }

    /** Removes a page. Undo steps on it can no longer apply, so history is cleared. */
    fun removePage(pageId: String) {
        pages = pages.filterNot { it.id == pageId }
        history.clear()
        version++
        notifyChanged()
    }

    /** Changes pages' size or design (page settings); their ink is untouched. */
    fun updatePages(change: (Page) -> Page) {
        pages = pages.map { p -> change(p).copy(id = p.id, items = p.items) }
        version++
        notifyChanged()
    }

    fun setTemplate(pageId: String, template: PageTemplate) {
        pages = pages.map { if (it.id == pageId) it.copy(template = template, layout = null) else it }
        version++
        notifyChanged()
    }

    fun items(pageId: String): List<InkItem> = page(pageId)?.items?.items.orEmpty()

    // ------------------------------------------------------------------ internals

    private fun replaceItems(pageId: String, edit: InkEdit): Boolean {
        val i = pageIndex(pageId)
        if (i < 0) return false
        val updated = pages[i].items.apply(edit) ?: return false
        pages = pages.toMutableList().also { it[i] = it[i].copy(items = updated) }
        return true
    }

    private fun changed(pe: PageEdit) {
        version++
        onEdit?.invoke(pe)
        notifyChanged()
    }

    private fun notifyChanged() {
        onChanged?.invoke()
    }
}
