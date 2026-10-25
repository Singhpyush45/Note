package com.appname.notes

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.repository.InkPageContent
import com.appname.notes.core.domain.repository.PdfDocumentInfo
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.di.AppDataEntryPoint
import com.appname.notes.feature.ink.InkCanvasView
import com.appname.notes.feature.ink.InkTestTags
import com.appname.notes.feature.library.LibraryTestTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * PDF notes through the real app on the JVM. Android's PDF renderer is native and doesn't run here, so this
 * covers the parts around it: the workspace for a PDF note, the "can't display" state (SRS 7.1: damaged or
 * missing files must not crash), and that handwriting on a PDF page is saved like any ink.
 * Rendering, import and export of real files are covered by PdfDeviceTest on the emulator.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class PdfFlowRobolectricTest {

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

    private fun findCanvas(v: View): InkCanvasView? =
        v as? InkCanvasView ?: (v as? ViewGroup)?.children?.firstNotNullOfOrNull { findCanvas(it) }

    private fun pages(id: NoteId): List<InkPageContent> = runBlocking { (data.ink().loadPages(id) as AppResult.Success).value }

    /** A PDF note whose file isn't in storage (as if it was deleted or damaged): A4 portrait, then landscape. */
    private fun createPdfNoteWithMissingFile(sizes: List<Pair<Float, Float>> = listOf(794f to 1123f, 1123f to 794f)): NoteId = runBlocking {
        val doc = PdfDocumentInfo("missing-${System.nanoTime()}.pdf", "Lecture 5.pdf", "0".repeat(64), 1234, sizes.size)
        (data.pdf().createPdfNote(null, "Lecture 5", doc, sizes) as AppResult.Success).value
    }

    private fun openNote(id: NoteId) {
        waitAdvancing { exists(hasTestTag("note_card_${id.value}")) }
        composeRule.onNodeWithTag("note_card_${id.value}").performClick()
        waitAdvancing { exists(hasTestTag(InkTestTags.CANVAS)) }
        waitAdvancing { (findCanvas(composeRule.activity.window.decorView)?.width ?: 0) > 0 }
    }

    @Test
    fun pdfNoteHasOneInkPagePerPdfPageSizedLikeThePdf() {
        val id = createPdfNoteWithMissingFile()
        val p = pages(id)
        assertThat(p.map { it.info.pdfPageIndex }).containsExactly(0, 1).inOrder()
        assertThat(p.map { it.info.width to it.info.height }).containsExactly(794f to 1123f, 1123f to 794f).inOrder()
    }

    @Test
    fun missingPdfFileShowsAMessageInsteadOfCrashing() {
        val id = createPdfNoteWithMissingFile()
        // The library card still shows (title from the file name, thumbnail without the PDF page).
        waitAdvancing { exists(hasText("Lecture 5")) }
        openNote(id)
        waitAdvancing { exists(hasTestTag(InkTestTags.PDF_FAILED)) }
        assertThat(exists(hasText(string(R.string.pdf_cant_display)))).isTrue()

        // A PDF's pages come from the document: no adding, deleting or changing templates. Export needs the file.
        composeRule.onNodeWithContentDescription(string(R.string.ink_more_options)).performClick()
        composeRule.waitForIdle()
        assertThat(exists(hasText(string(R.string.ink_page_add)))).isFalse()
        assertThat(exists(hasText(string(R.string.ink_page_template)))).isFalse()
        assertThat(exists(hasText(string(R.string.ink_page_delete)))).isFalse()
        assertThat(exists(hasTestTag(InkTestTags.EXPORT_PDF) and isNotEnabled())).isTrue()
        assertThat(exists(hasTestTag(InkTestTags.EXPORT_PDF) and isEnabled())).isFalse()
    }

    @Test
    fun handwritingOnAPdfPageIsSaved() {
        val id = createPdfNoteWithMissingFile()
        openNote(id)
        composeRule.runOnUiThread {
            val view = findCanvas(composeRule.activity.window.decorView)!!
            val down = SystemClock.uptimeMillis()
            var t = down
            val props = MotionEvent.PointerProperties().apply { this.id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
            fun send(action: Int, x: Float, y: Float) {
                val c = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 0.6f }
                val e = MotionEvent.obtain(down, t, action, 1, arrayOf(props), arrayOf(c), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
                view.dispatchTouchEvent(e)
                e.recycle()
            }
            send(MotionEvent.ACTION_DOWN, 60f, 200f)
            for (i in 1..24) {
                t += 8
                send(MotionEvent.ACTION_MOVE, 60f + i * 11f, 200f + i * 2f)
            }
            t += 8
            send(MotionEvent.ACTION_UP, 324f, 248f)
        }
        waitAdvancing { pages(id).first().items.size == 1 }
        assertThat(pages(id)[1].items).isEmpty()
    }

    @Test
    fun importAndScanAreOfferedInTheCreateMenu() {
        waitAdvancing { exists(hasTestTag(LibraryTestTags.SCREEN)) }
        composeRule.onNodeWithTag(LibraryTestTags.CREATE).performClick()
        composeRule.waitForIdle()
        assertThat(exists(hasTestTag(LibraryTestTags.IMPORT_FILE))).isTrue()
        assertThat(exists(hasTestTag(LibraryTestTags.SCAN))).isTrue()
    }

    /** SRS 7.1 page navigation and page count: the grid jumps to a page, the indicator follows. */
    @Test
    fun pageGridJumpsToTheChosenPage() {
        // Four A4 pages: taller than the screen, so the current page follows scrolling.
        val id = createPdfNoteWithMissingFile(List(4) { 794f to 1123f })
        openNote(id)
        waitAdvancing { exists(hasTestTag(InkTestTags.PAGE_INDICATOR)) }
        assertThat(exists(hasText("1 / 4"), unmerged = true)).isTrue()

        composeRule.onNodeWithContentDescription(string(R.string.ink_more_options)).performClick()
        composeRule.onNodeWithTag(InkTestTags.GO_TO_PAGE).performClick()
        waitAdvancing { exists(hasTestTag(InkTestTags.PAGES_DIALOG)) }
        composeRule.onNodeWithTag(InkTestTags.pageCell(2)).performClick()
        waitAdvancing { exists(hasText("3 / 4"), unmerged = true) }
        assertThat(exists(hasTestTag(InkTestTags.PAGES_DIALOG))).isFalse()
    }

    /**
     * INK-SH-07: zoom a PDF page, correct a shape there, and it is stored in page coordinates, so it stays
     * anchored to the page at any zoom (export alignment of shapes is checked on the emulator, PdfDeviceTest).
     */
    @Test
    fun shapeCorrectedWhileZoomedIsAnchoredToThePage() {
        val id = createPdfNoteWithMissingFile(List(2) { 794f to 1123f })
        openNote(id)
        // Reference: a 280 px line at the normal zoom.
        pen(line(60f, 200f, 340f, 200f))
        waitAdvancing { pages(id)[0].items.size == 1 }
        val unitsPerPxBefore = (pages(id)[0].items.single().bounds().width) / 280.0

        pinch(from = 50f, to = 100f, centerX = 200f, centerY = 400f) // 2× zoom
        pen(rectangle(100f, 300f, 300f, 450f), holdMillis = 700)
        waitAdvancing { pages(id)[0].items.any { it is ShapeItem } }
        val shape = pages(id)[0].items.filterIsInstance<ShapeItem>().single()
        assertThat(shape.kind).isEqualTo(ShapeKind.RECTANGLE)
        // 200 px drawn at 2× = half as many page units as at 1×.
        assertThat(shape.bounds().width / (200 * unitsPerPxBefore)).isWithin(0.05).of(0.5)
        assertThat(pages(id)[1].items).isEmpty() // on the page it was drawn on

        // Reopen: same geometry on the same page, whatever the zoom now.
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        openNote(id)
        assertThat(pages(id)[0].items.filterIsInstance<ShapeItem>().single()).isEqualTo(shape)
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 25) =
        List(n) { i -> val f = i / (n - 1f); (x0 + (x1 - x0) * f) to (y0 + (y1 - y0) * f) }

    private fun rectangle(x0: Float, y0: Float, x1: Float, y1: Float): List<Pair<Float, Float>> =
        line(x0, y0, x1, y0) + line(x1, y0, x1, y1).drop(1) + line(x1, y1, x0, y1).drop(1) + line(x0, y1, x0, y0).drop(1)

    /** One stylus stroke through the canvas, MOVEs 8 ms apart, then optionally held still at the end. */
    private fun pen(points: List<Pair<Float, Float>>, holdMillis: Long = 0) {
        composeRule.runOnUiThread {
            val view = findCanvas(composeRule.activity.window.decorView)!!
            val down = SystemClock.uptimeMillis()
            var t = down
            val props = MotionEvent.PointerProperties().apply { this.id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
            fun send(action: Int, p: Pair<Float, Float>) {
                val c = MotionEvent.PointerCoords().apply { x = p.first; y = p.second; pressure = 0.6f }
                val e = MotionEvent.obtain(down, t, action, 1, arrayOf(props), arrayOf(c), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
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

    /** Two fingers moving apart from ±[from] to ±[to] px around the centre (2× zoom for 50 → 100). */
    private fun pinch(from: Float, to: Float, centerX: Float, centerY: Float) {
        composeRule.runOnUiThread {
            val view = findCanvas(composeRule.activity.window.decorView)!!
            val down = SystemClock.uptimeMillis()
            var t = down
            val props = Array(2) { i -> MotionEvent.PointerProperties().apply { this.id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            fun send(action: Int, count: Int, half: Float) {
                val coords = Array(count) { i -> MotionEvent.PointerCoords().apply { x = centerX + (if (i == 0) -half else half); y = centerY } }
                val e = MotionEvent.obtain(down, t, action, count, props.copyOf(count).requireNoNulls(), coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                view.dispatchTouchEvent(e)
                e.recycle()
            }
            val second = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
            send(MotionEvent.ACTION_DOWN, 1, from)
            t += 8; send(MotionEvent.ACTION_POINTER_DOWN or second, 2, from)
            for (k in 1..10) { t += 8; send(MotionEvent.ACTION_MOVE, 2, from + (to - from) * k / 10) }
            t += 8; send(MotionEvent.ACTION_POINTER_UP or second, 2, to)
            t += 8; send(MotionEvent.ACTION_UP, 1, to)
        }
        composeRule.waitForIdle()
    }
}
