package com.appname.notes.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Accent colour presets (owner brief §19), as hues on the colour wheel. Stored by name: never rename. */
enum class Accent(val hue: Float) {
    BLUE(221f),
    VIOLET(262f),
    PINK(332f),
    ORANGE(24f),
    AMBER(44f),
    MINT(162f),
    ;

    companion object {
        val DEFAULT = BLUE
        fun fromName(name: String?): Accent? = entries.firstOrNull { it.name == name }
    }
}

/** Compact fits more on screen; Comfortable gives more room (owner brief §19). Touch targets stay 48 dp in both. */
enum class UiDensity {
    COMPACT,
    COMFORTABLE,
    ;

    companion object {
        fun fromName(name: String?): UiDensity = entries.firstOrNull { it.name == name } ?: COMFORTABLE
    }
}

/**
 * How glass surfaces look (owner brief §1, §19).
 *
 * @param intensity 0..1: how much a glass surface shows what's behind it (0 = almost solid, 1 = very clear).
 * @param blur 0..1: how strongly what's behind is blurred (Android 12+; older phones get a more solid surface).
 */
@Immutable
data class GlassStyle(val intensity: Float = 0.6f, val blur: Float = 0.4f) {
    /** Opacity of the tint over the blurred backdrop: clearer glass, lower opacity, but never so low text gets hard to read. */
    fun tintAlpha(blurAvailable: Boolean): Float {
        val base = 0.92f - 0.5f * intensity.coerceIn(0f, 1f) // 0.92 .. 0.42
        // Without blur, sharp content behind would show through: stay mostly solid.
        return if (blurAvailable && blur > 0f) base else maxOf(base, 0.88f)
    }

    /** Blur radius for the backdrop. */
    val blurRadius: Dp get() = (blur.coerceIn(0f, 1f) * MAX_BLUR.value).dp

    companion object {
        val Default = GlassStyle()
        val MAX_BLUR = 40.dp
    }
}

/** Built-in wallpapers, drawn as soft gradients (no image files). Stored by name: never rename. */
enum class WallpaperPreset {
    AURORA,
    OCEAN,
    SUNSET,
    MEADOW,
    MIST,
    ;

    companion object {
        fun fromName(name: String?): WallpaperPreset? = entries.firstOrNull { it.name == name }
    }
}

/** Corner radius range offered in Settings (owner brief §19). */
object CornerRadius {
    val MIN = 8.dp
    val MAX = 32.dp
    val DEFAULT = 20.dp
}
