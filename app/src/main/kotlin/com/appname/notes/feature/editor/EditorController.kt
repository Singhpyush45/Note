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
import com.appname.notes.core.domain.editor.inTable
import com.appname.notes.core.domain.editor.insertLinesAfterCursor
import com.appname.notes.core.domain.editor.insertTable
import com.appname.notes.core.domain.editor.tableAddColumn
import com.appname.notes.core.domain.editor.tableAddRow
import com.appname.notes.core.domain.editor.tableDeleteColumn
import com.appname.notes.core.domain.editor.tableDeleteRow
import com.appname.notes.core.domain.editor.tableNextCell

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

    // Tables (8c)
    val inTable: Boolean get() = state.inTable

    fun insertTable(rows: Int, cols: Int) = apply(state.insertTable(rows, cols, newId))

    fun tableAddRow() = apply(state.tableAddRow(newId))

    fun tableAddColumn() = apply(state.tableAddColumn(newId))

    fun tableDeleteRow() = apply(state.tableDeleteRow(newId))

    fun tableDeleteColumn() = apply(state.tableDeleteColumn(newId))

    fun tableNextCell() = apply(state.tableNextCell(newId))

    /** Whole lines under the cursor's line (a clipped web page). */
    fun insertLines(lines: List<Pair<BlockKind, com.appname.notes.core.domain.document.RichText>>) =
        apply(state.insertLinesAfterCursor(lines, newId))

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

    // ------------------------------------------------------------ live transcription (owner request)

    /** Where the words being transcribed right now sit in the text, and what was put there; -1 when none. */
    private var liveStart = -1
    private var liveText = ""
    private var liveBase: EditorState? = null

    /** Where the last voice sentence ended (to close it with a full stop when listening stops); -1: none. */
    private var voiceEnd = -1

    /**
     * Words being heard right now, written straight into the note at the cursor and replaced as they firm up
     * (word by word, like live captions). Not an undo step on its own; [voiceCommit] makes the sentence one.
     * [sentenceEnd] ("." or "।"): the words start a new sentence after a pause, so the previous one is closed with
     * it and these begin with a capital; null: they carry on the sentence before.
     */
    fun voiceLive(text: String, sentenceEnd: String? = null) = voiceWrite(text, commit = false, sentenceEnd)

    /** The finished words: replace the live ones and become one undo step. Empty text removes them. */
    fun voiceCommit(text: String, sentenceEnd: String? = null) = voiceWrite(text, commit = true, sentenceEnd)

    /** Listening stopped: closes the last voice sentence with [punctuation] if the cursor is still right after it. */
    fun voiceFinish(punctuation: String) {
        val s = state
        val at = voiceEnd
        voiceEnd = -1
        if (liveStart >= 0 || at <= 0 || at > s.text.length || s.selection.min != at || s.selection.max != at) return
        if (!s.text[at - 1].isLetterOrDigit()) return
        val result = s.applyTextChange(s.text.substring(0, at) + punctuation + s.text.substring(at), Selection(at + punctuation.length), null, newId)
        history.record(s, EditKind.FORMAT)
        state = result.state
        version++
        refreshHistoryFlags()
    }

    private fun voiceWrite(raw: String, commit: Boolean, sentenceEnd: String?) {
        val s = state
        // The user typed or moved things meanwhile: leave what is there and start again at the cursor.
        if (liveStart >= 0 && (liveStart + liveText.length > s.text.length || !s.text.regionMatches(liveStart, liveText, 0, liveText.length))) {
            liveStart = -1
        }
        if (liveStart < 0) {
            if (raw.isBlank()) return
            liveBase = s
            liveStart = s.selection.max
            liveText = ""
        }
        val before = s.text.substring(0, liveStart)
        val after = s.text.substring(liveStart + liveText.length)
        val trimmed = before.trimEnd(' ')
        val ended = trimmed.isEmpty() || trimmed.last() in ".!?\n।:"
        // After a pause the sentence before is closed here (only right after a word, never after a space).
        val close = sentenceEnd != null && !ended && before.last().isLetterOrDigit()
        val prefix = when {
            close -> sentenceEnd + " "
            before.isNotEmpty() && !before.last().isWhitespace() -> " "
            else -> ""
        }
        val body = raw.trim().let { if (ended || close) it.replaceFirstChar { ch -> ch.titlecase(java.util.Locale.ROOT) } else it }
        val content = if (body.isEmpty()) "" else prefix + body
        val newText = before + content + after
        val result = s.applyTextChange(newText, Selection(liveStart + content.length), null, newId)
        if (commit) {
            if (content.isNotEmpty()) liveBase?.let { history.record(it, EditKind.FORMAT) }
            voiceEnd = if (content.isEmpty()) voiceEnd else liveStart + content.length
            liveStart = -1
            liveText = ""
            liveBase = null
        } else {
            liveText = content
        }
        state = result.state
        version++
        refreshHistoryFlags()
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
