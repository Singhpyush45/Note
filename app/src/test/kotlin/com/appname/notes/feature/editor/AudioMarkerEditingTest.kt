package com.appname.notes.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.SpanType
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.core.domain.editor.EditorState
import com.appname.notes.core.domain.model.AttachmentId
import com.appname.notes.core.domain.model.AudioMarker
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** FR-012 timestamped markers: written into the text as links, undoable like typing. */
@RunWith(AndroidJUnit4::class)
class AudioMarkerEditingTest {

    private var n = 0
    private fun controller(text: String): EditorController {
        val doc = NoteDocument(blocks = listOf(TextBlock("b0", text = RichText(text))))
        val c = EditorController(EditorState.fromDocument(doc) { "id${n++}" }, "Lecture", { "id${n++}" })
        c.onValueChange(c.textFieldValue.copy(selection = TextRange(text.length)))
        return c
    }

    @Test
    fun `a marker is inserted at the cursor as a link, with spaces around it`() {
        val c = controller("Important")
        val m = AudioMarker(AttachmentId("rec"), 760_000)
        c.insertLink(m.label, m.url)
        assertThat(c.state.text).isEqualTo("Important 12:40 ")
        val link = c.state.spans.single { it.style == SpanType.LINK }
        assertThat(c.state.text.substring(link.start, link.end)).isEqualTo("12:40")
        assertThat(AudioMarker.parse(link.url!!)).isEqualTo(m)
        assertThat(c.state.selection.start).isEqualTo(c.state.text.length) // typing continues after it
        assertThat(c.links).containsExactly(m.url)
    }

    @Test
    fun `an empty note gets the marker without a leading space`() {
        val c = controller("")
        c.insertLink("0:05", AudioMarker(AttachmentId("rec"), 5_000).url)
        assertThat(c.state.text).isEqualTo("0:05 ")
    }

    @Test
    fun `undo removes the marker`() {
        val c = controller("Notes")
        c.insertLink("1:00", AudioMarker(AttachmentId("rec"), 60_000).url)
        while (c.canUndo && c.state.text != "Notes") c.undo()
        assertThat(c.state.text).isEqualTo("Notes")
        assertThat(c.links).isEmpty()
    }
}
