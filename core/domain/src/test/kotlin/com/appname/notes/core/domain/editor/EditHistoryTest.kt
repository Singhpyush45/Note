package com.appname.notes.core.domain.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditHistoryTest {

    private var time = 0L
    private val history = EditHistory(maxSteps = 3, groupWindowMillis = 1_000, now = { time })

    private fun s(text: String) = EditorState(text, listOf(ParagraphMeta("p")))

    @Test
    fun `quick typing is one undo step`() {
        history.record(s(""), EditKind.TYPING)
        time += 200
        history.record(s("a"), EditKind.TYPING)
        time += 200
        history.record(s("ab"), EditKind.TYPING)
        assertThat(history.undo(s("abc"))).isEqualTo(s(""))
        assertThat(history.canUndo).isFalse()
    }

    @Test
    fun `a pause or a different kind of edit starts a new step`() {
        history.record(s(""), EditKind.TYPING)
        time += 5_000
        history.record(s("a"), EditKind.TYPING)
        history.record(s("ab"), EditKind.FORMAT)
        assertThat(history.undo(s("ab*"))).isEqualTo(s("ab"))
        assertThat(history.undo(s("ab"))).isEqualTo(s("a"))
        assertThat(history.undo(s("a"))).isEqualTo(s(""))
    }

    @Test
    fun `redo restores undone state and a new edit clears redo`() {
        history.record(s("a"), EditKind.FORMAT)
        val undone = history.undo(s("b"))!!
        assertThat(history.redo(undone)).isEqualTo(s("b"))
        history.undo(s("b"))
        history.record(s("a"), EditKind.FORMAT)
        assertThat(history.canRedo).isFalse()
    }

    @Test
    fun `history is bounded`() {
        repeat(10) { history.record(s("$it"), EditKind.FORMAT) }
        var count = 0
        var cur = s("x")
        while (true) { cur = history.undo(cur) ?: break; count++ }
        assertThat(count).isEqualTo(3)
    }

    @Test
    fun `non edits are ignored`() {
        history.record(s("a"), EditKind.NONE)
        assertThat(history.canUndo).isFalse()
    }
}
