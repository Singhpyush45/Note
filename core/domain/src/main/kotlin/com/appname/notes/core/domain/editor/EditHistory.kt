package com.appname.notes.core.domain.editor

/**
 * Bounded undo/redo of editor states (SRS 6.3 / 8.3).
 *
 * Keystrokes are grouped: consecutive typing (or consecutive deleting) within [groupWindowMillis]
 * is one undo step, like most editors. Formatting and line structure changes are always their own step.
 * Memory is bounded by [maxSteps]; autosave is independent of history (SRS 6.3).
 */
class EditHistory(
    private val maxSteps: Int = 200,
    private val groupWindowMillis: Long = 1_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val undoStack = ArrayDeque<EditorState>()
    private val redoStack = ArrayDeque<EditorState>()
    private var lastKind: EditKind? = null
    private var lastTime = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Call with the state *before* a change of [kind] is applied. */
    fun record(before: EditorState, kind: EditKind) {
        if (kind == EditKind.NONE) return
        val time = now()
        val groupable = kind == EditKind.TYPING || kind == EditKind.DELETING
        val sameGroup = groupable && kind == lastKind && time - lastTime <= groupWindowMillis
        if (!sameGroup) {
            undoStack.addLast(before.forHistory())
            if (undoStack.size > maxSteps) undoStack.removeFirst()
        }
        redoStack.clear()
        lastKind = kind
        lastTime = time
    }

    fun undo(current: EditorState): EditorState? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current.forHistory())
        lastKind = null
        return previous
    }

    fun redo(current: EditorState): EditorState? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current.forHistory())
        lastKind = null
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastKind = null
    }

    private fun EditorState.forHistory() = copy(composition = null, pendingStyles = null)
}
