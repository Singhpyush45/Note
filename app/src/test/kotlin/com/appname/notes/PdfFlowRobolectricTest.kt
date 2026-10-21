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
    private fun createPdfNoteWithMissingFile(): NoteId = runBlocking {
        val doc = PdfDocumentInfo("missing-${System.nanoTime()}.pdf", "Lecture 5.pdf", "0".repeat(64), 1234, 2)
        (data.pdf().createPdfNote(null, "Lecture 5", doc, listOf(794f to 1123f, 1123f to 794f)) as AppResult.Success).value
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
    fun importPdfIsOfferedInTheLibraryMenu() {
        waitAdvancing { exists(hasTestTag(LibraryTestTags.SCREEN)) }
        composeRule.onNodeWithContentDescription(string(R.string.action_more)).performClick()
        composeRule.waitForIdle()
        assertThat(exists(hasTestTag(LibraryTestTags.IMPORT_PDF))).isTrue()
    }
}
