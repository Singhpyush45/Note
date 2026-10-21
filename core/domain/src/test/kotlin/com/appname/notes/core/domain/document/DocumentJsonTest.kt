package com.appname.notes.core.domain.document

import com.appname.notes.core.domain.editor.EditorState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocumentJsonTest {

    private val doc = NoteDocument(
        blocks = listOf(
            TextBlock("a", BlockKind.HEADING1, RichText("Lecture 4")),
            TextBlock("b", BlockKind.CHECKLIST, RichText("Revise chain rule", listOf(StyleSpan(7, 17, SpanType.BOLD))), checked = true),
            TextBlock("c", BlockKind.PARAGRAPH, RichText("see notes", listOf(StyleSpan(4, 9, SpanType.LINK, "https://e.x")))),
        ),
    )

    @Test
    fun `round trip keeps everything`() {
        val json = DocumentJson.encode(doc)
        assertThat(json).contains("\"type\":\"text\"")
        assertThat(json).contains("\"kind\":\"checklist\"")
        assertThat(DocumentJson.decode(json)).isEqualTo(doc)
    }

    @Test
    fun `unknown block types are preserved exactly and make the note read-only`() {
        val json = """{"schemaVersion":2,"blocks":[
            {"type":"text","id":"a","kind":"paragraph","text":{"text":"hi"}},
            {"type":"ink","id":"z","strokes":[1,2,3],"brush":{"w":2}}]}"""
        val decoded = DocumentJson.decode(json)
        assertThat(decoded.isFullyEditable).isFalse()
        val unknown = decoded.blocks[1] as UnknownBlock
        assertThat(unknown.type).isEqualTo("ink")
        val reencoded = DocumentJson.decode(DocumentJson.encode(decoded))
        assertThat(reencoded).isEqualTo(decoded)
    }

    @Test
    fun `unknown kinds and fields from newer versions degrade gracefully`() {
        val json = """{"blocks":[{"type":"text","id":"a","kind":"callout","colour":"red","text":{"text":"x","spans":[]}}]}"""
        val block = DocumentJson.decode(json).blocks.single() as TextBlock
        assertThat(block.kind).isEqualTo(BlockKind.PARAGRAPH)
        assertThat(block.text.text).isEqualTo("x")
    }

    @Test
    fun `editor state converts to and from documents losslessly`() {
        var n = 0
        val state = EditorState.fromDocument(doc) { "new${n++}" }
        assertThat(state.text).isEqualTo("Lecture 4\nRevise chain rule\nsee notes")
        assertThat(state.toDocument()).isEqualTo(doc)
    }

    @Test
    fun `preview keeps list markers and skips empty lines`() {
        val d = NoteDocument(
            blocks = listOf(
                TextBlock("1", BlockKind.CHECKLIST, RichText("Milk"), checked = true),
                TextBlock("2", BlockKind.CHECKLIST, RichText("Eggs")),
                TextBlock("3", BlockKind.PARAGRAPH, RichText("   ")),
                TextBlock("4", BlockKind.NUMBERED, RichText("first")),
                TextBlock("5", BlockKind.NUMBERED, RichText("second")),
                TextBlock("6", BlockKind.BULLET, RichText("dot")),
            ),
        )
        assertThat(d.preview()).isEqualTo("☑ Milk\n☐ Eggs\n1. first\n2. second\n• dot")
    }

    @Test
    fun `plain text and blank detection`() {
        assertThat(doc.toPlainText()).isEqualTo("Lecture 4\nRevise chain rule\nsee notes")
        assertThat(NoteDocument.empty { "x" }.isBlank()).isTrue()
        assertThat(doc.isBlank()).isFalse()
        assertThat(doc.usedSpanTypes()).containsExactly(SpanType.BOLD, SpanType.LINK)
    }
}
