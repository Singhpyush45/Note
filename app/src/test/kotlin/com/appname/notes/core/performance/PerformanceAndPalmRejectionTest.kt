package com.appname.notes.core.performance

import com.appname.notes.feature.ink.InkTouchDecision
import com.appname.notes.feature.ink.PalmRejectionFilter
import com.appname.notes.feature.ink.PalmRejectionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PerformanceAndPalmRejectionTest {

    @Test
    fun calculateInSampleSize_downsamplesLargeImagesToPowerOfTwo() {
        assertEquals(1, BitmapMemoryCache.calculateInSampleSize(400, 300, 400, 300))
        assertEquals(4, BitmapMemoryCache.calculateInSampleSize(4000, 3000, 500, 500))
        assertEquals(2, BitmapMemoryCache.calculateInSampleSize(1200, 1200, 400, 400))
        assertEquals(1, BitmapMemoryCache.calculateInSampleSize(0, 0, 400, 400))
    }

    @Test
    fun palmRejectionFilter_prioritizesStylusAndPreservesTwoFingerZoom() {
        val filter = PalmRejectionFilter(stylusCooldownMs = 650L, palmMajorThresholdPx = 84f)

        // Stylus always draws
        assertEquals(
            InkTouchDecision.ALLOW_DRAW,
            filter.evaluate(PalmRejectionMode.AUTO_STYLUS, isStylus = true, pointerCount = 1, touchMajorPx = 10f, eventTimeMs = 1000L),
        )
        // Finger within 650ms cooldown after stylus is rejected as palm
        assertEquals(
            InkTouchDecision.REJECT_PALM,
            filter.evaluate(PalmRejectionMode.AUTO_STYLUS, isStylus = false, pointerCount = 1, touchMajorPx = 24f, eventTimeMs = 1300L),
        )
        // Two-finger pinch/pan always passes through even in STYLUS_ONLY mode
        assertEquals(
            InkTouchDecision.ALLOW_NAVIGATION_GESTURE,
            filter.evaluate(PalmRejectionMode.STYLUS_ONLY, isStylus = false, pointerCount = 2, touchMajorPx = 30f, eventTimeMs = 1350L),
        )
        // Finger after cooldown with normal touch radius draws normally in AUTO_STYLUS
        assertEquals(
            InkTouchDecision.ALLOW_DRAW,
            filter.evaluate(PalmRejectionMode.AUTO_STYLUS, isStylus = false, pointerCount = 1, touchMajorPx = 22f, eventTimeMs = 2000L),
        )
        // Large palm contact area is rejected even after cooldown
        assertEquals(
            InkTouchDecision.REJECT_PALM,
            filter.evaluate(PalmRejectionMode.AUTO_STYLUS, isStylus = false, pointerCount = 1, touchMajorPx = 110f, eventTimeMs = 2500L),
        )
    }
}
