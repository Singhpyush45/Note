package com.appname.notes

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.repository.InkPageContent
import com.appname.notes.core.ink.input.EditorTool
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
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Handwriting end to end through the real app (real database), with synthetic stylus/finger input on a
 * simulated phone. Jetpack Ink's native code can't run on the JVM, so this also proves the fallback
 * renderer works; Ink itself is exercised by the emulator tests.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class InkFlowRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val data by lazy { EntryPointAccessors.fromApplication(composeRule.activity.application, AppDataEntryPoint::class.java) }

    private fun string(id: Int) = composeRule.activity.getString(id)

    private fun waitAdvancing(condition: () -> Boolean) = composeRule.waitUntil(15_000) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        condition()
    }

    private fun exists(matcher: SemanticsMatcher, unmerged: Boolean = false) =
        composeRule.onAllNodes(matcher, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(tag: String) = waitAdvancing { exists(hasTestTag(tag)) }

    private fun allNotes() = runBlocking { data.notes().observeNotes(NoteCollection.All, NoteSort.MODIFIED).first() }

    private fun inkNote(): NoteId = allNotes().single { it.type == NoteType.INK }.id

    private fun pages(id: NoteId = inkNote()): List<InkPageContent> =
        runBlocking { (data.ink().loadPages(id) as AppResult.Success).value }

    private fun items(): List<InkItem> = pages().flatMap { it.items }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun findCanvas(v: View): InkCanvasView? =
        v as? InkCanvasView ?: (v as? ViewGroup)?.children?.firstNotNullOfOrNull { findCanvas(it) }

    private fun canvas(): InkCanvasView = findCanvas(composeRule.activity.window.decorView)!!

    private fun openNewInkNote() {
        composeRule.onNodeWithTag(LibraryTestTags.NEW_INK_NOTE).performClick()
        waitFor(InkTestTags.CANVAS)
        waitAdvancing { findCanvas(composeRule.activity.window.decorView)?.width ?: 0 > 0 }
    }

    /**
     * Sends one pen (or finger) stroke through the view, like the touch screen would: DOWN, MOVEs 8 ms
     * apart, optional samples at the last point while "holding", UP. Times use the system clock, as real
     * events do.
     */
    private fun stroke(
        points: List<Pair<Float, Float>>,
        tool: Int = MotionEvent.TOOL_TYPE_STYLUS,
        holdMillis: Long = 0,
    ) {
        composeRule.runOnUiThread {
            val view = canvas()
            val down = SystemClock.uptimeMillis()
            var t = down
            fun send(action: Int, p: Pair<Float, Float>) {
                val props = MotionEvent.PointerProperties().apply { id = 0; toolType = tool }
                val coords = MotionEvent.PointerCoords().apply { x = p.first; y = p.second; pressure = 0.6f }
                val source = if (tool == MotionEvent.TOOL_TYPE_FINGER) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_STYLUS
                val e = MotionEvent.obtain(down, t, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, source, 0)
                view.dispatchTouchEvent(e)
                e.recycle()
            }
            send(MotionEvent.ACTION_DOWN, points.first())
            for (p in points.drop(1)) {
                t += 8
                send(MotionEvent.ACTION_MOVE, p)
            }
            var held = 0L
            while (held < holdMillis) {
                t += 50
                held += 50
                send(MotionEvent.ACTION_MOVE, points.last())
            }
            t += 8
            send(MotionEvent.ACTION_UP, points.last())
        }
        composeRule.waitForIdle()
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 25) =
        List(n) { i -> val f = i / (n - 1f); (x0 + (x1 - x0) * f) to (y0 + (y1 - y0) * f) }

    private fun rectangle(x0: Float, y0: Float, x1: Float, y1: Float): List<Pair<Float, Float>> =
        line(x0, y0, x1, y0) + line(x1, y0, x1, y1).drop(1) + line(x1, y1, x0, y1).drop(1) + line(x0, y1, x0, y0).drop(1)

    @Test
    fun penStrokesAreSavedAndShownAfterReopening() {
        assertThat(InkEngine.available).isFalse() // JVM: the fallback renderer is in use, without crashing
        openNewInkNote()
        stroke(line(60f, 200f, 340f, 260f))
        waitAdvancing { items().size == 1 }
        val s = items().single() as StrokeItem
        assertThat(s.points.size).isAtLeast(20)
        assertThat(s.points.pressure).isNotNull() // stylus pressure is kept
        waitAdvancing { exists(hasText(string(R.string.editor_saved))) }

        pressBack()
        waitFor(LibraryTestTags.SCREEN)
        // The card merges its children for TalkBack, so the thumbnail is found in the unmerged tree.
        waitAdvancing { exists(hasTestTag("ink_thumbnail_${inkNote().value}"), unmerged = true) }
        composeRule.onNodeWithText(string(R.string.ink_untitled)).performClick()
        waitFor(InkTestTags.CANVAS)
        assertThat(items()).hasSize(1)
    }

    @Test
    fun drawAndHoldMakesAShapeAndUndoBringsTheInkBack() {
        openNewInkNote()
        stroke(rectangle(80f, 200f, 330f, 360f), holdMillis = 700)
        waitAdvancing { items().singleOrNull() is ShapeItem }
        val shape = items().single() as ShapeItem
        assertThat(shape.kind).isEqualTo(ShapeKind.RECTANGLE)
        assertThat(shape.source).isNotNull()

        composeRule.onNodeWithContentDescription(string(R.string.editor_undo)).performClick()
        waitAdvancing { items().singleOrNull() is StrokeItem }
    }

    @Test
    fun eraserRemovesStrokesAndUndoRestoresThem() {
        openNewInkNote()
        stroke(line(60f, 200f, 340f, 200f))
        stroke(line(60f, 300f, 340f, 300f))
        waitAdvancing { items().size == 2 }
        composeRule.onNodeWithTag(InkTestTags.tool(EditorTool.ERASER)).performClick()
        stroke(line(200f, 150f, 200f, 350f))
        waitAdvancing { items().isEmpty() }
        composeRule.onNodeWithContentDescription(string(R.string.editor_undo)).performClick()
        waitAdvancing { items().size == 2 }
    }

    @Test
    fun lassoMovesInk() {
        openNewInkNote()
        stroke(line(100f, 220f, 200f, 220f))
        waitAdvancing { items().size == 1 }
        val before = (items().single() as StrokeItem).points.y[0]
        composeRule.onNodeWithTag(InkTestTags.tool(EditorTool.LASSO)).performClick()
        stroke(listOf(80f to 190f, 230f to 190f, 230f to 250f, 80f to 250f, 80f to 192f))
        waitAdvancing { exists(hasContentDescription(string(R.string.ink_selection_delete))) }
        stroke(line(150f, 220f, 150f, 320f))
        waitAdvancing { (items().single() as StrokeItem).points.y[0] > before + 50 }
    }

    @Test
    fun fingersDrawUntilAStylusIsUsedThenScroll() {
        openNewInkNote()
        stroke(line(60f, 200f, 340f, 200f), tool = MotionEvent.TOOL_TYPE_FINGER)
        waitAdvancing { items().size == 1 }
        assertThat((items().single() as StrokeItem).points.pressure).isNull() // finger: no pressure
        stroke(line(60f, 300f, 340f, 300f), tool = MotionEvent.TOOL_TYPE_STYLUS)
        waitAdvancing { items().size == 2 }
        waitAdvancing { exists(hasText(string(R.string.ink_stylus_detected), substring = true)) }
        stroke(line(200f, 500f, 200f, 300f), tool = MotionEvent.TOOL_TYPE_FINGER)
        composeRule.waitForIdle()
        assertThat(items()).hasSize(2) // the finger scrolled instead of drawing
    }

    @Test
    fun emptyHandwrittenNoteIsDiscardedWhenLeft() {
        openNewInkNote()
        pressBack()
        waitFor(LibraryTestTags.SCREEN)
        waitAdvancing { allNotes().isEmpty() }
    }

    @Test
    fun inkSurvivesRotation() {
        openNewInkNote()
        stroke(line(60f, 200f, 340f, 260f))
        waitAdvancing { items().size == 1 }
        composeRule.activityRule.scenario.recreate()
        waitFor(InkTestTags.CANVAS)
        // Same editor, still editable: a second stroke is added to the same note.
        waitAdvancing { findCanvas(composeRule.activity.window.decorView)?.width ?: 0 > 0 }
        stroke(line(60f, 320f, 340f, 320f))
        waitAdvancing { items().size == 2 }
    }

    @Test
    fun pagesCanBeAddedAndDeleted() {
        openNewInkNote()
        composeRule.onNodeWithContentDescription(string(R.string.ink_more_options)).performClick()
        composeRule.onNodeWithText(string(R.string.ink_page_add)).performClick()
        waitAdvancing { pages().size == 2 }
        waitAdvancing { exists(hasText("2 / 2")) } // the new page is scrolled into view
        composeRule.onNodeWithContentDescription(string(R.string.ink_more_options)).performClick()
        composeRule.onNodeWithText(string(R.string.ink_page_delete)).performClick()
        composeRule.onNodeWithText(string(R.string.action_delete)).performClick()
        waitAdvancing { pages().size == 1 }
    }
}
