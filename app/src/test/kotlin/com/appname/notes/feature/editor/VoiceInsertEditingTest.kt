package com.appname.notes.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.core.domain.editor.EditorState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Voice-typed text goes in at the cursor as normal, editable, undoable text (owner brief §11). */
@RunWith(AndroidJUnit4::class)
class VoiceInsertEditingTest {

    private var n = 0
    private fun controller(text: String, cursor: Int = text.length, selectionEnd: Int = cursor): EditorController {
        val doc = NoteDocument(blocks = listOf(TextBlock("b0", text = RichText(text))))
        val c = EditorController(EditorState.fromDocument(doc) { "id${n++}" }, "Lecture", { "id${n++}" })
        c.onValueChange(c.textFieldValue.copy(selection = TextRange(cursor, selectionEnd)))
        return c
    }

    @Test
    fun `inserted between words with spaces on both sides, cursor after it`() {
        val c = controller("Buy milk", cursor = 3)
        c.insertText("fresh")
        assertThat(c.state.text).isEqualTo("Buy fresh milk")
        assertThat(c.state.selection.start).isEqualTo("Buy fresh".length)
    }

    @Test
    fun `at the end of the text and in an empty note`() {
        val c = controller("Notes:")
        c.insertText("Chapter four is on the exam.")
        assertThat(c.state.text).isEqualTo("Notes: Chapter four is on the exam.")
        val empty = controller("")
        empty.insertText("Hello")
        assertThat(empty.state.text).isEqualTo("Hello")
    }

    @Test
    fun `replaces the selection and is undone in one step`() {
        val c = controller("Call Ravi today", cursor = 5, selectionEnd = 9)
        c.insertText("Priya")
        assertThat(c.state.text).isEqualTo("Call Priya today")
        c.undo()
        assertThat(c.state.text).isEqualTo("Call Ravi today")
    }

    @Test
    fun `a transcript with a new line becomes two lines`() {
        val c = controller("")
        c.insertText("First line\nSecond line")
        assertThat(c.state.paragraphs).hasSize(2)
        assertThat(c.state.lineText(1)).isEqualTo("Second line")
    }
}
