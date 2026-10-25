package com.appname.notes.core.settings

import com.appname.notes.core.ink.input.DefaultBrushes
import com.appname.notes.core.ink.input.EditorTool
import com.appname.notes.core.ink.input.EraserMode
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.recognition.HoldDetector

/** What a finger does on a handwritten page. */
enum class FingerMode {
    /** Fingers draw until a stylus is used for the first time; after that they scroll and zoom. */
    AUTO,

    /** Fingers always draw (two fingers still scroll and zoom). */
    DRAW,

    /** Fingers only scroll and zoom; only a stylus or mouse draws. */
    SCROLL,
}

/** Hold durations offered in Settings (SRS 6.2: hold time is configurable). */
enum class HoldDuration(val millis: Long) {
    SHORT(350),
    NORMAL(HoldDetector.DEFAULT_HOLD_MILLIS),
    LONG(800),
    ;

    companion object {
        fun of(millis: Long): HoldDuration = entries.minBy { kotlin.math.abs(it.millis - millis) }
    }
}

/** Handwriting preferences, including the last used pens so they are kept between notes. */
data class InkSettings(
    val shapeCorrection: Boolean = true,
    val holdDuration: HoldDuration = HoldDuration.NORMAL,
    val fingerMode: FingerMode = FingerMode.AUTO,
    val stylusSeen: Boolean = false,
    val lastTool: EditorTool = EditorTool.PEN,
    val eraserMode: EraserMode = EraserMode.STROKE,
    val brushes: Map<InkTool, InkBrush> = DefaultBrushes,
    /** Recently used tool set-ups (variant, size, colour), newest first (owner brief §13). */
    val recentBrushes: List<InkBrush> = emptyList(),
    /** Recently used colours, newest first (owner brief §14). */
    val recentColors: List<Int> = emptyList(),
    /** The user's saved colours ("My colours"), in the order saved. */
    val savedColors: List<Int> = emptyList(),
    val laser: LaserSettings = LaserSettings(),
) {
    val fingerDraws: Boolean
        get() = when (fingerMode) {
            FingerMode.AUTO -> !stylusSeen
            FingerMode.DRAW -> true
            FingerMode.SCROLL -> false
        }
}

/** Laser pointer looks (owner brief §17). */
enum class LaserStyle { SOLID, OUTLINE, DOT }

/** The laser pointer: transient, never saved into the note. */
data class LaserSettings(
    val style: LaserStyle = LaserStyle.SOLID,
    val rainbow: Boolean = false,
    val colorArgb: Int = 0xFFFF3B30.toInt(),
    /** Width in dp on screen. */
    val widthDp: Float = 6f,
)

/** Keeps recent lists short and without repeats. */
object Recents {
    const val MAX_BRUSHES = 6
    const val MAX_COLORS = 12
    const val MAX_SAVED = 48

    fun <T> push(list: List<T>, item: T, max: Int): List<T> = (listOf(item) + list.filter { it != item }).take(max)

    /** "tool,style,colour,size,pressure;…" — the storage form of recent brushes. Unknown entries are skipped. */
    fun encodeBrushes(list: List<InkBrush>): String = list.joinToString(";") { b ->
        "${b.tool.code},${b.effectiveStyle.code},${b.colorArgb},${b.size},${if (b.pressure) 1 else 0}"
    }

    fun decodeBrushes(text: String?): List<InkBrush> = text.orEmpty().split(';').mapNotNull { entry ->
        val f = entry.split(',')
        if (f.size != 5) return@mapNotNull null
        val tool = f[0].toIntOrNull()?.let { InkTool.fromCode(it) } ?: return@mapNotNull null
        val style = f[1].toIntOrNull()?.let { com.appname.notes.core.ink.model.BrushStyle.fromCode(it) }?.takeIf { it.tool == tool } ?: return@mapNotNull null
        val color = f[2].toIntOrNull() ?: return@mapNotNull null
        val size = f[3].toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return@mapNotNull null
        InkBrush(tool, color, size, style, f[4] != "0").normalized()
    }.take(MAX_BRUSHES)

    fun encodeColors(list: List<Int>): String = list.joinToString(",")

    fun decodeColors(text: String?, max: Int): List<Int> = text.orEmpty().split(',').mapNotNull { it.toIntOrNull() }.take(max)
}
