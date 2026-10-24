package com.appname.notes.feature.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.SpanType
import com.appname.notes.core.domain.editor.EditHistory
import com.appname.notes.core.domain.editor.EditKind
import com.appname.notes.core.domain.editor.EditResult
import com.appname.notes.core.domain.editor.EditorState
import com.appname.notes.core.domain.editor.Selection
import com.appname.notes.core.domain.editor.activeStyles
import com.appname.notes.core.domain.editor.applyTextChange
import com.appname.notes.core.domain.editor.currentKind
import com.appname.notes.core.domain.editor.linkAtSelection
import com.appname.notes.core.domain.editor.removeLink
import com.appname.notes.core.domain.editor.setLink
import com.appname.notes.core.domain.editor.toggleChecked
import com.appname.notes.core.domain.editor.toggleKind
import com.appname.notes.core.domain.editor.toggleStyle

/**
 * Holds what the user is editing as Compose state and applies edits **synchronously** (a text field
 * must get its new value in the same frame, or the cursor jumps). The pure editing rules live in
 * `:core:domain`; this class only connects them to Compose and undo history.
 */
@Stable
class EditorController(
    initial: EditorState,
    initialTitle: String,
    private val newId: () -> String,
    now: () -> Long = System::currentTimeMillis,
) {
    var state: EditorState by mutableStateOf(initial)
        private set

    var title: String by mutableStateOf(initialTitle)
        private set

    /** Increases on every content change (not on cursor moves) — autosave watches this. */
    var version: Int by mutableIntStateOf(0)
        private set

    var canUndo: Boolean by mutableStateOf(false)
        private set

    var canRedo: Boolean by mutableStateOf(false)
        private set

    private val history = EditHistory(now = now)

    val textFieldValue: TextFieldValue
        get() = TextFieldValue(
            text = state.text,
            selection = TextRange(state.selection.start, state.selection.end),
            composition = state.composition?.let { TextRange(it.start, it.end) },
        )

    val activeStyles: Set<SpanType> get() = state.activeStyles()

    val currentKind: BlockKind get() = state.currentKind()

    val currentLinkUrl: String? get() = state.linkAtSelection()?.url

    val hasSelection: Boolean get() = !state.selection.collapsed

    fun onValueChange(value: TextFieldValue) {
        apply(
            state.applyTextChange(
                newText = value.text,
                newSelection = Selection(value.selection.start, value.selection.end),
                newComposition = value.composition?.let { Selection(it.start, it.end) },
                newId = newId,
            ),
        )
    }

    fun onTitleChange(value: String) {
        val clean = value.replace('\n', ' ')
        if (clean != title) {
            title = clean
            version++
        }
    }

    fun toggleStyle(type: SpanType) = apply(state.toggleStyle(type))

    fun toggleKind(kind: BlockKind) = apply(state.toggleKind(kind))

    fun toggleChecked(line: Int) = apply(state.toggleChecked(line))

    fun setLink(url: String) = apply(state.setLink(url))

    fun removeLink() = apply(state.removeLink())

    /**
     * Inserts [label] as a link to [url] at the cursor, followed by a space, and puts the cursor after it
     * (audio markers, FR-012). Undo removes it like any typing.
     */
    fun insertLink(label: String, url: String) {
        val v = textFieldValue
        val at = v.selection.max
        val before = v.text.substring(0, at)
        val prefix = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val start = at + prefix.length
        val end = start + label.length
        onValueChange(TextFieldValue(before + prefix + label + " " + v.text.substring(at), TextRange(start, end)))
        setLink(url)
        onValueChange(textFieldValue.copy(selection = TextRange(end + 1), composition = null))
    }

    /**
     * Inserts [text] at the cursor (replacing a selection), with a space on either side where needed, and puts the
     * cursor after it (voice typing). Undo removes it like any typing.
     */
    fun insertText(text: String) {
        if (text.isEmpty()) return
        val v = textFieldValue
        val before = v.text.substring(0, v.selection.min)
        val after = v.text.substring(v.selection.max)
        val prefix = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val suffix = if (after.isNotEmpty() && !after.first().isWhitespace()) " " else ""
        val inserted = prefix + text + suffix
        val cursor = before.length + prefix.length + text.length
        onValueChange(TextFieldValue(before + inserted + after, TextRange(cursor)))
    }

    /** Links in the note, e.g. to find the markers of a recording. */
    val links: List<String> get() = state.spans.mapNotNull { it.url }

    fun undo() {
        history.undo(state)?.let { replace(it) }
    }

    fun redo() {
        history.redo(state)?.let { replace(it) }
    }

    private fun replace(newState: EditorState) {
        state = newState
        version++
        refreshHistoryFlags()
    }

    private fun apply(result: EditResult) {
        if (result.kind != EditKind.NONE) {
            history.record(state, result.kind)
            version++
        }
        state = result.state
        refreshHistoryFlags()
    }

    private fun refreshHistoryFlags() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }
}
