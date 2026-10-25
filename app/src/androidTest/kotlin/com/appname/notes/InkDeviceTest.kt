package com.appname.notes

import com.appname.notes.core.ink.model.PageTemplate
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.di.AppDataEntryPoint
import com.appname.notes.feature.ink.InkCanvasView
import com.appname.notes.feature.ink.InkEngine
import com.appname.notes.feature.ink.InkTestTags
import com.appname.notes.feature.library.LibraryTestTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Handwriting with the real Jetpack Ink engine (native code, front-buffered live strokes) on an emulator.
 * Input is injected as stylus MotionEvents straight into the canvas view.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class InkDeviceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val data by lazy { EntryPointAccessors.fromApplication(composeRule.activity.application, AppDataEntryPoint::class.java) }

    private fun inkNoteIds(): Set<NoteId> = runBlocking {
        data.notes().observeNotes(NoteCollection.All, NoteSort.MODIFIED).first().filter { it.type == NoteType.INK }.map { it.id }.toSet()
    }

    private fun items(id: NoteId): List<InkItem> = runBlocking { (data.ink().loadPages(id) as AppResult.Success).value.flatMap { it.items } }

    private fun findCanvas(v: View): InkCanvasView? =
        v as? InkCanvasView ?: (v as? ViewGroup)?.children?.firstNotNullOfOrNull { findCanvas(it) }

    private fun canvas(): InkCanvasView = findCanvas(composeRule.activity.window.decorView)!!

    /** Opens a new handwritten note and returns its id. */
    private fun newInkNote(): NoteId {
        val before = inkNoteIds()
        // + → Choose template → Lined (owner brief §3)
        composeRule.onNodeWithTag(LibraryTestTags.CREATE).performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag(LibraryTestTags.CHOOSE_TEMPLATE)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(LibraryTestTags.CHOOSE_TEMPLATE).performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag(LibraryTestTags.template(PageTemplate.LINED))).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(LibraryTestTags.template(PageTemplate.LINED)).performClick()
        composeRule.waitUntil(10_000) { composeRule.onAllNodes(hasTestTag(InkTestTags.CANVAS)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitUntil(10_000) { (inkNoteIds() - before).size == 1 }
        composeRule.waitUntil(5_000) { findCanvas(composeRule.activity.window.decorView)?.width ?: 0 > 0 }
        return (inkNoteIds() - before).single()
    }

    private fun stylus(points: List<Pair<Float, Float>>, holdMillis: Long = 0) {
        composeRule.runOnUiThread {
            val view = canvas()
            val down = SystemClock.uptimeMillis()
            var t = down
            fun send(action: Int, p: Pair<Float, Float>) {
                val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
                val coords = MotionEvent.PointerCoords().apply { x = p.first; y = p.second; pressure = 0.8f }
                val e = MotionEvent.obtain(down, t, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
                view.dispatchTouchEvent(e)
                e.recycle()
            }
            send(MotionEvent.ACTION_DOWN, points.first())
            for (p in points.drop(1)) { t += 8; send(MotionEvent.ACTION_MOVE, p) }
            var held = 0L
            while (held < holdMillis) { t += 50; held += 50; send(MotionEvent.ACTION_MOVE, points.last()) }
            t += 8
            send(MotionEvent.ACTION_UP, points.last())
        }
        composeRule.waitForIdle()
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 40) =
        List(n) { i -> val f = i / (n - 1f); (x0 + (x1 - x0) * f) to (y0 + (y1 - y0) * f) }

    @Test
    fun inkEngineLoadsAndAStrokeIsSavedAndDrawnWhereThePenWent() {
        assertThat(InkEngine.available).isTrue()
        val id = newInkNote()
        val w = canvas().width.toFloat()
        val y = canvas().height * 0.3f
        stylus(line(w * 0.2f, y, w * 0.8f, y))
        composeRule.waitUntil(10_000) { items(id).size == 1 }
        assertThat((items(id).single() as StrokeItem).points.pressure).isNotNull()

        // Pixel check: dark ink along the pen path, paper a little above it.
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(InkTestTags.CANVAS).captureToImage().asAndroidBitmap()
        fun darkest(x: Int, yc: Int): Int = (yc - 4..yc + 4).minOf { yy ->
            val c = bitmap.getPixel(x, yy)
            ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3
        }
        val yi = y.toInt()
        // Ink along the pen path (start, middle, end) is clearly darker than the paper just above it …
        for (fraction in listOf(0.3f, 0.5f, 0.7f)) {
            val x = (w * fraction).toInt()
            val paper = darkest(x, yi - 40)
            assertThat(paper).isGreaterThan(180) // paper or a light ruled line
            assertThat(darkest(x, yi)).isLessThan(paper - 60)
        }
        // … and nothing is drawn where the pen never went.
        assertThat(darkest((w * 0.1f).toInt(), yi)).isGreaterThan(180)
    }

    @Test
    fun drawAndHoldWithRealInkMakesAShape() {
        val id = newInkNote()
        val w = canvas().width.toFloat()
        val h = canvas().height.toFloat()
        val x0 = w * 0.2f
        val x1 = w * 0.8f
        val y0 = h * 0.25f
        val y1 = h * 0.45f
        val rect = line(x0, y0, x1, y0) + line(x1, y0, x1, y1).drop(1) + line(x1, y1, x0, y1).drop(1) + line(x0, y1, x0, y0).drop(1)
        stylus(rect, holdMillis = 700)
        composeRule.waitUntil(10_000) { items(id).singleOrNull() is ShapeItem }
        assertThat((items(id).single() as ShapeItem).kind).isEqualTo(ShapeKind.RECTANGLE)
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.editor_undo)).performClick()
        composeRule.waitUntil(10_000) { items(id).singleOrNull() is StrokeItem }
    }
}
