package com.appname.notes

import android.os.Looper
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteSort
import java.time.Duration
import org.robolectric.Shadows
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.di.AppDataEntryPoint
import com.appname.notes.feature.editor.EditorTestTags
import com.appname.notes.feature.library.LibraryTestTags
import com.appname.notes.feature.search.SearchTestTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** End-to-end flows through the real app (real Room database) on a simulated phone. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class NotesFlowRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val data by lazy { EntryPointAccessors.fromApplication(composeRule.activity.application, AppDataEntryPoint::class.java) }

    private fun string(id: Int) = composeRule.activity.getString(id)

    /**
     * Robolectric's main-thread clock only moves when told to. Autosave and search wait a few hundred
     * milliseconds (debounce), so advance the clock while polling.
     */
    private fun waitAdvancing(condition: () -> Boolean) = composeRule.waitUntil(15_000) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        condition()
    }

    private fun exists(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(tag: String) = waitAdvancing { exists(hasTestTag(tag)) }

    private fun waitForText(text: String) = waitAdvancing { exists(hasText(text, substring = true)) }

    private fun allNotes() = runBlocking { data.notes().observeNotes(NoteCollection.All, NoteSort.MODIFIED).first() }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun createNote(title: String, body: String) {
        composeRule.onNodeWithTag(LibraryTestTags.NEW_NOTE).performClick()
        waitFor(EditorTestTags.BODY)
        composeRule.onNodeWithTag(EditorTestTags.TITLE).performTextInput(title)
        composeRule.onNodeWithTag(EditorTestTags.BODY).performTextInput(body)
    }

    @Test
    fun newNoteIsSavedAndShownInTheLibrary() {
        createNote("Lecture 4", "Chain rule and product rule")
        // Autosave after a pause in typing.
        waitAdvancing { allNotes().any { it.title == "Lecture 4" } }
        waitForText(string(R.string.editor_saved))

        pressBack()
        waitFor(LibraryTestTags.SCREEN)
        waitForText("Lecture 4")
        waitForText("Chain rule and product rule")
    }

    @Test
    fun leavingTheEditorImmediatelyStillSaves() {
        createNote("Quick", "typed and left at once")
        pressBack() // no waiting for the autosave delay
        waitFor(LibraryTestTags.SCREEN)
        waitForText("typed and left at once")
    }

    @Test
    fun emptyNewNoteIsDiscardedWhenLeft() {
        composeRule.onNodeWithTag(LibraryTestTags.NEW_NOTE).performClick()
        waitFor(EditorTestTags.BODY)
        pressBack()
        waitFor(LibraryTestTags.SCREEN)
        waitAdvancing { allNotes().isEmpty() }
        waitForText(string(R.string.home_empty_title))
    }

    @Test
    fun reopenedNoteShowsSavedText() {
        createNote("Reopen me", "persisted body")
        pressBack()
        waitForText("Reopen me")
        composeRule.onNodeWithText("Reopen me").performClick()
        waitFor(EditorTestTags.BODY)
        waitAdvancing { exists(hasTestTag(EditorTestTags.BODY) and hasText("persisted body", substring = true)) }
    }

    @Test
    fun searchFindsNoteAndOpensIt() {
        createNote("Physics", "Newton's second law F = ma")
        pressBack()
        waitForText("Physics")
        composeRule.onNodeWithTag(LibraryTestTags.SEARCH).performClick()
        waitFor(SearchTestTags.FIELD)
        composeRule.onNodeWithTag(SearchTestTags.FIELD).performTextInput("newt")
        waitFor(SearchTestTags.RESULTS)
        composeRule.onNodeWithText("Physics").performClick()
        waitFor(EditorTestTags.BODY)
    }

    @Test
    fun trashAndRestoreFromTheLibrary() {
        createNote("Disposable", "to trash")
        pressBack()
        waitForText("Disposable")

        composeRule.onNodeWithText("Disposable").performTouchInput { longClick() }
        composeRule.onNodeWithText(string(R.string.note_trash)).performClick()
        waitAdvancing { !exists(hasText("Disposable")) }

        composeRule.onNodeWithTag(LibraryTestTags.COLLECTION_BUTTON).performClick()
        composeRule.onNodeWithText(string(R.string.collection_trash)).performClick()
        waitForText("Disposable")
        composeRule.onNodeWithText("Disposable").performTouchInput { longClick() }
        composeRule.onNodeWithText(string(R.string.note_restore)).performClick()
        waitAdvancing { !exists(hasText("Disposable")) }

        composeRule.onNodeWithTag(LibraryTestTags.COLLECTION_BUTTON).performClick()
        composeRule.onNodeWithText(string(R.string.collection_all)).performClick()
        waitForText("Disposable")
    }

    @Test
    fun noteInANotebookFlow() {
        val notebook = runBlocking { data.notebooks().create("Maths") }
        assertThat(notebook).isInstanceOf(com.appname.notes.core.common.result.AppResult.Success::class.java)
        composeRule.onNodeWithTag(LibraryTestTags.COLLECTION_BUTTON).performClick()
        waitForText("Maths")
        composeRule.onNodeWithText("Maths").performClick()

        createNote("Algebra", "in a notebook")
        pressBack()
        waitForText("Algebra")
        // The note was created inside the notebook being viewed.
        waitAdvancing { allNotes().any { it.title == "Algebra" } }
        val saved = allNotes().single { it.title == "Algebra" }
        assertThat(saved.notebookTitle).isEqualTo("Maths")
    }
}
