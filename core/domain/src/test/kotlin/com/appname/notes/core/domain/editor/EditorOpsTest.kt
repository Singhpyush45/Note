package com.appname.notes.core.domain.editor

import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.SpanType
import com.appname.notes.core.domain.document.StyleSpan
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditorOpsTest {

    private var counter = 0
    private val newId = { "id${++counter}" }

    private fun state(
        text: String,
        kinds: List<BlockKind> = List(text.count { it == '\n' } + 1) { BlockKind.PARAGRAPH },
        spans: List<StyleSpan> = emptyList(),
        cursor: Int = text.length,
    ) = EditorState(
        text = text,
        paragraphs = kinds.mapIndexed { i, k -> ParagraphMeta("p$i", k) },
        spans = normalizeSpans(spans),
        selection = Selection(cursor),
    )

    /** Simulates typing [s] at the cursor. */
    private fun EditorState.type(s: String): EditorState {
        val at = selection.min
        val newText = text.substring(0, at) + s + text.substring(selection.max)
        return applyTextChange(newText, Selection(at + s.length), null, newId).state.also { assertThat(it.isValid()).isTrue() }
    }

    /** Simulates backspace at the cursor. */
    private fun EditorState.backspace(): EditorState {
        val at = selection.start
        val newText = text.removeRange(at - 1, at)
        return applyTextChange(newText, Selection(at - 1), null, newId).state.also { assertThat(it.isValid()).isTrue() }
    }

    @Test
    fun `typing at end of bold text continues bold`() {
        val s = state("Hello", spans = listOf(StyleSpan(0, 5, SpanType.BOLD))).type("!!")
        assertThat(s.text).isEqualTo("Hello!!")
        assertThat(s.spans).containsExactly(StyleSpan(0, 7, SpanType.BOLD))
    }

    @Test
    fun `typing before bold text does not become bold`() {
        val s = state("Hello", spans = listOf(StyleSpan(0, 5, SpanType.BOLD)), cursor = 0).type("Oh ")
        assertThat(s.spans).containsExactly(StyleSpan(3, 8, SpanType.BOLD))
    }

    @Test
    fun `typing at end of a link does not extend the link`() {
        val link = StyleSpan(0, 4, SpanType.LINK, "https://a.b")
        val s = state("site", spans = listOf(link)).type(" x")
        assertThat(s.spans).containsExactly(link)
    }

    @Test
    fun `typing inside a link stays inside it`() {
        val s = state("site", spans = listOf(StyleSpan(0, 4, SpanType.LINK, "u")), cursor = 2).type("XX")
        assertThat(s.spans).containsExactly(StyleSpan(0, 6, SpanType.LINK, "u"))
    }

    @Test
    fun `typing the same letter again is placed at the cursor`() {
        // "aa|" vs "a|a": the cursor decides where the insertion happened (matters for styles).
        val s = state("aa", spans = listOf(StyleSpan(1, 2, SpanType.BOLD)), cursor = 1)
            .applyTextChange("aaa", Selection(2), null, newId).state
        assertThat(s.spans).containsExactly(StyleSpan(2, 3, SpanType.BOLD))
    }

    @Test
    fun `deleting styled text shrinks and removes spans`() {
        var s = state("abcdef", spans = listOf(StyleSpan(2, 4, SpanType.ITALIC)), cursor = 4)
        s = s.backspace().backspace()
        assertThat(s.text).isEqualTo("abef")
        assertThat(s.spans).isEmpty()
    }

    @Test
    fun `replacing a selection keeps the style of its first character`() {
        val s = state("one two", spans = listOf(StyleSpan(4, 7, SpanType.BOLD)))
            .copy(selection = Selection(4, 7))
            .applyTextChange("one six", Selection(7), null, newId).state
        assertThat(s.spans).containsExactly(StyleSpan(4, 7, SpanType.BOLD))
    }

    @Test
    fun `enter in a bullet continues the list with a new id`() {
        val s = state("milk", kinds = listOf(BlockKind.BULLET)).type("\n")
        assertThat(s.paragraphs.map { it.kind }).containsExactly(BlockKind.BULLET, BlockKind.BULLET).inOrder()
        assertThat(s.paragraphs[0].id).isEqualTo("p0")
        assertThat(s.paragraphs[1].id).isNotEqualTo("p0")
    }

    @Test
    fun `enter in an empty list item leaves the list`() {
        val s = state("milk\n", kinds = listOf(BlockKind.BULLET, BlockKind.BULLET)).type("\n")
        assertThat(s.text).isEqualTo("milk\n")
        assertThat(s.paragraphs.map { it.kind }).containsExactly(BlockKind.BULLET, BlockKind.PARAGRAPH).inOrder()
    }

    @Test
    fun `enter after a heading starts a paragraph`() {
        val s = state("Title", kinds = listOf(BlockKind.HEADING1)).type("\n")
        assertThat(s.paragraphs.map { it.kind }).containsExactly(BlockKind.HEADING1, BlockKind.PARAGRAPH).inOrder()
    }

    @Test
    fun `enter at the start of a heading opens an empty paragraph above and keeps the heading`() {
        val s = state("Title", kinds = listOf(BlockKind.HEADING1), cursor = 0).type("\n")
        assertThat(s.text).isEqualTo("\nTitle")
        assertThat(s.paragraphs.map { it.kind }).containsExactly(BlockKind.PARAGRAPH, BlockKind.HEADING1).inOrder()
        assertThat(s.paragraphs[1].id).isEqualTo("p0")
    }

    @Test
    fun `new checklist items start unchecked`() {
        val checked = state("done", kinds = listOf(BlockKind.CHECKLIST)).let {
            it.copy(paragraphs = listOf(it.paragraphs[0].copy(checked = true)))
        }
        val s = checked.type("\n")
        assertThat(s.paragraphs[1].kind).isEqualTo(BlockKind.CHECKLIST)
        assertThat(s.paragraphs[1].checked).isFalse()
        assertThat(s.paragraphs[0].checked).isTrue()
    }

    @Test
    fun `backspace at the start of a list item removes the bullet but keeps the text`() {
        val s = state("a\nb", kinds = listOf(BlockKind.PARAGRAPH, BlockKind.BULLET), cursor = 2).backspace()
        assertThat(s.text).isEqualTo("a\nb")
        assertThat(s.paragraphs[1].kind).isEqualTo(BlockKind.PARAGRAPH)
        assertThat(s.selection).isEqualTo(Selection(2))
    }

    @Test
    fun `backspace at the start of a plain paragraph joins it to the line above`() {
        val s = state("a\nb", kinds = listOf(BlockKind.BULLET, BlockKind.PARAGRAPH), cursor = 2).backspace()
        assertThat(s.text).isEqualTo("ab")
        assertThat(s.paragraphs.map { it.kind }).containsExactly(BlockKind.BULLET)
    }

    @Test
    fun `forward delete at end of a line joins lines`() {
        val before = state("a\nb", kinds = listOf(BlockKind.PARAGRAPH, BlockKind.BULLET), cursor = 1)
        val s = before.applyTextChange("ab", Selection(1), null, newId).state
        assertThat(s.text).isEqualTo("ab")
        assertThat(s.lineCount).isEqualTo(1)
    }

    @Test
    fun `pasting several lines into a list makes several items`() {
        val s = state("", kinds = listOf(BlockKind.NUMBERED)).type("one\ntwo\nthree")
        assertThat(s.lineCount).isEqualTo(3)
        assertThat(s.paragraphs.map { it.kind }.toSet()).containsExactly(BlockKind.NUMBERED)
    }

    @Test
    fun `deleting across lines merges metadata correctly`() {
        val s = state("aa\nbb\ncc", kinds = listOf(BlockKind.HEADING1, BlockKind.BULLET, BlockKind.QUOTE))
            .copy(selection = Selection(1, 7))
            .applyTextChange("ac", Selection(1), null, newId).state
        assertThat(s.paragraphs).containsExactly(ParagraphMeta("p0", BlockKind.HEADING1))
        assertThat(s.isValid()).isTrue()
    }

    @Test
    fun `toggling bold on a selection adds then removes the style`() {
        val s = state("hello world").copy(selection = Selection(0, 5))
        val bold = s.toggleStyle(SpanType.BOLD).state
        assertThat(bold.spans).containsExactly(StyleSpan(0, 5, SpanType.BOLD))
        assertThat(bold.activeStyles()).contains(SpanType.BOLD)
        val plain = bold.toggleStyle(SpanType.BOLD).state
        assertThat(plain.spans).isEmpty()
    }

    @Test
    fun `unbolding the middle of bold text splits the span`() {
        val s = state("abcdef", spans = listOf(StyleSpan(0, 6, SpanType.BOLD))).copy(selection = Selection(2, 4))
        assertThat(s.toggleStyle(SpanType.BOLD).state.spans)
            .containsExactly(StyleSpan(0, 2, SpanType.BOLD), StyleSpan(4, 6, SpanType.BOLD)).inOrder()
    }

    @Test
    fun `partly bold selection becomes fully bold`() {
        val s = state("abcdef", spans = listOf(StyleSpan(0, 2, SpanType.BOLD))).copy(selection = Selection(1, 5))
        assertThat(s.toggleStyle(SpanType.BOLD).state.spans).containsExactly(StyleSpan(0, 5, SpanType.BOLD))
    }

    @Test
    fun `bold with no selection applies to the next typed text only`() {
        val armed = state("ab").toggleStyle(SpanType.BOLD).state
        assertThat(armed.activeStyles()).contains(SpanType.BOLD)
        val typed = armed.type("cd")
        assertThat(typed.spans).containsExactly(StyleSpan(2, 4, SpanType.BOLD))
    }

    @Test
    fun `turning bold off at the end of bold text stops it`() {
        val s = state("ab", spans = listOf(StyleSpan(0, 2, SpanType.BOLD)))
        val off = s.toggleStyle(SpanType.BOLD).state
        assertThat(off.activeStyles()).doesNotContain(SpanType.BOLD)
        assertThat(off.type("c").spans).containsExactly(StyleSpan(0, 2, SpanType.BOLD))
    }

    @Test
    fun `moving the cursor drops armed styles`() {
        val armed = state("abc").toggleStyle(SpanType.ITALIC).state
        val moved = armed.applyTextChange("abc", Selection(1), null, newId).state
        assertThat(moved.pendingStyles).isNull()
    }

    @Test
    fun `links can be set, found and removed`() {
        val s = state("see docs").copy(selection = Selection(4, 8)).setLink("https://x.y").state
        assertThat(s.spans).containsExactly(StyleSpan(4, 8, SpanType.LINK, "https://x.y"))
        val inside = s.copy(selection = Selection(6))
        assertThat(inside.linkAtSelection()?.url).isEqualTo("https://x.y")
        assertThat(inside.removeLink().state.spans).isEmpty()
    }

    @Test
    fun `kind toggle applies to all selected lines and toggles back`() {
        val s = state("a\nb\nc").copy(selection = Selection(0, 3))
        val bullets = s.toggleKind(BlockKind.BULLET).state
        assertThat(bullets.paragraphs.map { it.kind })
            .containsExactly(BlockKind.BULLET, BlockKind.BULLET, BlockKind.PARAGRAPH).inOrder()
        assertThat(bullets.toggleKind(BlockKind.BULLET).state.paragraphs.map { it.kind }.toSet())
            .containsExactly(BlockKind.PARAGRAPH)
    }

    @Test
    fun `checklist items can be ticked`() {
        val s = state("task", kinds = listOf(BlockKind.CHECKLIST))
        assertThat(s.toggleChecked(0).state.paragraphs[0].checked).isTrue()
        assertThat(state("x").toggleChecked(0).kind).isEqualTo(EditKind.NONE)
    }

    @Test
    fun `selection-only changes are not edits`() {
        val r = state("abc").applyTextChange("abc", Selection(1, 2), null, newId)
        assertThat(r.kind).isEqualTo(EditKind.NONE)
        assertThat(r.state.selection).isEqualTo(Selection(1, 2))
    }

    @Test
    fun `random edits never break invariants`() {
        val random = java.util.Random(42)
        val alphabet = "ab \n*"
        var s = state("start", kinds = listOf(BlockKind.BULLET), spans = listOf(StyleSpan(0, 3, SpanType.BOLD)))
        repeat(2_000) {
            val len = s.text.length
            val a = random.nextInt(len + 1)
            val b = (a + random.nextInt(3)).coerceAtMost(len)
            val insert = buildString { repeat(random.nextInt(3)) { append(alphabet[random.nextInt(alphabet.length)]) } }
            s = s.copy(selection = Selection(a, b))
            when (random.nextInt(4)) {
                0 -> s = s.toggleStyle(SpanType.entries.filter { it != SpanType.LINK }.random(kotlin.random.Random(it))).state
                1 -> s = s.toggleKind(BlockKind.entries[random.nextInt(BlockKind.entries.size)]).state
                else -> {
                    val newText = s.text.substring(0, a) + insert + s.text.substring(b)
                    s = s.applyTextChange(newText, Selection(a + insert.length), null, newId).state
                }
            }
            assertThat(s.isValid()).isTrue()
            assertThat(EditorState.fromDocument(s.toDocument(), newId).text).isEqualTo(s.text)
        }
    }
}
