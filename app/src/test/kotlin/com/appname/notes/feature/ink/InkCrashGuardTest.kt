package com.appname.notes.feature.ink

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InkCrashGuardTest {

    private val race = IllegalStateException("Attempt to draw after renderer has been closed")

    @Test
    fun `only the known Ink shutdown race on Ink's render thread is absorbed`() {
        assertThat(InkCrashGuard.isKnownInkShutdownRace("CanvasInProgressStrokesRenderHelperV33_Render", race)).isTrue()
        // Same message elsewhere, other errors on Ink's thread, or no thread name: crash normally.
        assertThat(InkCrashGuard.isKnownInkShutdownRace("main", race)).isFalse()
        assertThat(InkCrashGuard.isKnownInkShutdownRace("CanvasInProgressStrokesRenderHelperV33_Render", IllegalStateException("other"))).isFalse()
        assertThat(InkCrashGuard.isKnownInkShutdownRace("CanvasInProgressStrokesRenderHelperV33_Render", RuntimeException(race.message))).isFalse()
        assertThat(InkCrashGuard.isKnownInkShutdownRace(null, race)).isFalse()
    }
}
