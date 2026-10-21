package com.appname.notes.feature.editor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.R
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.SpanType
import com.appname.notes.core.domain.document.StyleSpan
import com.appname.notes.core.domain.editor.EditorState
import com.appname.notes.core.domain.editor.ParagraphMeta
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The editor screen with a real controller, without database or ViewModel. */
// Real text measurement is needed to tap a checkbox at a pixel position.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class EditorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var n = 0
    private fun string(id: Int) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private val noActions = EditorMenuActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun show(controller: EditorController, readOnly: ReadOnlyReason? = null) = composeRule.setContent {
        AppTheme {
            NoteEditorScreen(
                load = EditorLoad.Ready(controller),
                readOnly = readOnly,
                saveStatus = SaveStatus.SAVED,
                meta = NoteMeta(isPinned = false, isFavourite = false, isArchived = false, inTrash = false, notebookId = null, tags = emptyList()),
                snackbar = SnackbarHostState(),
                showBack = true,
                onBack = {},
                actions = noActions,
            )
        }
    }

    private fun controller(state: EditorState = EditorState("", listOf(ParagraphMeta("p0")))) =
        EditorController(state, "", newId = { "n${n++}" })

    @Test
    fun typingUpdatesTheDocument() {
        val c = controller()
        show(c)
        composeRule.onNodeWithTag(EditorTestTags.BODY).performTextInput("hello")
        composeRule.onNodeWithTag(EditorTestTags.TITLE).performTextInput("My title")
        composeRule.waitForIdle()
        assertThat(c.state.text).isEqualTo("hello")
        assertThat(c.title).isEqualTo("My title")
        assertThat(c.canUndo).isTrue()
    }

    @Test
    fun boldButtonStylesTheSelection() {
        val c = controller()
        show(c)
        val body = composeRule.onNodeWithTag(EditorTestTags.BODY)
        body.performTextInput("make bold")
        body.performTextInputSelection(TextRange(5, 9))
        composeRule.onNodeWithContentDescription(string(R.string.fmt_bold)).performClick()
        composeRule.waitForIdle()
        assertThat(c.state.spans).containsExactly(StyleSpan(5, 9, SpanType.BOLD))
    }

    @Test
    fun listButtonsChangeTheCurrentLine() {
        val c = controller()
        show(c)
        composeRule.onNodeWithTag(EditorTestTags.BODY).performTextInput("milk")
        composeRule.onNodeWithContentDescription(string(R.string.fmt_checklist)).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertThat(c.state.paragraphs.single().kind).isEqualTo(BlockKind.CHECKLIST)
    }

    @Test
    fun tappingACheckboxTicksIt() {
        val c = controller(EditorState("buy milk", listOf(ParagraphMeta("p0", BlockKind.CHECKLIST))))
        show(c)
        composeRule.onNodeWithTag(EditorTestTags.BODY).performTouchInput { click(Offset(6f, 12f)) }
        composeRule.waitForIdle()
        assertThat(c.state.paragraphs.single().checked).isTrue()
        assertThat(c.state.text).isEqualTo("buy milk") // the box isn't part of the text
    }

    @Test
    fun undoAndRedoButtonsWork() {
        val c = controller()
        show(c)
        composeRule.onNodeWithTag(EditorTestTags.BODY).performTextInput("abc")
        composeRule.onNodeWithContentDescription(string(R.string.editor_undo)).performClick()
        composeRule.waitForIdle()
        assertThat(c.state.text).isEmpty()
        composeRule.onNodeWithContentDescription(string(R.string.editor_redo)).performClick()
        composeRule.waitForIdle()
        assertThat(c.state.text).isEqualTo("abc")
    }

    @Test
    fun readOnlyNotesHideTheToolbar() {
        show(controller(EditorState("old", listOf(ParagraphMeta("p0")))), readOnly = ReadOnlyReason.NEWER_VERSION)
        composeRule.onNodeWithTag(EditorTestTags.TOOLBAR).assertDoesNotExist()
        composeRule.onNodeWithTag(EditorTestTags.STATUS).assertDoesNotExist()
    }
}
