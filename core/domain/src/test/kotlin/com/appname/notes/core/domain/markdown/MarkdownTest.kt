package com.appname.notes.core.domain.markdown

import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.SpanType
import com.appname.notes.core.domain.document.StyleSpan
import com.appname.notes.core.domain.document.TextBlock
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarkdownTest {

    private var n = 0
    private val newId = { "i${n++}" }

    private fun block(kind: BlockKind, text: String, spans: List<StyleSpan> = emptyList(), checked: Boolean = false) =
        TextBlock(newId(), kind, RichText(text, spans), checked)

    private val doc = NoteDocument(
        blocks = listOf(
            block(BlockKind.HEADING1, "Plan"),
            block(BlockKind.PARAGRAPH, "Read *carefully* and bold", listOf(StyleSpan(21, 25, SpanType.BOLD))),
            block(BlockKind.BULLET, "milk"),
            block(BlockKind.BULLET, "eggs", listOf(StyleSpan(0, 4, SpanType.ITALIC))),
            block(BlockKind.NUMBERED, "first"),
            block(BlockKind.NUMBERED, "second"),
            block(BlockKind.CHECKLIST, "done", checked = true),
            block(BlockKind.CHECKLIST, "todo"),
            block(BlockKind.QUOTE, "wise words"),
            block(BlockKind.HEADING3, "Small"),
            block(
                BlockKind.PARAGRAPH,
                "code and link and strike",
                listOf(
                    StyleSpan(0, 4, SpanType.CODE),
                    StyleSpan(9, 13, SpanType.LINK, "https://ex.com/a_(b)"),
                    StyleSpan(18, 24, SpanType.STRIKETHROUGH),
                ),
            ),
            block(BlockKind.PARAGRAPH, "1. not a list"),
            block(BlockKind.PARAGRAPH, "# not a heading"),
        ),
    )

    private fun normalized(d: NoteDocument) = d.textBlocks.map { Triple(it.kind, it.text, it.checked) }

    @Test
    fun `export produces readable markdown`() {
        val md = Markdown.export("My note", doc).text
        assertThat(md).startsWith("# My note\n\n## Plan\n")
        assertThat(md).contains("Read \\*carefully\\* and **bold**")
        assertThat(md).contains("- milk\n- *eggs*\n1. first\n2. second\n- [x] done\n- [ ] todo\n")
        assertThat(md).contains("> wise words")
        assertThat(md).contains("`code` and [link](https://ex.com/a_(b%29) and ~~strike~~")
        assertThat(md).contains("1\\. not a list")
        assertThat(md).contains("\\# not a heading")
    }

    @Test
    fun `export then import gives back the same note`() {
        val md = Markdown.export("My note", doc).text
        val imported = Markdown.import(md, "fallback", newId)
        assertThat(imported.title).isEqualTo("My note")
        assertThat(normalized(imported.document)).isEqualTo(normalized(doc))
    }

    @Test
    fun `underline is reported as lost`() {
        val underlined = NoteDocument(blocks = listOf(block(BlockKind.PARAGRAPH, "u", listOf(StyleSpan(0, 1, SpanType.UNDERLINE)))))
        val result = Markdown.export("", underlined)
        assertThat(result.lost).containsExactly(LostFeature.UNDERLINE)
        assertThat(result.text.trim()).isEqualTo("u")
    }

    @Test
    fun `nested and adjacent styles round trip`() {
        val d = NoteDocument(
            blocks = listOf(
                block(
                    BlockKind.PARAGRAPH,
                    "bold both bold then strike",
                    listOf(
                        StyleSpan(0, 14, SpanType.BOLD),
                        StyleSpan(5, 9, SpanType.ITALIC),
                        StyleSpan(20, 26, SpanType.STRIKETHROUGH),
                    ),
                ),
            ),
        )
        val md = Markdown.export("", d).text.trim()
        assertThat(md).isEqualTo("**bold *both* bold** then ~~strike~~")
        assertThat(normalized(Markdown.import(md, "", newId).document)).isEqualTo(normalized(d))
    }

    @Test
    fun `crossing styles export as balanced markers`() {
        // Bold [ab cd] crossing italic [cd ef]: Markdown can't express crossing, so styles are split.
        val d = NoteDocument(
            blocks = listOf(
                block(BlockKind.PARAGRAPH, "abcdef", listOf(StyleSpan(0, 4, SpanType.BOLD), StyleSpan(2, 6, SpanType.ITALIC))),
            ),
        )
        val md = Markdown.export("", d).text.trim()
        assertThat(md.count { it == '*' } % 2).isEqualTo(0)
        assertThat(md.replace("*", "")).isEqualTo("abcdef")
    }

    @Test
    fun `commonmark emphasis edge cases`() {
        fun styles(s: String) = Markdown.parseInline(s).let { r -> r.text to r.spans.map { Triple(it.start, it.end, it.style) } }
        assertThat(styles("***both***")).isEqualTo(
            "both" to listOf(Triple(0, 4, SpanType.BOLD), Triple(0, 4, SpanType.ITALIC)),
        )
        assertThat(styles("a * b * c").first).isEqualTo("a * b * c") // spaced stars aren't emphasis
        assertThat(styles("snake_case_name").first).isEqualTo("snake_case_name")
        assertThat(styles("**unclosed").first).isEqualTo("**unclosed")
        assertThat(styles("~single~ and ~~double~~")).isEqualTo(
            "~single~ and double" to listOf(Triple(13, 19, SpanType.STRIKETHROUGH)),
        )
        assertThat(styles("[**bold link**](https://x.y)").second)
            .containsExactly(Triple(0, 9, SpanType.BOLD), Triple(0, 9, SpanType.LINK))
    }

    @Test
    fun `import handles common markdown from other apps`() {
        val md = """
            Shopping

            * apples
            + pears
            3) third
            - [X] paid
            > quoted
            ## Section
            some __bold__ and _it_ and snake_case_name and \*literal\*
        """.trimIndent()
        val imported = Markdown.import(md, "From file", newId)
        assertThat(imported.title).isEqualTo("From file")
        val blocks = imported.document.textBlocks
        assertThat(blocks.map { it.kind }).containsExactly(
            BlockKind.PARAGRAPH, BlockKind.BULLET, BlockKind.BULLET, BlockKind.NUMBERED,
            BlockKind.CHECKLIST, BlockKind.QUOTE, BlockKind.HEADING1, BlockKind.PARAGRAPH,
        ).inOrder()
        assertThat(blocks[4].checked).isTrue()
        val last = blocks.last().text
        assertThat(last.text).isEqualTo("some bold and it and snake_case_name and *literal*")
        assertThat(last.spans.map { it.style }).containsExactly(SpanType.BOLD, SpanType.ITALIC)
    }

    @Test
    fun `unmatched markers stay literal`() {
        assertThat(Markdown.parseInline("2 * 3 = 6 and a [bracket").text).isEqualTo("2 * 3 = 6 and a [bracket")
    }

    @Test
    fun `empty input gives one empty block`() {
        val imported = Markdown.import("", "Empty", newId)
        assertThat(imported.document.textBlocks).hasSize(1)
        assertThat(imported.title).isEqualTo("Empty")
    }

    @Test
    fun `plain text sharing uses list symbols`() {
        val text = Markdown.plainText("T", doc)
        assertThat(text).startsWith("T\n\nPlan\n")
        assertThat(text).contains("• milk\n• eggs\n1. first\n2. second\n☑ done\n☐ todo\n“wise words”")
    }
}
