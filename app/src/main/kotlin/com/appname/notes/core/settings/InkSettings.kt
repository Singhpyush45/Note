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
) {
    val fingerDraws: Boolean
        get() = when (fingerMode) {
            FingerMode.AUTO -> !stylusSeen
            FingerMode.DRAW -> true
            FingerMode.SCROLL -> false
        }
}
