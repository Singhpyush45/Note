package com.appname.notes.core.domain.editor

import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.markdown.Markdown
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tables in typed notes (8c): editing rules, the drawn grid and Markdown. */
class TableOpsTest {
    private var n = 0
    private val newId = { "n${n++}" }

    private fun type(s: EditorState, newText: String, cursor: Int) = s.applyTextChange(newText, Selection(cursor), null, newId).state

    @Test
    fun `a table is inserted on an empty line with a line under it, and Enter adds a row`() {
        val empty = EditorState("", listOf(ParagraphMeta("a")))
        var s = empty.insertTable(2, 3, newId).state
        assertThat(s.text).isEqualTo("\t\t\n\t\t\n")
        assertThat(s.paragraphs.map { it.kind }).containsExactly(BlockKind.TABLE_ROW, BlockKind.TABLE_ROW, BlockKind.PARAGRAPH).inOrder()
        // Type in the first cell, then Enter: a new empty row under it, the cursor in its first cell.
        s = type(s, "x\t\t\n\t\t\n", 1)
        val enter = s.text.substring(0, 1) + "\n" + s.text.substring(1)
        s = type(s, enter, 2)
        assertThat(s.text).isEqualTo("x\t\t\n\t\t\n\t\t\n")
        assertThat(s.lineOf(s.selection.start)).isEqualTo(1)
        // Next cell, add a column, delete a row.
        s = s.tableNextCell(newId).state
        assertThat(s.selection.start).isEqualTo(s.lineStart(1) + 1)
        s = s.tableAddColumn(newId).state
        assertThat(s.lineText(0)).isEqualTo("x\t\t\t")
        s = s.tableDeleteRow(newId).state
        assertThat(s.tableLines(0)).isEqualTo(0..1)
    }

    @Test
    fun `rows are drawn as an aligned grid and every offset maps back`() {
        val s = EditorState("Name\tAge\nRahul\t21", listOf(ParagraphMeta("a", BlockKind.TABLE_ROW), ParagraphMeta("b", BlockKind.TABLE_ROW)))
        val d = LineDecorations.of(s)
        assertThat(LineDecorations.displayText(s, d)).isEqualTo("│ Name  │ Age │\n│ Rahul │ 21  │")
        assertThat(d.transformedLength).isEqualTo(LineDecorations.displayText(s, d).length)
        for (o in 0..s.text.length) assertThat(d.transformedToOriginal(d.originalToTransformed(o))).isEqualTo(o)
        for (t in 0..d.transformedLength) assertThat(d.transformedToOriginal(t)).isIn(0..s.text.length)
    }

    @Test
    fun `tables survive Markdown export and import`() {
        val s = EditorState("Name\tAge\nRahul\t21", listOf(ParagraphMeta("a", BlockKind.TABLE_ROW), ParagraphMeta("b", BlockKind.TABLE_ROW)))
        val md = Markdown.export("", s.toDocument()).text
        assertThat(md).isEqualTo("| Name | Age |\n| --- | --- |\n| Rahul | 21 |\n")
        val back = Markdown.import(md, "t", newId).document.textBlocks
        assertThat(back.map { it.kind }).containsExactly(BlockKind.TABLE_ROW, BlockKind.TABLE_ROW).inOrder()
        assertThat(back.map { it.text.text }).containsExactly("Name\tAge", "Rahul\t21").inOrder()
    }
}
