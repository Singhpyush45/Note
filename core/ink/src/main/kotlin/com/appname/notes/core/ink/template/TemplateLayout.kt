package com.appname.notes.core.ink.template

import com.appname.notes.core.ink.model.PageSizes
import com.appname.notes.core.ink.model.PageTemplate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Page templates as structured layouts (owner brief §4): the page size, paper colour, ruling and a list of
 * simple components (labels, boxes, ruled areas, tables, checklists, music staves…), all in page units
 * (1 unit ≈ 1/96 inch; A4 is 794 × 1123). Never images: they scale sharply, can be edited, and a page stores
 * its own copy, so a note keeps its look even if the template is later changed or deleted.
 */

/** How the whole page is ruled, under the components. */
@Serializable
enum class Ruling { NONE, LINES, GRID, DOTS }

@Serializable
enum class LabelAlign { START, CENTER, END }

/** Ruling of the whole page. [top] leaves room for a header; [marginX] draws a red margin line (null = none). */
@Serializable
data class PaperStyle(
    val ruling: Ruling = Ruling.NONE,
    val spacing: Float = 30f,
    val lineColor: Int = TemplateColors.RULE,
    val marginX: Float? = null,
    val top: Float = 0f,
)

/** The size of a page; a template can use any, these are the ones offered (owner brief §5). */
enum class PageFormat(val width: Float, val height: Float) {
    A4_PORTRAIT(PageSizes.A4_WIDTH, PageSizes.A4_HEIGHT),
    A4_LANDSCAPE(PageSizes.A4_HEIGHT, PageSizes.A4_WIDTH),
    /** A tall page in a phone's 20:9 shape. */
    LONG_20_9(PageSizes.A4_WIDTH, PageSizes.A4_WIDTH * 20f / 9f),
    ;

    companion object {
        /** The offered format closest to [width] × [height] (by aspect ratio). */
        fun closest(width: Float, height: Float): PageFormat = entries.minBy { kotlin.math.abs(it.width / it.height - width / height) }
    }
}

/**
 * One page's design. [version] lets older app versions recognise layouts written by newer ones; components
 * of a kind they don't know are skipped when reading (see [TemplateJson]).
 */
@Serializable
data class TemplateLayout(
    val width: Float = PageSizes.A4_WIDTH,
    val height: Float = PageSizes.A4_HEIGHT,
    val paperColor: Int = TemplateColors.PAPER,
    val paper: PaperStyle = PaperStyle(),
    val components: List<TemplateComponent> = emptyList(),
    val version: Int = VERSION,
) {
    /** The nearest simple background, stored next to the layout for older code paths. */
    val basicTemplate: PageTemplate
        get() = when (paper.ruling) {
            Ruling.NONE -> PageTemplate.BLANK
            Ruling.LINES -> PageTemplate.LINED
            Ruling.GRID -> PageTemplate.GRID
            Ruling.DOTS -> PageTemplate.DOTTED
        }

    /** The same design on a page of another size: everything moves and stretches with the page. */
    fun resized(newWidth: Float, newHeight: Float): TemplateLayout {
        if (newWidth == width && newHeight == height) return this
        val sx = newWidth / width
        val sy = newHeight / height
        return copy(
            width = newWidth,
            height = newHeight,
            paper = paper.copy(marginX = paper.marginX?.times(sx), top = paper.top * sy),
            components = components.map { it.scaled(sx, sy) },
        )
    }

    companion object {
        const val VERSION = 1

        /** A plain page with one of the original backgrounds (Phase 3), as a layout. */
        fun basic(template: PageTemplate, width: Float = PageSizes.A4_WIDTH, height: Float = PageSizes.A4_HEIGHT): TemplateLayout =
            when (template) {
                PageTemplate.BLANK -> TemplateLayout(width, height)
                PageTemplate.LINED -> TemplateLayout(width, height, paper = PaperStyle(Ruling.LINES, PageTemplate.LINE_SPACING, marginX = 64f, top = 90f))
                PageTemplate.GRID -> TemplateLayout(width, height, paper = PaperStyle(Ruling.GRID, PageTemplate.GRID_SPACING))
                PageTemplate.DOTTED -> TemplateLayout(width, height, paper = PaperStyle(Ruling.DOTS, PageTemplate.GRID_SPACING, lineColor = TemplateColors.DOT))
            }
    }
}

/** Colours used by built-in templates (ARGB). Paper stays light: ink is drawn on "paper". */
object TemplateColors {
    const val PAPER = 0xFFFFFFFF.toInt()
    const val RULE = 0xFFD3DCE4.toInt()
    const val DOT = 0xFFB9C4CE.toInt()
    const val MARGIN = 0xFFF2B8B8.toInt()
    const val FRAME = 0xFFB4C0CC.toInt()
    const val TITLE = 0xFF202124.toInt()
    const val LABEL = 0xFF5F6368.toInt()
    const val HEADER_FILL = 0xFFF1F4F8.toInt()

    /** Paper colours offered for custom templates: white and soft pastels. */
    val PAPERS = listOf(
        PAPER,
        0xFFFFF8E7.toInt(), // cream
        0xFFF1F8F1.toInt(), // mint
        0xFFEEF4FC.toInt(), // sky
        0xFFFDF0F3.toInt(), // blush
        0xFFF4F0FB.toInt(), // lavender
        0xFFF3F3F1.toInt(), // stone
    )

    /** Line colours offered for custom templates. */
    val LINES = listOf(RULE, 0xFFA8C7FA.toInt(), 0xFFB7DFC0.toInt(), 0xFFF6C1CC.toInt(), 0xFFCFC3EA.toInt(), 0xFFBDBDBD.toInt())
}

/** Something printed on the page. Coordinates are page units from the top-left corner. */
@Serializable
sealed interface TemplateComponent {
    fun scaled(sx: Float, sy: Float): TemplateComponent
}

/** A printed word or heading, e.g. "Monday". [y] is the text's baseline. */
@Serializable
@SerialName("text")
data class TextLabel(
    val x: Float,
    val y: Float,
    val text: String,
    val size: Float = 14f,
    val bold: Boolean = false,
    val align: LabelAlign = LabelAlign.START,
    val color: Int = TemplateColors.LABEL,
) : TemplateComponent {
    override fun scaled(sx: Float, sy: Float) = copy(x = x * sx, y = y * sy, size = size * minOf(sx, sy))
}

/** A rectangle, optionally rounded and filled. */
@Serializable
@SerialName("box")
data class Box(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val radius: Float = 0f,
    val stroke: Int = TemplateColors.FRAME,
    val strokeWidth: Float = 1.5f,
    val fill: Int? = null,
) : TemplateComponent {
    override fun scaled(sx: Float, sy: Float) = copy(x = x * sx, y = y * sy, w = w * sx, h = h * sy)
}

/** A straight line. */
@Serializable
@SerialName("line")
data class Rule(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val color: Int = TemplateColors.FRAME,
    val width: Float = 1.5f,
) : TemplateComponent {
    override fun scaled(sx: Float, sy: Float) = copy(x1 = x1 * sx, y1 = y1 * sy, x2 = x2 * sx, y2 = y2 * sy)
}

/** Ruled lines filling an area, [spacing] apart, the first one [spacing] below the top. */
@Serializable
@SerialName("lines")
data class LinedArea(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val spacing: Float = 30f,
    val color: Int = TemplateColors.RULE,
) : TemplateComponent {
    override fun scaled(sx: Float, sy: Float) = copy(x = x * sx, y = y * sy, w = w * sx, h = h * sy, spacing = spacing * sy)
}

/** Dots on a square grid filling an area. */
@Serializable
@SerialName("dots")
data class DotArea(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val spacing: Float = 24f,
    val color: Int = TemplateColors.DOT,
) : TemplateComponent {
    override fun scaled(sx: Float, sy: Float) = copy(x = x * sx, y = y * sy, w = w * sx, h = h * sy, spacing = spacing * minOf(sx, sy))
}

/** Rows with an empty tick box and a line to write on. */
@Serializable
@SerialName("checklist")
data class Checklist(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val spacing: Float = 34f,
    val color: Int = TemplateColors.FRAME,
) : TemplateComponent {
    override fun scaled(sx: Float, sy: Float) = copy(x = x * sx, y = y * sy, w = w * sx, h = h * sy, spacing = spacing * sy)

    val rows: Int get() = (h / spacing).toInt().coerceAtLeast(0)
}

/**
 * A table: columns sized by [columns] weights, [rows] body rows, an optional header row with [headers] and
 * labels down the first column ([rowLabels]). Calendars, timetables, trackers and budgets are tables.
 */
@Serializable
@SerialName("table")
data class Table(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val columns: List<Float>,
    val rows: Int,
    val headers: List<String> = emptyList(),
    val rowLabels: List<String> = emptyList(),
    val headerHeight: Float = 28f,
    val labelSize: Float = 12f,
    val color: Int = TemplateColors.FRAME,
    val headerFill: Int? = TemplateColors.HEADER_FILL,
) : TemplateComponent {
    init {
        require(columns.isNotEmpty() && columns.all { it > 0f }) { "a table needs positive column weights" }
        require(rows >= 1) { "a table needs a row" }
    }

    override fun scaled(sx: Float, sy: Float) =
        copy(x = x * sx, y = y * sy, w = w * sx, h = h * sy, headerHeight = headerHeight * sy, labelSize = labelSize * minOf(sx, sy))

    val hasHeader: Boolean get() = headers.isNotEmpty()

    /** Left edges of the columns and the right edge of the last one. */
    fun columnEdges(): List<Float> {
        val total = columns.sum()
        var at = x
        return listOf(x) + columns.map { at += w * it / total; at }
    }

    /** Top of the body (below the header) and each row's bottom edge. */
    fun rowEdges(): List<Float> {
        val top = if (hasHeader) y + headerHeight else y
        val rowH = (y + h - top) / rows
        return List(rows + 1) { top + it * rowH }
    }
}

/** Music staves: groups of five lines, [lineGap] apart, filling the area. */
@Serializable
@SerialName("staves")
data class Staves(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val lineGap: Float = 9f,
    val staffGap: Float = 54f,
    val color: Int = 0xFF9AA5B1.toInt(),
) : TemplateComponent {
    override fun scaled(sx: Float, sy: Float) = copy(x = x * sx, y = y * sy, w = w * sx, h = h * sy, lineGap = lineGap * sy, staffGap = staffGap * sy)

    /** Top line of each whole staff that fits. */
    fun staffTops(): List<Float> {
        val staffHeight = lineGap * 4
        val tops = ArrayList<Float>()
        var top = y
        while (top + staffHeight <= y + h + 0.01f) {
            tops += top
            top += staffHeight + staffGap
        }
        return tops
    }
}

/** A row of empty circles to fill in (water glasses, ratings, moods). */
@Serializable
@SerialName("circles")
data class CircleRow(
    val x: Float,
    val y: Float,
    val count: Int,
    val diameter: Float = 22f,
    val gap: Float = 10f,
    val color: Int = TemplateColors.FRAME,
) : TemplateComponent {
    override fun scaled(sx: Float, sy: Float) =
        copy(x = x * sx, y = y * sy, diameter = diameter * minOf(sx, sy), gap = gap * sx)
}
