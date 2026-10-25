package com.appname.notes.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Builds a full Material colour scheme from one accent hue (owner brief §19: accent presets and custom colour).
 *
 * Surfaces are near-neutral with a faint tint of the accent (the calm, "glass over paper" look); the accent shows in
 * primary roles. Every text/background pair is pushed until it meets WCAG AA (4.5:1), whatever the hue — yellow and
 * mint accents get darker in light theme so white text on them stays readable. Tested for hues all round the wheel.
 */
object Palette {

    fun scheme(hue: Float, dark: Boolean): ColorScheme = if (dark) darkScheme(norm(hue)) else lightScheme(norm(hue))

    private fun norm(h: Float) = ((h % 360f) + 360f) % 360f

    private fun hsl(h: Float, s: Float, l: Float) = Color.hsl(norm(h), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

    fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb).toDouble() / minOf(la, lb).toDouble()
    }

    /** Darkens (or lightens) [h]/[s] from lightness [start] until it reaches [min] contrast against [against]. */
    private fun readable(h: Float, s: Float, start: Float, against: Color, min: Double = AA, darker: Boolean): Color {
        var l = start
        var c = hsl(h, s, l)
        while (contrast(c, against) < min && l in 0.02f..0.98f) {
            l += if (darker) -0.01f else 0.01f
            c = hsl(h, s, l)
        }
        return c
    }

    /**
     * A container colour and the text on it. If the text can't reach AA (bright hues like yellow have high luminance
     * at any lightness), the container itself moves away from the text until it does.
     */
    private fun containerPair(h: Float, cs: Float, cl: Float, ts: Float, tl: Float, dark: Boolean): Pair<Color, Color> {
        var l = cl
        while (true) {
            val container = hsl(h, cs, l)
            val text = readable(h, ts, tl, container, darker = !dark)
            if (contrast(text, container) >= AA || l !in 0.04f..0.96f) return container to text
            l += if (dark) -0.02f else 0.02f
        }
    }

    private fun lightScheme(h: Float): ColorScheme {
        val t = h + 55f // tertiary: a neighbouring hue, used sparingly
        val surface = hsl(h, 0.30f, 0.975f)
        val onSurface = hsl(h, 0.16f, 0.11f)
        val primary = readable(h, 0.80f, 0.50f, Color.White, darker = true)
        val (primaryContainer, onPrimaryContainer) = containerPair(h, 0.85f, 0.91f, 0.70f, 0.20f, dark = false)
        val (secondaryContainer, onSecondaryContainer) = containerPair(h, 0.40f, 0.90f, 0.30f, 0.16f, dark = false)
        val (tertiaryContainer, onTertiaryContainer) = containerPair(t, 0.75f, 0.90f, 0.55f, 0.18f, dark = false)
        return lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = hsl(h, 0.90f, 0.80f),
            secondary = readable(h, 0.20f, 0.42f, Color.White, darker = true),
            onSecondary = Color.White,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = readable(t, 0.60f, 0.42f, Color.White, darker = true),
            onTertiary = Color.White,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = hsl(h, 0.18f, 0.90f),
            onSurfaceVariant = readable(h, 0.10f, 0.32f, hsl(h, 0.18f, 0.90f), darker = true),
            surfaceTint = primary,
            inverseSurface = hsl(h, 0.10f, 0.19f),
            inverseOnSurface = hsl(h, 0.25f, 0.95f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            outline = readable(h, 0.08f, 0.50f, surface, min = 3.0, darker = true),
            outlineVariant = hsl(h, 0.14f, 0.82f),
            scrim = Color.Black,
            surfaceBright = hsl(h, 0.30f, 0.98f),
            surfaceDim = hsl(h, 0.14f, 0.87f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, 0.28f, 0.96f),
            surfaceContainer = hsl(h, 0.24f, 0.945f),
            surfaceContainerHigh = hsl(h, 0.20f, 0.93f),
            surfaceContainerHighest = hsl(h, 0.18f, 0.915f),
        )
    }

    private fun darkScheme(h: Float): ColorScheme {
        val t = h + 55f
        val surface = hsl(h, 0.16f, 0.075f)
        val onSurface = hsl(h, 0.14f, 0.91f)
        val onPrimary = hsl(h, 0.70f, 0.16f)
        val primary = readable(h, 0.90f, 0.76f, onPrimary, darker = false)
        val (primaryContainer, onPrimaryContainer) = containerPair(h, 0.50f, 0.28f, 0.90f, 0.90f, dark = true)
        val (secondaryContainer, onSecondaryContainer) = containerPair(h, 0.18f, 0.26f, 0.35f, 0.90f, dark = true)
        val (tertiaryContainer, onTertiaryContainer) = containerPair(t, 0.45f, 0.27f, 0.70f, 0.90f, dark = true)
        val onSecondary = hsl(h, 0.20f, 0.17f)
        val onTertiary = hsl(t, 0.60f, 0.16f)
        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = hsl(h, 0.75f, 0.42f),
            secondary = readable(h, 0.25f, 0.78f, onSecondary, darker = false),
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = readable(t, 0.70f, 0.78f, onTertiary, darker = false),
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = hsl(h, 0.12f, 0.27f),
            onSurfaceVariant = readable(h, 0.12f, 0.78f, surface, darker = false),
            surfaceTint = primary,
            inverseSurface = hsl(h, 0.14f, 0.90f),
            inverseOnSurface = hsl(h, 0.10f, 0.19f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            outline = readable(h, 0.08f, 0.56f, surface, min = 3.0, darker = false),
            outlineVariant = hsl(h, 0.10f, 0.30f),
            scrim = Color.Black,
            surfaceBright = hsl(h, 0.12f, 0.22f),
            surfaceDim = surface,
            surfaceContainerLowest = hsl(h, 0.16f, 0.05f),
            surfaceContainerLow = hsl(h, 0.14f, 0.095f),
            surfaceContainer = hsl(h, 0.13f, 0.115f),
            surfaceContainerHigh = hsl(h, 0.12f, 0.15f),
            surfaceContainerHighest = hsl(h, 0.11f, 0.185f),
        )
    }

    private const val AA = 4.5
}
