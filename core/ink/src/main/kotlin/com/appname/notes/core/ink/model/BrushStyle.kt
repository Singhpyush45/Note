package com.appname.notes.core.ink.model

/*
 * Pen, pencil and brush variants (owner brief §12). Every variant has its own rendering recipe
 * ([StrokeSpec]): how the width follows pressure or direction, whether the ends taper, which grain texture
 * fills it, and how it is finished (solid, soft edge, spray, watercolour wash, dots). Two variants never
 * share a recipe, so no two buttons draw the same stroke under different names (tested).
 */

/** How a stroke's width is decided along its length. */
enum class WidthMode {
    /** Always [InkBrush.size] (fineliner, technical pen). */
    CONSTANT,

    /** Thinner with light pressure, down to [StrokeSpec.minWidth] × size (ballpoint, pencils, brushes). */
    PRESSURE,

    /** A flat nib held at [StrokeSpec.nibAngleDeg]: wide across the nib, thin along it (fountain pen, calligraphy). */
    NIB,
}

/** Grain printed through the stroke, like paper tooth catching graphite or wax. */
enum class Texture { NONE, GRAPHITE_FINE, GRAPHITE, GRAPHITE_COARSE, WAX, CHALK, CHARCOAL, CANVAS, BRISTLE }

/** How the stroke's paint is laid down. */
enum class Finish {
    SOLID,

    /** Blurred edge (soft brush, charcoal smudge). */
    SOFT,

    /** Wide, very soft spray (airbrush). */
    SPRAY,

    /** Translucent wash with a darker rim where the "water" dried (watercolour). */
    WASH,

    /** Separate dots along the path (dot pen). */
    DOTS,
}

/** The rendering recipe of one variant. Sizes are fractions of [InkBrush.size]. */
data class StrokeSpec(
    val width: WidthMode,
    val minWidth: Float = 1f,
    val nibAngleDeg: Float = 45f,
    /** Also follow pressure on a [WidthMode.NIB] stroke. */
    val nibPressure: Boolean = false,
    val texture: Texture = Texture.NONE,
    val finish: Finish = Finish.SOLID,
    /** Multiplies the colour's own opacity (pencils are lighter than ink). */
    val opacity: Float = 1f,
    /** Blur radius for soft finishes, as a fraction of the size. */
    val softness: Float = 0f,
    val roundCap: Boolean = true,
    /** Length tapered at each end, as a fraction of the stroke (0 = blunt). */
    val taper: Float = 0f,
    /** Distance between dots for [Finish.DOTS], in sizes. */
    val dotSpacing: Float = 0f,
)

/** One variant. [code] is the stored value: never renumber. [defaultSize] in page units. */
enum class BrushStyle(val code: Int, val tool: InkTool, val defaultSize: Float, val spec: StrokeSpec, val jetpack: Boolean = false) {
    // ---- pens
    /** The original pen: drawn by Jetpack Ink (lowest latency). */
    BALLPOINT(1, InkTool.PEN, 2.5f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.55f), jetpack = true),
    FINE_PEN(2, InkTool.PEN, 1.2f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.7f)),
    GEL_PEN(3, InkTool.PEN, 3f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.85f, softness = 0.08f, finish = Finish.SOFT)),
    FOUNTAIN_PEN(4, InkTool.PEN, 3.5f, StrokeSpec(WidthMode.NIB, minWidth = 0.35f, nibAngleDeg = 35f, nibPressure = true)),
    FINELINER(5, InkTool.PEN, 1.6f, StrokeSpec(WidthMode.CONSTANT)),
    TECHNICAL_PEN(6, InkTool.PEN, 1f, StrokeSpec(WidthMode.CONSTANT, roundCap = false)),
    DOT_PEN(7, InkTool.PEN, 2.5f, StrokeSpec(WidthMode.CONSTANT, finish = Finish.DOTS, dotSpacing = 2.2f)),

    // ---- markers
    /** The original marker: drawn by Jetpack Ink. */
    FELT_MARKER(10, InkTool.MARKER, 5f, StrokeSpec(WidthMode.CONSTANT), jetpack = true),
    CHISEL_MARKER(11, InkTool.MARKER, 7f, StrokeSpec(WidthMode.NIB, minWidth = 0.3f, nibAngleDeg = 30f, opacity = 0.9f, roundCap = false)),

    // ---- highlighters
    /** The original highlighter: drawn by Jetpack Ink. */
    CHISEL_HIGHLIGHTER(20, InkTool.HIGHLIGHTER, 18f, StrokeSpec(WidthMode.CONSTANT, roundCap = false), jetpack = true),
    ROUND_HIGHLIGHTER(21, InkTool.HIGHLIGHTER, 14f, StrokeSpec(WidthMode.CONSTANT, opacity = 0.95f)),
    FINE_HIGHLIGHTER(22, InkTool.HIGHLIGHTER, 8f, StrokeSpec(WidthMode.CONSTANT, opacity = 0.9f, roundCap = false)),

    // ---- pencils (graphite: grain texture, lighter than ink; softer leads are darker and wider)
    NATURAL_PENCIL(30, InkTool.PENCIL, 2.2f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.7f, texture = Texture.GRAPHITE, opacity = 0.8f)),
    HB_PENCIL(31, InkTool.PENCIL, 1.8f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.75f, texture = Texture.GRAPHITE_FINE, opacity = 0.6f)),
    PENCIL_2B(32, InkTool.PENCIL, 2.4f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.65f, texture = Texture.GRAPHITE, opacity = 0.88f)),
    PENCIL_4B(33, InkTool.PENCIL, 3.2f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.6f, texture = Texture.GRAPHITE_COARSE, opacity = 0.96f)),
    MECHANICAL_PENCIL(34, InkTool.PENCIL, 1f, StrokeSpec(WidthMode.CONSTANT, texture = Texture.GRAPHITE_FINE, opacity = 0.72f)),
    SKETCH_PENCIL(35, InkTool.PENCIL, 3f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.3f, texture = Texture.GRAPHITE, opacity = 0.45f, taper = 0.2f)),
    GRAPHITE_PENCIL(36, InkTool.PENCIL, 4f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.55f, texture = Texture.GRAPHITE_COARSE, opacity = 0.85f, softness = 0.06f, finish = Finish.SOFT)),

    // ---- brushes
    BASIC_BRUSH(40, InkTool.BRUSH, 8f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.3f, taper = 0.12f)),
    SOFT_BRUSH(41, InkTool.BRUSH, 12f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.5f, finish = Finish.SOFT, softness = 0.35f, opacity = 0.8f)),
    CALLIGRAPHY_BRUSH(42, InkTool.BRUSH, 10f, StrokeSpec(WidthMode.NIB, minWidth = 0.15f, nibAngleDeg = 45f, nibPressure = true, taper = 0.1f)),
    CALLIGRAPHY_PEN(43, InkTool.BRUSH, 7f, StrokeSpec(WidthMode.NIB, minWidth = 0.2f, nibAngleDeg = 45f, roundCap = false)),
    AIRBRUSH(44, InkTool.BRUSH, 24f, StrokeSpec(WidthMode.CONSTANT, finish = Finish.SPRAY, softness = 0.7f, opacity = 0.35f)),
    OIL_BRUSH(45, InkTool.BRUSH, 14f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.7f, texture = Texture.BRISTLE, taper = 0.06f)),
    WATERCOLOUR_BRUSH(46, InkTool.BRUSH, 16f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.6f, finish = Finish.WASH, softness = 0.12f, opacity = 0.35f)),
    CRAYON(47, InkTool.BRUSH, 6f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.85f, texture = Texture.WAX, opacity = 0.92f)),
    CHALK(48, InkTool.BRUSH, 7f, StrokeSpec(WidthMode.CONSTANT, texture = Texture.CHALK, opacity = 0.9f)),
    CHARCOAL(49, InkTool.BRUSH, 8f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.6f, texture = Texture.CHARCOAL, finish = Finish.SOFT, softness = 0.1f, opacity = 0.9f)),
    TEXTURE_BRUSH(50, InkTool.BRUSH, 12f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.6f, texture = Texture.CANVAS)),
    ARTISTIC_BRUSH(51, InkTool.BRUSH, 10f, StrokeSpec(WidthMode.PRESSURE, minWidth = 0.2f, taper = 0.3f)),
    MARKER_BRUSH(52, InkTool.BRUSH, 10f, StrokeSpec(WidthMode.NIB, minWidth = 0.55f, nibAngleDeg = 90f, opacity = 0.85f, roundCap = false)),
    ;

    companion object {
        fun fromCode(code: Int): BrushStyle? = entries.firstOrNull { it.code == code }

        /** The variant every tool starts with (and what older notes' ink is). */
        fun classic(tool: InkTool): BrushStyle = when (tool) {
            InkTool.PEN -> BALLPOINT
            InkTool.MARKER -> FELT_MARKER
            InkTool.HIGHLIGHTER -> CHISEL_HIGHLIGHTER
            InkTool.PENCIL -> NATURAL_PENCIL
            InkTool.BRUSH -> BASIC_BRUSH
        }

        fun of(tool: InkTool): List<BrushStyle> = entries.filter { it.tool == tool }
    }
}
