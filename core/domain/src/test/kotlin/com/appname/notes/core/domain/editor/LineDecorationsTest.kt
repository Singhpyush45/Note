package com.appname.notes.core.domain.editor

import com.appname.notes.core.domain.document.BlockKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LineDecorationsTest {

    private fun state(text: String, vararg kinds: BlockKind, checked: Set<Int> = emptySet()) = EditorState(
        text = text,
        paragraphs = kinds.mapIndexed { i, k -> ParagraphMeta("p$i", k, checked = i in checked) },
    )

    @Test
    fun `prefixes for each kind with numbering restarting after other lines`() {
        val s = state(
            "a\nb\nc\nd\ne\nf",
            BlockKind.NUMBERED, BlockKind.NUMBERED, BlockKind.PARAGRAPH, BlockKind.NUMBERED, BlockKind.CHECKLIST, BlockKind.BULLET,
            checked = setOf(4),
        )
        val d = LineDecorations.of(s)
        assertThat(d.prefixes).containsExactly("1.  ", "2.  ", "", "1.  ", LineDecorations.CHECKED, LineDecorations.BULLET).inOrder()
        assertThat(LineDecorations.displayText(s, d)).isEqualTo("1.  a\n2.  b\nc\n1.  d\n☑  e\n•  f")
        assertThat(d.transformedLength).isEqualTo(LineDecorations.displayText(s, d).length)
    }

    @Test
    fun `mapping round-trips every original offset and stays in bounds`() {
        val s = state("hello\n\nworld!\nx", BlockKind.BULLET, BlockKind.CHECKLIST, BlockKind.PARAGRAPH, BlockKind.QUOTE)
        val d = LineDecorations.of(s)
        for (o in 0..s.text.length) {
            val t = d.originalToTransformed(o)
            assertThat(t).isIn(0..d.transformedLength)
            assertThat(d.transformedToOriginal(t)).isEqualTo(o)
        }
        for (t in 0..d.transformedLength) {
            assertThat(d.transformedToOriginal(t)).isIn(0..s.text.length)
        }
    }

    @Test
    fun `positions inside a prefix map to the start of the line text`() {
        val s = state("ab\ncd", BlockKind.PARAGRAPH, BlockKind.BULLET)
        val d = LineDecorations.of(s)
        // "ab\n•  cd": transformed offsets 3..5 are the bullet prefix of line 1.
        assertThat(d.prefixRange(1)).isEqualTo(3 until 6)
        for (t in 3..6) assertThat(d.transformedToOriginal(t)).isEqualTo(3)
        assertThat(d.lineWithPrefixAt(4)).isEqualTo(1)
        assertThat(d.lineWithPrefixAt(1)).isNull()
    }

    @Test
    fun `out of range offsets are clamped instead of crashing`() {
        val d = LineDecorations.of(state("x", BlockKind.BULLET))
        assertThat(d.originalToTransformed(99)).isEqualTo(d.transformedLength)
        assertThat(d.transformedToOriginal(-5)).isEqualTo(0)
    }
}
