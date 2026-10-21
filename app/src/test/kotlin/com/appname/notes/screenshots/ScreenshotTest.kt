package com.appname.notes.screenshots

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.MainActivity
import com.appname.notes.R
import com.appname.notes.core.common.result.getOrNull
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.SpanType
import com.appname.notes.core.domain.document.StyleSpan
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.di.AppDataEntryPoint
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.ink.codec.InkCodec
import com.appname.notes.core.ink.edit.InkEdit
import com.appname.notes.core.ink.input.EditorTool
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.model.Pt
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import com.appname.notes.feature.editor.EditorTestTags
import com.appname.notes.feature.ink.InkTestTags
import com.appname.notes.feature.library.LibraryTestTags
import com.appname.notes.feature.search.SearchTestTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders real screens with sample notes to PNG files in app/build/screenshots (CI publishes them to the
 * `ci-output` branch) so the UI can be reviewed without a device. Also checks the adaptive layouts.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTestBase(private val device: String) {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val data by lazy { EntryPointAccessors.fromApplication(composeRule.activity.application, AppDataEntryPoint::class.java) }

    protected fun string(id: Int) = composeRule.activity.getString(id)

    protected fun waitAdvancing(condition: () -> Boolean) = composeRule.waitUntil(15_000) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        condition()
    }

    protected fun exists(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    protected fun snap(name: String) {
        waitAdvancing { true }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "${device}_$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun id() = UUID.randomUUID().toString()

    private fun block(kind: BlockKind, text: String, spans: List<StyleSpan> = emptyList(), checked: Boolean = false) =
        TextBlock(id(), kind, RichText(text, spans), checked)

    /** Realistic sample content (student persona, SRS 4.1). */
    protected fun seed() = runBlocking {
        val notes = data.notes()
        val calculus = data.notebooks().create("Calculus").getOrNull()!!
        data.notebooks().create("Personal")
        val exam = data.tags().createOrGet("exam").getOrNull()!!

        val lecture = notes.createNote(
            "Lecture 4 — Chain rule",
            NoteDocument(
                blocks = listOf(
                    block(BlockKind.HEADING2, "Key idea"),
                    block(
                        BlockKind.PARAGRAPH,
                        "Differentiate the outer function, then multiply by the derivative of the inner one.",
                        listOf(StyleSpan(0, 34, SpanType.BOLD)),
                    ),
                    block(BlockKind.BULLET, "d/dx sin(x²) = 2x·cos(x²)", listOf(StyleSpan(0, 25, SpanType.CODE))),
                    block(BlockKind.BULLET, "Works for any composition f(g(x))"),
                    block(BlockKind.HEADING2, "To revise"),
                    block(BlockKind.CHECKLIST, "Product rule examples", checked = true),
                    block(BlockKind.CHECKLIST, "Past paper Q3 and Q7"),
                    block(BlockKind.QUOTE, "Practice beats rereading."),
                ),
            ),
            calculus,
        ).getOrNull()!!
        notes.setTags(lecture, setOf(exam.id))
        notes.setPinned(setOf(lecture), true)

        notes.createNote(
            "Groceries",
            NoteDocument(blocks = listOf("Milk", "Eggs", "Rice").map { block(BlockKind.CHECKLIST, it) }),
            null,
        ).getOrNull()!!.also { notes.setFavourite(setOf(it), true) }
        notes.createNote(
            "",
            NoteDocument(blocks = listOf(block(BlockKind.PARAGRAPH, "Idea: a study timer that blocks distracting apps."))),
            null,
        )
        notes.createNote(
            "Project meeting",
            NoteDocument(
                blocks = listOf(
                    block(BlockKind.NUMBERED, "Finalise the SRS"),
                    block(BlockKind.NUMBERED, "Design system review"),
                    block(BlockKind.NUMBERED, "Phase 2 demo on Friday"),
                ),
            ),
            null,
        )
        lecture
    }

    /** A handwritten note with cursive-like writing, a highlight and corrected shapes. */
    protected fun seedInk(): com.appname.notes.core.domain.model.NoteId = runBlocking {
        val ink = data.ink()
        val noteId = ink.createInkNote(null, PageTemplate.LINED).getOrNull()!!
        data.notes().setTitle(noteId, "Physics — waves")
        val page = (ink.loadPages(noteId) as AppResult.Success).value.first().info.id
        val pen = InkBrush(InkTool.PEN, 0xFF1F1F1F.toInt(), 2.5f)
        val blue = InkBrush(InkTool.PEN, 0xFF1A73E8.toInt(), 2.5f)
        val items = mutableListOf<InkItem>()
        var z = 1L
        // "Words": loops along a baseline, like joined-up writing.
        fun word(x0: Float, baseline: Float, loops: Int, brush: InkBrush) {
            val b = StrokePoints.Builder(hasPressure = true, hasTilt = false, hasOrientation = false)
            val n = loops * 14
            for (i in 0..n) {
                val t = i / 14.0 * 2 * Math.PI
                val x = x0 + 9 * t - 13 * kotlin.math.sin(t) // loops: 13 > 9
                val y = baseline - 11 - 11 * kotlin.math.cos(t)
                b.add(x.toFloat(), y.toFloat(), i * 8, (0.45 + 0.35 * kotlin.math.sin(t / 3)).toFloat())
            }
            items += InkCodec.quantize(StrokeItem(id(), z++, brush, b.build()))
        }
        var x = 72f
        for (w in listOf(4, 3, 5)) { word(x, 120f, w, pen); x += w * 57f + 24f }
        x = 72f
        for (w in listOf(3, 6, 2, 4)) { word(x, 180f, w, blue); x += w * 57f + 24f }
        // Highlight under the second line.
        val hl = StrokePoints.Builder(false, false, false).apply { for (i in 0..20) add(70f + i * 17f, 172f, i * 8) }.build()
        items += StrokeItem(id(), z++, InkBrush(InkTool.HIGHLIGHTER, 0x66FFEB3B, 18f), hl)
        // Corrected shapes: a box with an arrow to a circle, and an underline.
        items += ShapeItem(id(), z++, pen, ShapeKind.RECTANGLE, ShapeGeometry.Polygon(listOf(Pt(80f, 260f), Pt(330f, 260f), Pt(330f, 400f), Pt(80f, 400f))), null)
        items += ShapeItem(id(), z++, blue, ShapeKind.ARROW, ShapeGeometry.Segment(Pt(340f, 330f), Pt(470f, 330f), 22f), null)
        items += ShapeItem(id(), z++, pen, ShapeKind.CIRCLE, ShapeGeometry.Ellipse(Pt(560f, 330f), 75f, 75f, 0f), null)
        items += ShapeItem(id(), z++, InkBrush(InkTool.MARKER, 0xFFD93025.toInt(), 4f), ShapeKind.LINE, ShapeGeometry.Segment(Pt(72f, 450f), Pt(420f, 450f)), null)
        items += ShapeItem(id(), z++, pen, ShapeKind.TRIANGLE, ShapeGeometry.Polygon(listOf(Pt(120f, 640f), Pt(220f, 480f), Pt(320f, 640f))), null)
        ink.commit(page, InkEdit(emptyList(), items))
        noteId
    }

    @Test
    fun captureHandwriting() {
        seed()
        seedInk()
        waitAdvancing { exists(hasText("Physics — waves")) }
        snap("library_with_ink_light")
        composeRule.onNodeWithText("Physics — waves").performClick()
        waitAdvancing { exists(hasTestTag(InkTestTags.CANVAS)) }
        snap("ink_light")
        composeRule.onNodeWithTag(InkTestTags.tool(EditorTool.ERASER)).performClick()
        snap("ink_eraser_tools_light")
        pressBack()
        setTheme(R.string.settings_theme_dark)
        composeRule.onNodeWithText("Physics — waves").performClick()
        waitAdvancing { exists(hasTestTag(InkTestTags.CANVAS)) }
        snap("ink_dark")
    }

    protected fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        waitAdvancing { exists(hasTestTag(LibraryTestTags.SCREEN)) }
    }

    protected fun setTheme(id: Int) {
        composeRule.onNodeWithTag("nav_settings").performClick()
        waitAdvancing { exists(hasText(string(id))) }
        composeRule.onNodeWithText(string(id)).performClick()
        waitAdvancing { exists(hasText(string(id)) and isSelected()) }
        composeRule.onNodeWithTag("nav_home").performClick()
        waitAdvancing { exists(hasTestTag(LibraryTestTags.SCREEN)) }
    }

    @Test
    fun captureScreens() {
        seed()
        waitAdvancing { exists(hasText("Groceries")) }
        snap("library_light")

        composeRule.onNodeWithText("Lecture 4 — Chain rule").performClick()
        waitAdvancing { exists(hasTestTag(EditorTestTags.BODY)) }
        snap("editor_light")
        pressBack() // the bottom bar is hidden while editing on phones

        setTheme(R.string.settings_theme_dark)
        waitAdvancing { exists(hasText("Groceries")) }
        snap("library_dark")

        composeRule.onNodeWithTag(LibraryTestTags.SEARCH).performClick()
        waitAdvancing { exists(hasTestTag(SearchTestTags.FIELD)) }
        composeRule.onNodeWithTag(SearchTestTags.FIELD).performTextInput("deriv")
        waitAdvancing { exists(hasTestTag(SearchTestTags.RESULTS)) }
        snap("search_dark")
    }
}

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneScreenshotTest : ScreenshotTestBase("phone") {

    @Test
    fun phoneUsesBottomNavigationBar() {
        val item = composeRule.onNodeWithTag("nav_settings").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(item.top).isGreaterThan(root.height * 0.75f) // bottom of the screen
    }

    @Test
    fun editorIsFullScreenOnPhones() {
        composeRule.onNodeWithTag(LibraryTestTags.NEW_NOTE).performClick()
        waitAdvancing { exists(hasTestTag(EditorTestTags.BODY)) }
        assertThat(exists(hasTestTag(LibraryTestTags.SCREEN))).isFalse()
        assertThat(exists(hasTestTag("nav_settings"))).isFalse() // bottom bar hidden while editing
    }
}

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp-land-xhdpi")
class TabletScreenshotTest : ScreenshotTestBase("tablet") {

    @Test
    fun tabletUsesNavigationRail() {
        val item = composeRule.onNodeWithTag("nav_settings").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(item.left).isLessThan(root.width * 0.15f) // left edge, not a bottom bar
        assertThat(item.top).isLessThan(root.height * 0.75f)
    }

    @Test
    fun tabletShowsListAndNoteSideBySide() {
        waitAdvancing { exists(hasTestTag("detail_placeholder")) }
        composeRule.onNodeWithTag(LibraryTestTags.NEW_NOTE).performClick()
        waitAdvancing { exists(hasTestTag(EditorTestTags.BODY)) }
        assertThat(exists(hasTestTag(LibraryTestTags.SCREEN))).isTrue()
        val list = composeRule.onNodeWithTag(LibraryTestTags.SCREEN).fetchSemanticsNode().boundsInRoot
        val editor = composeRule.onNodeWithTag(EditorTestTags.SCREEN).fetchSemanticsNode().boundsInRoot
        assertThat(editor.left).isAtLeast(list.right - 1f)
    }

    @Test
    fun handwritingIsFullScreenOnTablets() {
        composeRule.onNodeWithTag(LibraryTestTags.NEW_INK_NOTE).performClick()
        waitAdvancing { exists(hasTestTag(InkTestTags.CANVAS)) }
        assertThat(exists(hasTestTag(LibraryTestTags.SCREEN))).isFalse() // no list pane
        assertThat(exists(hasTestTag("nav_settings"))).isFalse() // no navigation rail
        val canvas = composeRule.onNodeWithTag(InkTestTags.CANVAS).fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(canvas.width).isAtLeast(root.width - 1f)
    }
}
