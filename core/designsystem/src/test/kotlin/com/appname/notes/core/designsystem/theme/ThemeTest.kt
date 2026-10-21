package com.appname.notes.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightAndDarkThemes_useBrandSurfaces() {
        var light = Color.Unspecified
        var dark = Color.Unspecified
        composeRule.setContent {
            AppTheme(darkTheme = false) { light = MaterialTheme.colorScheme.background }
            AppTheme(darkTheme = true) { dark = MaterialTheme.colorScheme.background }
        }
        assertThat(light).isEqualTo(LightColors.background)
        assertThat(dark).isEqualTo(DarkColors.background)
    }

    @Test
    fun textOnBackground_meetsWcagAaContrast() {
        for (scheme in listOf(LightColors, DarkColors)) {
            assertThat(contrast(scheme.onBackground, scheme.background)).isAtLeast(4.5)
            assertThat(contrast(scheme.onSurfaceVariant, scheme.surface)).isAtLeast(4.5)
            assertThat(contrast(scheme.onPrimary, scheme.primary)).isAtLeast(4.5)
            assertThat(contrast(scheme.primary, scheme.background)).isAtLeast(4.5)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance() + 0.05
        val l2 = b.luminance() + 0.05
        return (maxOf(l1, l2) / minOf(l1, l2)).toDouble()
    }
}
