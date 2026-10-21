package com.appname.notes.core.domain.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The body of a note (ADR-0007): an ordered list of blocks. Stored as JSON in the database.
 *
 * Invariant: the text of a [TextBlock] never contains a newline — one block is one line.
 */
@Serializable
data class NoteDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val blocks: List<@Serializable(with = BlockSerializer::class) Block> = emptyList(),
) {
    /** False when the note contains blocks from a newer app version that this version can't edit. */
    val isFullyEditable: Boolean get() = blocks.none { it is UnknownBlock }

    val textBlocks: List<TextBlock> get() = blocks.filterIsInstance<TextBlock>()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun empty(newId: () -> String): NoteDocument =
            NoteDocument(blocks = listOf(TextBlock(id = newId())))
    }
}

sealed interface Block {
    val id: String
}

@Serializable
data class TextBlock(
    override val id: String,
    val kind: BlockKind = BlockKind.PARAGRAPH,
    val text: RichText = RichText(""),
    /** Only meaningful for [BlockKind.CHECKLIST]. */
    val checked: Boolean = false,
) : Block

/** A block type this app version doesn't know. Kept exactly as read so saving never loses it. */
data class UnknownBlock(override val id: String, val type: String, val raw: JsonObject) : Block

@Serializable
enum class BlockKind {
    @SerialName("paragraph") PARAGRAPH,
    @SerialName("heading1") HEADING1,
    @SerialName("heading2") HEADING2,
    @SerialName("heading3") HEADING3,
    @SerialName("bullet") BULLET,
    @SerialName("numbered") NUMBERED,
    @SerialName("checklist") CHECKLIST,
    @SerialName("quote") QUOTE,
    ;

    val isList: Boolean get() = this == BULLET || this == NUMBERED || this == CHECKLIST
    val isHeading: Boolean get() = this == HEADING1 || this == HEADING2 || this == HEADING3
}

/** Text plus inline styles. Span offsets are relative to [text] and half-open: `[start, end)`. */
@Serializable
data class RichText(
    val text: String,
    val spans: List<StyleSpan> = emptyList(),
)

@Serializable
data class StyleSpan(
    val start: Int,
    val end: Int,
    val style: SpanType,
    /** Target for [SpanType.LINK]; null otherwise. */
    val url: String? = null,
) {
    init {
        require(start in 0..end) { "Invalid span [$start, $end)" }
    }

    fun overlaps(from: Int, to: Int): Boolean = start < to && from < end

    fun sameStyleAs(other: StyleSpan): Boolean = style == other.style && url == other.url
}

@Serializable
enum class SpanType {
    @SerialName("bold") BOLD,
    @SerialName("italic") ITALIC,
    @SerialName("underline") UNDERLINE,
    @SerialName("strikethrough") STRIKETHROUGH,
    @SerialName("code") CODE,
    @SerialName("link") LINK,
    ;

    /** Typing right after a styled character continues the style — except for links. */
    val extendsWhileTyping: Boolean get() = this != LINK
}
