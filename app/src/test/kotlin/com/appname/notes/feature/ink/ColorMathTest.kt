package com.appname.notes.feature.ink

import com.appname.notes.core.ink.model.BrushStyle
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.settings.Recents
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The colour picker's maths and the recent-colour/brush lists (owner brief §13–14). */
class ColorMathTest {

    @Test
    fun hexIsReadInEveryCommonForm() {
        assertThat(ColorMath.parseHex("#1A73E8")).isEqualTo(0xFF1A73E8.toInt())
        assertThat(ColorMath.parseHex("1a73e8")).isEqualTo(0xFF1A73E8.toInt())
        assertThat(ColorMath.parseHex(" #F80 ")).isEqualTo(0xFFFF8800.toInt())
        assertThat(ColorMath.parseHex("#12345")).isNull()
        assertThat(ColorMath.parseHex("#GG0000")).isNull()
        assertThat(ColorMath.parseHex("")).isNull()
        assertThat(ColorMath.hex(0x801A73E8.toInt())).isEqualTo("#1A73E8")
        assertThat(ColorMath.hex(0xFF000005.toInt())).isEqualTo("#000005")
    }

    @Test
    fun hsvRoundTripsWithinOneStep() {
        val colors = ColorPalettes.ALL.flatMap { it.colors } + listOf(0xFF336699.toInt(), 0xFFFF0000.toInt(), 0xFF808080.toInt())
        for (c in colors) {
            val (h, s, v) = ColorMath.colorToHsv(c).let { Triple(it[0], it[1], it[2]) }
            val back = ColorMath.hsvToColor(h, s, v)
            for (shift in listOf(16, 8, 0)) {
                assertThat(kotlin.math.abs((back shr shift and 0xFF) - (c shr shift and 0xFF))).isAtMost(1)
            }
        }
        assertThat(ColorMath.hsvToColor(120f, 1f, 1f)).isEqualTo(0xFF00FF00.toInt())
        assertThat(ColorMath.hsvToColor(360f, 1f, 1f)).isEqualTo(0xFFFF0000.toInt())
        assertThat(ColorMath.rgb(300, -5, 10)).isEqualTo(0xFFFF000A.toInt())
    }

    @Test
    fun palettesHaveNoRepeatsWithinThemselves() {
        for (p in ColorPalettes.ALL) assertThat(p.colors).containsNoDuplicates()
    }

    @Test
    fun recentListsStayShortAndWithoutRepeats() {
        var list = emptyList<Int>()
        repeat(20) { list = Recents.push(list, it % 15, Recents.MAX_COLORS) }
        assertThat(list).hasSize(Recents.MAX_COLORS)
        assertThat(list).containsNoDuplicates()
        assertThat(list.first()).isEqualTo(19 % 15)
        assertThat(Recents.decodeColors(Recents.encodeColors(list), Recents.MAX_COLORS)).isEqualTo(list)
    }

    @Test
    fun recentBrushesSurviveStorageAndSkipUnknownOnes() {
        val brushes = listOf(
            InkBrush(InkTool.PENCIL, 0xFF2F2F2F.toInt(), 3f, BrushStyle.PENCIL_2B, pressure = false),
            InkBrush(InkTool.BRUSH, 0x80FF0000.toInt(), 8f, BrushStyle.WATERCOLOUR_BRUSH),
            InkBrush(InkTool.PEN, 0xFF000000.toInt(), 2f),
        )
        val text = Recents.encodeBrushes(brushes)
        assertThat(Recents.decodeBrushes(text)).isEqualTo(brushes.map { it.normalized() })
        // A variant from a newer version, or a broken entry, is skipped; the rest is kept.
        assertThat(Recents.decodeBrushes("4,99,-1,2.0,1;garbage;" + text)).hasSize(3)
        assertThat(Recents.decodeBrushes(null)).isEmpty()
    }
}
