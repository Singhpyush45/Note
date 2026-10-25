package com.appname.notes.core.designsystem.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp

/** Dynamic colour ("Material You") needs Android 12 / API 31. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Real backdrop blur (RenderEffect) needs Android 12 / API 31; older phones get more solid glass. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsBlur(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

val LocalGlass = staticCompositionLocalOf { GlassStyle.Default }

/** True in dark theme (the glass border and highlight differ). */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * App-wide theme. Wrap every screen (and every preview/test) in this.
 *
 * @param darkTheme dark colours when true.
 * @param dynamicColor use wallpaper-based colours on Android 12+ (overrides [accentHue]); ignored on older versions.
 * @param accentHue the accent colour as a hue (a preset from [Accent], or a custom one).
 * @param glass how glass surfaces look.
 * @param cornerRadius the card corner radius; other shapes follow.
 * @param density Compact or Comfortable spacing.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accentHue: Float = Accent.DEFAULT.hue,
    glass: GlassStyle = GlassStyle.Default,
    cornerRadius: Dp = CornerRadius.DEFAULT,
    density: UiDensity = UiDensity.COMFORTABLE,
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme = when {
        dynamicColor && supportsDynamicColor() -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> remember(accentHue, darkTheme) { Palette.scheme(accentHue, darkTheme) }
    }
    val shapes = remember(cornerRadius) { shapesFor(cornerRadius) }

    CompositionLocalProvider(
        LocalSpacing provides spacingFor(density),
        LocalGlass provides glass,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = shapes,
            content = content,
        )
    }
}

/** Shortcut to design tokens that Material doesn't provide: `AppTheme.spacing.lg`, `AppTheme.glass`. */
object AppTheme {
    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current

    val glass: GlassStyle
        @Composable @ReadOnlyComposable
        get() = LocalGlass.current
}
