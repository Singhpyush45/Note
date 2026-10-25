package com.appname.notes.core.settings

import com.appname.notes.core.designsystem.theme.Accent
import com.appname.notes.core.designsystem.theme.CornerRadius
import com.appname.notes.core.designsystem.theme.UiDensity
import com.appname.notes.core.designsystem.theme.WallpaperPreset
import com.appname.notes.core.domain.model.NoteSort

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** What's behind the app's screens (owner brief §19). Stored as "none", "preset:AURORA" or "photo:<file name>". */
sealed interface WallpaperChoice {
    data object None : WallpaperChoice
    data class Preset(val preset: WallpaperPreset) : WallpaperChoice

    /** A photo the user picked, copied into app storage as [fileName]. */
    data class Photo(val fileName: String) : WallpaperChoice

    val stored: String
        get() = when (this) {
            None -> "none"
            is Preset -> "preset:${preset.name}"
            is Photo -> "photo:$fileName"
        }

    companion object {
        val DEFAULT: WallpaperChoice = Preset(WallpaperPreset.AURORA)

        /** Unknown values (a future version, a missing preset) fall back to the default. */
        fun parse(value: String?): WallpaperChoice = when {
            value == null -> DEFAULT
            value == "none" -> None
            value.startsWith("preset:") -> WallpaperPreset.fromName(value.removePrefix("preset:"))?.let(::Preset) ?: DEFAULT
            value.startsWith("photo:") && value.length > "photo:".length -> Photo(value.removePrefix("photo:"))
            else -> DEFAULT
        }
    }
}

/**
 * User-chosen look of the app (owner brief §19). Defaults are used until the user changes something; every value
 * has a visible effect and survives restarts.
 */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** A preset accent, or null for the custom [customHue]. */
    val accent: Accent? = Accent.DEFAULT,
    val customHue: Float = DEFAULT_CUSTOM_HUE,
    /** 0..100: how clear glass surfaces are. */
    val glassIntensity: Int = 60,
    /** 0..100: how much the background is blurred behind glass. */
    val blurIntensity: Int = 40,
    /** Card corner radius in dp. */
    val cornerRadius: Int = CornerRadius.DEFAULT.value.toInt(),
    val wallpaper: WallpaperChoice = WallpaperChoice.DEFAULT,
    val density: UiDensity = UiDensity.COMFORTABLE,
) {
    val accentHue: Float get() = accent?.hue ?: customHue

    /** Values kept within the ranges the app offers (a hand-edited or future file can't break the UI). */
    fun clamped() = copy(
        customHue = ((customHue % 360f) + 360f) % 360f,
        glassIntensity = glassIntensity.coerceIn(0, 100),
        blurIntensity = blurIntensity.coerceIn(0, 100),
        cornerRadius = cornerRadius.coerceIn(CornerRadius.MIN.value.toInt(), CornerRadius.MAX.value.toInt()),
    )

    companion object {
        const val DEFAULT_CUSTOM_HUE = 200f
    }
}

/** Library preferences remembered across launches. */
data class LibrarySettings(
    val sort: NoteSort = NoteSort.MODIFIED,
)
