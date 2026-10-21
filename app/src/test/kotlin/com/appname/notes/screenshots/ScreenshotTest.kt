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
import com.appname.notes.feature.editor.EditorTestTags
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
}
