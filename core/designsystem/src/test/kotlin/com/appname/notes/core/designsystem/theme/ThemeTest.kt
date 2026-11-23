package com.appname.notes.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Every accent preset and custom hues all round the wheel, in both themes. */
    private val hues = Accent.entries.map { it.hue } + (0 until 360 step 15).map { it.toFloat() }

    private fun schemes(): List<Pair<String, ColorScheme>> =
        hues.flatMap { h -> listOf("light $h" to Palette.scheme(h, dark = false), "dark $h" to Palette.scheme(h, dark = true)) }

    private fun check(name: String, what: String, fg: Color, bg: Color, min: Double = 4.5) =
        assertWithMessage("$name: $what").that(Palette.contrast(fg, bg)).isAtLeast(min)

    @Test
    fun everyAccent_meetsWcagAaForTextOnItsBackgrounds() {
        for ((name, s) in schemes()) {
            check(name, "onBackground/background", s.onBackground, s.background)
            check(name, "onSurface/surface", s.onSurface, s.surface)
            check(name, "onSurfaceVariant/surface", s.onSurfaceVariant, s.surface)
            check(name, "onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant)
            check(name, "onPrimary/primary", s.onPrimary, s.primary)
            check(name, "onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer)
            check(name, "onSecondary/secondary", s.onSecondary, s.secondary)
            check(name, "onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
            check(name, "onTertiary/tertiary", s.onTertiary, s.tertiary)
            check(name, "onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer)
            check(name, "onError/error", s.onError, s.error)
            // Primary used as text/icons on the page (links, selected tabs), and outlines as UI boundaries (3:1).
            check(name, "primary/background", s.primary, s.background, min = 3.0)
            check(name, "outline/surface", s.outline, s.surface, min = 3.0)
        }
    }

    @Test
    fun surfacesAreCalm_lightIsLightAndDarkIsDark() {
        for (h in hues) {
            assertThat(Palette.scheme(h, false).surface.luminanceOf()).isGreaterThan(0.85)
            assertThat(Palette.scheme(h, true).surface.luminanceOf()).isLessThan(0.02)
        }
    }

    @Test
    fun appThemeUsesTheChosenAccent() {
        var blue = Color.Unspecified
        var pink = Color.Unspecified
        var dark = Color.Unspecified
        composeRule.setContent {
            AppTheme(darkTheme = false) { blue = MaterialTheme.colorScheme.primary }
            AppTheme(darkTheme = false, accentHue = Accent.PINK.hue) { pink = MaterialTheme.colorScheme.primary }
            AppTheme(darkTheme = true) { dark = MaterialTheme.colorScheme.background }
        }
        assertThat(blue).isEqualTo(Palette.scheme(Accent.BLUE.hue, false).primary)
        assertThat(pink).isEqualTo(Palette.scheme(Accent.PINK.hue, false).primary)
        assertThat(pink).isNotEqualTo(blue)
        assertThat(dark).isEqualTo(Palette.scheme(Accent.BLUE.hue, true).background)
    }

    @Test
    fun cornerRadiusScalesAllShapes_withinTheAllowedRange() {
        val s: Shapes = shapesFor(20.dp)
        assertThat(s.large).isEqualTo(RoundedCornerShape(20.dp))
        assertThat(s.small).isEqualTo(RoundedCornerShape(10.dp))
        assertThat(shapesFor(100.dp).large).isEqualTo(RoundedCornerShape(CornerRadius.MAX))
        assertThat(shapesFor(0.dp).large).isEqualTo(RoundedCornerShape(CornerRadius.MIN))
    }

    @Test
    fun compactDensityTightensSpacingButKeepsTouchTargets() {
        val compact = spacingFor(UiDensity.COMPACT)
        val comfortable = spacingFor(UiDensity.COMFORTABLE)
        assertThat(compact.lg).isLessThan(comfortable.lg)
        assertThat(compact.minTouchTarget).isEqualTo(48.dp)
        assertThat(comfortable.minTouchTarget).isEqualTo(48.dp)
    }

    @Test
    fun glassStaysReadable() {
        // Very clear glass with blur still has a tint; without blur it's nearly solid.
        assertThat(GlassStyle(intensity = 1f, blur = 1f).tintAlpha(blurAvailable = true)).isAtLeast(0.4f)
        assertThat(GlassStyle(intensity = 1f, blur = 1f).tintAlpha(blurAvailable = false)).isAtLeast(0.88f)
        assertThat(GlassStyle(intensity = 0f).tintAlpha(blurAvailable = true)).isGreaterThan(0.9f)
        assertThat(GlassStyle(blur = 0.5f).blurRadius).isEqualTo(20.dp)
    }

    private fun Color.luminanceOf(): Double = androidx.compose.ui.graphics.Color(value).let {
        (0.2126 * lin(it.red) + 0.7152 * lin(it.green) + 0.0722 * lin(it.blue))
    }

    private fun lin(c: Float): Double = if (c <= 0.04045f) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
}
