package com.appname.notes.core.ink.edit

import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.PaintOrder

/**
 * The items on one page, kept in paint order. Immutable: every change returns a new instance, so the UI
 * can hand a snapshot to the renderer while the next change is computed.
 */
class PageItems private constructor(
    /** Paint order: lowest first. */
    val items: List<InkItem>,
    private val byId: Map<String, InkItem>,
) {
    val size: Int get() = items.size

    operator fun get(id: String): InkItem? = byId[id]

    operator fun contains(id: String) = id in byId

    /** Next z for new ink: above everything on the page. */
    val nextZ: Long get() = (items.maxOfOrNull { it.z } ?: 0L) + 1

    /**
     * Applies [edit]. Returns null (and changes nothing) if the edit doesn't fit this page: an item to
     * remove is missing or differs, or an added id already exists. All-or-nothing, like a transaction.
     */
    fun apply(edit: InkEdit): PageItems? {
        val map = LinkedHashMap(byId)
        for (old in edit.removed) {
            if (map[old.id] != old) return null
            map.remove(old.id)
        }
        for (new in edit.added) {
            if (new.id in map) return null
            map[new.id] = new
        }
        return PageItems(map.values.sortedWith(PaintOrder), map)
    }

    companion object {
        val EMPTY = PageItems(emptyList(), emptyMap())

        fun of(items: Collection<InkItem>): PageItems {
            val map = LinkedHashMap<String, InkItem>()
            for (i in items) require(map.put(i.id, i) == null) { "duplicate item id" }
            return PageItems(map.values.sortedWith(PaintOrder), map)
        }
    }
}

/**
 * One undoable change on a page: [removed] items go, [added] items come. Every operation is expressed this
 * way: draw (add), erase (remove), shape correction and partial erase (replace), move, recolour.
 *
 * Its [inverse] swaps the two lists, so undo is exact by construction. The database stores the same edit
 * incrementally: delete the removed rows, insert the added ones.
 */
data class InkEdit(val removed: List<InkItem>, val added: List<InkItem>) {
    val isEmpty: Boolean get() = removed.isEmpty() && added.isEmpty()

    fun inverse() = InkEdit(added, removed)

    companion object {
        fun add(vararg items: InkItem) = InkEdit(emptyList(), items.toList())
        fun remove(items: List<InkItem>) = InkEdit(items, emptyList())
        fun replace(old: InkItem, new: InkItem) = InkEdit(listOf(old), listOf(new))
    }
}

/** An [InkEdit] on a specific page: what the editor's undo history stores. */
data class PageEdit(val pageId: String, val edit: InkEdit) {
    fun inverse() = PageEdit(pageId, edit.inverse())
}

/**
 * Undo/redo stacks with a size limit (oldest changes are forgotten first).
 * Generic like a C++ template `UndoHistory<E>`: [invert] says how to undo one entry.
 */
class UndoHistory<E : Any>(
    private val limit: Int = DEFAULT_LIMIT,
    private val isEmpty: (E) -> Boolean = { false },
    private val invert: (E) -> E,
) {
    private val undoStack = ArrayDeque<E>()
    private val redoStack = ArrayDeque<E>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Records a change that was just applied. A new change makes redo impossible. */
    fun record(edit: E) {
        if (isEmpty(edit)) return
        undoStack.addLast(edit)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    /** Returns what to apply to undo the last change, or null. */
    fun undo(): E? {
        val e = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(e)
        return invert(e)
    }

    /** Returns what to apply to redo the last undone change, or null. */
    fun redo(): E? {
        val e = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(e)
        return e
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        const val DEFAULT_LIMIT = 200

        fun forInk(limit: Int = DEFAULT_LIMIT) = UndoHistory<InkEdit>(limit, InkEdit::isEmpty, InkEdit::inverse)

        fun forPages(limit: Int = DEFAULT_LIMIT) =
            UndoHistory<PageEdit>(limit, { it.edit.isEmpty }, PageEdit::inverse)
    }
}
