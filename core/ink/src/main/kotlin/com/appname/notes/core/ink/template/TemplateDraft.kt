package com.appname.notes.core.ink.template

/** The header a custom template can have. */
enum class HeaderStyle { NONE, TITLE, TITLE_AND_DATE }

/**
 * What the custom-template editor edits (owner brief §4: create, preview, edit): page size, ruling, colours,
 * margin and header. Printed parts that came with a copied built-in template ([extras], e.g. a planner's
 * tables) are kept and move/stretch with the page size. [toLayout] gives the design to store.
 */
data class TemplateDraft(
    val format: PageFormat = PageFormat.A4_PORTRAIT,
    val ruling: Ruling = Ruling.LINES,
    val spacing: Float = 30f,
    val lineColor: Int = TemplateColors.RULE,
    val paperColor: Int = TemplateColors.PAPER,
    val margin: Boolean = true,
    val header: HeaderStyle = HeaderStyle.NONE,
    val extras: List<TemplateComponent> = emptyList(),
    /** The page size [extras] were made for. */
    val extrasWidth: Float = format.width,
    val extrasHeight: Float = format.height,
) {
    /** Whether the margin line applies (only lined paper has one). */
    val marginApplies: Boolean get() = ruling == Ruling.LINES

    fun toLayout(): TemplateLayout {
        val w = format.width
        val h = format.height
        val sx = w / extrasWidth
        val sy = h / extrasHeight
        val headerParts = headerComponents(header, w)
        val s = spacing.coerceIn(MIN_SPACING, MAX_SPACING)
        val top = when {
            header != HeaderStyle.NONE -> HEADER_BOTTOM
            ruling == Ruling.LINES -> s * 3
            else -> 0f
        }
        return TemplateLayout(
            width = w,
            height = h,
            paperColor = paperColor,
            paper = PaperStyle(
                ruling = ruling,
                spacing = s,
                lineColor = lineColor,
                marginX = if (margin && marginApplies) MARGIN_X * w / PageFormat.A4_PORTRAIT.width else null,
                top = top,
            ),
            components = headerParts + extras.map { it.scaled(sx, sy) },
        )
    }

    companion object {
        const val MIN_SPACING = 12f
        const val MAX_SPACING = 48f
        private const val M = 48f
        private const val MARGIN_X = 64f
        private const val HEADER_BOTTOM = 120f

        /** A new template starts as a lined page. */
        val NEW = TemplateDraft()

        /** The editable form of [layout]; a draft made from a layout gives that layout back unchanged. */
        fun from(layout: TemplateLayout): TemplateDraft {
            val format = PageFormat.closest(layout.width, layout.height)
            val sized = layout.resized(format.width, format.height)
            // A header is recognised when the layout has exactly the parts the editor makes for it.
            val header = listOf(HeaderStyle.TITLE_AND_DATE, HeaderStyle.TITLE).firstOrNull { style ->
                sized.components.containsAll(headerComponents(style, sized.width))
            } ?: HeaderStyle.NONE
            val headerParts = headerComponents(header, sized.width)
            return TemplateDraft(
                format = format,
                ruling = sized.paper.ruling,
                spacing = sized.paper.spacing,
                lineColor = sized.paper.lineColor,
                paperColor = sized.paperColor,
                margin = sized.paper.marginX != null,
                header = header,
                extras = sized.components.filterNot { it in headerParts },
                extrasWidth = sized.width,
                extrasHeight = sized.height,
            )
        }

        /** "Title ____" (and "Date ____") across the top, with a line under it. */
        fun headerComponents(style: HeaderStyle, width: Float): List<TemplateComponent> {
            if (style == HeaderStyle.NONE) return emptyList()
            val right = width - M
            val titleEnd = if (style == HeaderStyle.TITLE_AND_DATE) right - 250f else right
            val parts = mutableListOf<TemplateComponent>(
                TextLabel(M, 72f, "Title", size = 15f, bold = true, color = TemplateColors.TITLE),
                Rule(M + 52f, 76f, titleEnd, 76f, color = TemplateColors.RULE, width = 1.2f),
            )
            if (style == HeaderStyle.TITLE_AND_DATE) {
                parts += TextLabel(right - 226f, 72f, "Date", size = 15f, bold = true, color = TemplateColors.TITLE)
                parts += Rule(right - 180f, 76f, right, 76f, color = TemplateColors.RULE, width = 1.2f)
            }
            parts += Rule(M, 96f, right, 96f, width = 1.6f)
            return parts
        }
    }
}
