package com.appname.notes.feature.ink

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MathRenderTest {
    @Test
    fun `fractions, powers and roots are laid out and drawn`() {
        val flat = MathRender.layout("ab", 96f, 0xFF000000.toInt())!!
        val frac = MathRender.layout("\\frac{a}{b}", 96f, 0xFF000000.toInt())!!
        // A fraction stacks: taller than the same letters side by side.
        assertThat(frac.ascent + frac.descent).isGreaterThan(flat.ascent + flat.descent)
        val power = MathRender.layout("x^2", 96f, 0xFF000000.toInt())!!
        assertThat(power.ascent).isGreaterThan(MathRender.layout("x", 96f, 0xFF000000.toInt())!!.ascent)
        val bmp = MathRender.render("\\sqrt[3]{x} + \\sum_{i=1}^{n} i^2 \\leq \\infty", 96f, 0xFF000000.toInt())!!
        assertThat(bmp.width).isGreaterThan(bmp.height)
        assertThat(MathRender.layout("   ", 96f, 0)).isNull()
    }
}
