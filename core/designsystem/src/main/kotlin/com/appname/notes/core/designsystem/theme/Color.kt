package com.appname.notes.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * "Ink & paper" palette: warm off-white paper surfaces, ink-blue primary, amber accent (highlighter feel).
 * Every role is set explicitly so no Material default (purple) leaks into the UI.
 * Text/background pairs meet WCAG AA contrast (SRS 8.1, 8.2).
 */

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2E4C9C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF142E7A),
    inversePrimary = Color(0xFFB6C4FF),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFFBF9F5),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFBF9F5),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFE5E2DA),
    onSurfaceVariant = Color(0xFF48463F),
    surfaceTint = Color(0xFF2E4C9C),
    inverseSurface = Color(0xFF31302D),
    inverseOnSurface = Color(0xFFF4F0EA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF7A776F),
    outlineVariant = Color(0xFFCAC6BE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF9F5),
    surfaceContainer = Color(0xFFF0EDE8),
    surfaceContainerHigh = Color(0xFFEAE7E2),
    surfaceContainerHighest = Color(0xFFE5E2DC),
    surfaceContainerLow = Color(0xFFF6F3EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDCD9D3),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF102B6B),
    primaryContainer = Color(0xFF29428A),
    onPrimaryContainer = Color(0xFFDCE1FF),
    inversePrimary = Color(0xFF2E4C9C),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFFFB870),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF141311),
    onBackground = Color(0xFFE6E2DC),
    surface = Color(0xFF141311),
    onSurface = Color(0xFFE6E2DC),
    surfaceVariant = Color(0xFF48463F),
    onSurfaceVariant = Color(0xFFCAC6BD),
    surfaceTint = Color(0xFFB6C4FF),
    inverseSurface = Color(0xFFE6E2DC),
    inverseOnSurface = Color(0xFF31302D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF94918A),
    outlineVariant = Color(0xFF48463F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A3936),
    surfaceContainer = Color(0xFF211F1D),
    surfaceContainerHigh = Color(0xFF2B2A27),
    surfaceContainerHighest = Color(0xFF363532),
    surfaceContainerLow = Color(0xFF1C1B19),
    surfaceContainerLowest = Color(0xFF0F0E0C),
    surfaceDim = Color(0xFF141311),
)
