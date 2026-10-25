package com.appname.notes.core.ink.template

import com.appname.notes.core.ink.model.PageTemplate

/** Narrow / normal / wide line and grid spacing (owner brief §5). */
enum class SpacingPreset { NARROW, NORMAL, WIDE }

/** A named set of paper colours. */
data class PaperPalette(val name: String, val colors: List<Int>)

/**
 * Page settings applied to a page's design (owner brief §5): its background (blank, lined, grid, dot) with a
 * spacing variant, and its paper colour. Pure functions over [TemplateLayout], so every change is testable.
 */
object PageDesign {

    /** Spacing in page units; lines and grids match the Basic templates (Lined 30, Grid 24 …). */
    fun spacing(ruling: Ruling, preset: SpacingPreset): Float = when (ruling) {
        Ruling.LINES -> when (preset) { SpacingPreset.NARROW -> 24f; SpacingPreset.NORMAL -> 30f; SpacingPreset.WIDE -> 38f }
        Ruling.NONE, Ruling.GRID, Ruling.DOTS -> when (preset) { SpacingPreset.NARROW -> 16f; SpacingPreset.NORMAL -> 24f; SpacingPreset.WIDE -> 32f }
    }

    /** The preset closest to [spacing] for [ruling]. */
    fun preset(ruling: Ruling, spacing: Float): SpacingPreset =
        SpacingPreset.entries.minBy { kotlin.math.abs(spacing(ruling, it) - spacing) }

    /** The ruling of the whole page, as the Basic templates draw it. */
    fun paperFor(ruling: Ruling, preset: SpacingPreset): PaperStyle {
        val s = spacing(ruling, preset)
        return when (ruling) {
            Ruling.NONE -> PaperStyle()
            Ruling.LINES -> PaperStyle(Ruling.LINES, s, marginX = 64f, top = s * 3)
            Ruling.GRID -> PaperStyle(Ruling.GRID, s)
            Ruling.DOTS -> PaperStyle(Ruling.DOTS, s, lineColor = TemplateColors.DOT)
        }
    }

    /** The page's current design, or its plain background as a design. */
    fun current(layout: TemplateLayout?, template: PageTemplate, width: Float, height: Float): TemplateLayout =
        layout?.resized(width, height) ?: TemplateLayout.basic(template, width, height)

    /**
     * A new background for a page. Keeps its paper colour; replaces any template design (its printed parts),
     * since a plain background was chosen instead.
     */
    fun withBackground(current: TemplateLayout, ruling: Ruling, preset: SpacingPreset): TemplateLayout =
        TemplateLayout(width = current.width, height = current.height, paperColor = current.paperColor, paper = paperFor(ruling, preset))

    /** The same design on paper of another colour. */
    fun withPaperColor(current: TemplateLayout, color: Int): TemplateLayout = current.copy(paperColor = color or OPAQUE)

    /** A page has a template design when something is printed on it beyond the ruling. */
    fun hasDesign(layout: TemplateLayout?): Boolean = !layout?.components.isNullOrEmpty()

    /** Paper colour sets: soft pastels, warm and cool tones, neutrals. All light, since ink is drawn on paper. */
    val PALETTES: List<PaperPalette> = listOf(
        PaperPalette("pastel", listOf(0xFFFFFFFF, 0xFFFFF8E7, 0xFFF1F8F1, 0xFFEEF4FC, 0xFFFDF0F3, 0xFFF4F0FB).map { it.toInt() }),
        PaperPalette("warm", listOf(0xFFFFF4E0, 0xFFFFEFD9, 0xFFFDE8E0, 0xFFFFF7CC, 0xFFF9EBDD, 0xFFF7E7EF).map { it.toInt() }),
        PaperPalette("cool", listOf(0xFFE8F5F2, 0xFFE3F0FA, 0xFFE9EEFB, 0xFFE6F4EA, 0xFFEAF6F6, 0xFFEFEAF8).map { it.toInt() }),
        PaperPalette("neutral", listOf(0xFFF8F9FA, 0xFFF3F3F1, 0xFFEFEDE8, 0xFFECEFF1, 0xFFF5F2EC, 0xFFE9E9E9).map { it.toInt() }),
    )

    /**
     * A custom paper colour from hue (0–360°) and how strong the tint is (0 = white, 1 = the strongest
     * allowed). Paper always stays light (lightness ≥ 88%), so ink and lines stay readable.
     */
    fun customPaper(hue: Float, tint: Float): Int {
        val s = tint.coerceIn(0f, 1f) * 0.85f
        val l = 0.97f - tint.coerceIn(0f, 1f) * 0.09f
        return hsl(hue, s, l)
    }

    /** HSL → opaque ARGB. */
    fun hsl(hue: Float, saturation: Float, lightness: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
        val x = c * (1f - kotlin.math.abs(h % 2f - 1f))
        val m = lightness - c / 2f
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = ((v + m) * 255f).toInt().coerceIn(0, 255)
        return OPAQUE or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    private const val OPAQUE = 0xFF shl 24
}
