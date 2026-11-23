package com.appname.notes.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun adaptiveLayoutSpec_mapsPhoneFoldableAndTabletBreakpoints() {
        val phone = AdaptiveLayoutSpec.fromWidthDp(390)
        assertEquals(ScreenWidthBucket.COMPACT, phone.widthBucket)
        assertEquals(2, phone.gridColumnsRegular)
        assertEquals(3, phone.gridColumnsCompactCards)
        assertFalse(phone.useTwoPaneLayout)

        val foldable = AdaptiveLayoutSpec.fromWidthDp(680)
        assertEquals(ScreenWidthBucket.MEDIUM, foldable.widthBucket)
        assertEquals(3, foldable.gridColumnsRegular)
        assertEquals(4, foldable.gridColumnsCompactCards)
        assertFalse(foldable.useTwoPaneLayout)

        val tablet = AdaptiveLayoutSpec.fromWidthDp(1024)
        assertEquals(ScreenWidthBucket.EXPANDED, tablet.widthBucket)
        assertEquals(4, tablet.gridColumnsRegular)
        assertEquals(5, tablet.gridColumnsCompactCards)
        assertTrue(tablet.useTwoPaneLayout)
    }
}
