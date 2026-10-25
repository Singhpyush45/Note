package com.appname.notes

import com.appname.notes.core.ink.model.PageTemplate
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.feature.ink.InkCanvasView
import com.appname.notes.feature.ink.InkEngine
import com.appname.notes.feature.ink.InkTestTags
import com.appname.notes.feature.library.LibraryTestTags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for a native crash seen in CI: Jetpack Ink 1.0's live-stroke renderer (Android 13+)
 * aborting with "Cannot drawFrame with no CanvasContext" after the handwriting screen went away.
 *
 * It does what users do and what triggers it: write a stroke and leave right away (Back, or Home), many
 * times, then puts the app under memory pressure. If the process crashes, the test run fails and CI
 * publishes the native crash log.
 */
@RunWith(AndroidJUnit4::class)
class InkLifecycleStressTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun findCanvas(v: View): InkCanvasView? =
        v as? InkCanvasView ?: (v as? ViewGroup)?.children?.firstNotNullOfOrNull { findCanvas(it) }

    private fun canvasOrNull() = findCanvas(composeRule.activity.window.decorView)

    private fun openNewInkNote() {
        // + → Choose template → Lined (owner brief §3)
        composeRule.onNodeWithTag(LibraryTestTags.CREATE).performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag(LibraryTestTags.CHOOSE_TEMPLATE)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(LibraryTestTags.CHOOSE_TEMPLATE).performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag(LibraryTestTags.template(PageTemplate.LINED))).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(LibraryTestTags.template(PageTemplate.LINED)).performClick()
        composeRule.waitUntil(10_000) { composeRule.onAllNodes(hasTestTag(InkTestTags.CANVAS)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitUntil(5_000) { (canvasOrNull()?.width ?: 0) > 0 }
    }

    /** One quick stylus stroke; returns without waiting, like a hand lifting and immediately navigating. */
    private fun quickStroke(seed: Int) {
        composeRule.runOnUiThread {
            val view = canvasOrNull() ?: return@runOnUiThread
            val down = SystemClock.uptimeMillis()
            var t = down
            val y0 = 200f + (seed % 5) * 60f
            fun send(action: Int, x: Float, y: Float) {
                val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
                val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 0.7f }
                val e = MotionEvent.obtain(down, t, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
                view.dispatchTouchEvent(e)
                e.recycle()
            }
            send(MotionEvent.ACTION_DOWN, 80f, y0)
            for (i in 1..20) { t += 8; send(MotionEvent.ACTION_MOVE, 80f + i * 12f, y0 + (i % 3) * 4f) }
            t += 8
            send(MotionEvent.ACTION_UP, 320f, y0)
        }
    }

    private fun memoryPressure() {
        repeat(6) {
            // Short-lived garbage forces collections (and finalizers) like a busy app would.
            @Suppress("UNUSED_VARIABLE")
            val junk = List(40) { ByteArray(1_000_000) }
            Runtime.getRuntime().gc()
            Thread.sleep(150)
        }
        System.runFinalization()
    }

    @Test
    fun writingAndLeavingImmediatelyManyTimesDoesNotCrash() {
        if (!InkEngine.available) return // nothing to test without the native engine
        repeat(12) { i ->
            openNewInkNote()
            quickStroke(i)
            Thread.sleep((i % 4) * 40L) // vary how far into Ink's ~500 ms handoff the screen closes
            composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitUntil(10_000) { composeRule.onAllNodes(hasTestTag(LibraryTestTags.SCREEN)).fetchSemanticsNodes().isNotEmpty() }
        }
        memoryPressure()
        Thread.sleep(2_000) // let any late Ink render-thread work run
        assertThat(composeRule.activity.isDestroyed).isFalse()
    }

    @Test
    fun writingAndGoingToTheBackgroundImmediatelyManyTimesDoesNotCrash() {
        if (!InkEngine.available) return
        openNewInkNote()
        repeat(10) { i ->
            quickStroke(i)
            Thread.sleep((i % 4) * 40L)
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED) // like pressing Home
            memoryPressure()
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitUntil(5_000) { (canvasOrNull()?.width ?: 0) > 0 }
        }
        Thread.sleep(2_000)
        assertThat(canvasOrNull()).isNotNull()
    }
}
