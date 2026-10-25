package com.appname.notes.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.ink.model.PageTemplate
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The home screen's parts on their own (owner brief §2–3): the + menu, the selection bar, rename, templates. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class HomePartsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun exists(tag: String) = composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private val picked = mutableListOf<String>()
    private val actions = CreateActions(
        blankNote = { picked += "blank" },
        chooseTemplate = { picked += "template" },
        scanNote = { picked += "scan" },
        importFile = { picked += "import" },
    )

    private fun setMenu() {
        composeRule.setContent {
            var open by remember { mutableStateOf(false) }
            CreateMenuScrim(open) { open = false }
            CreateMenu(expanded = open, onExpandedChange = { open = it }, actions = actions)
        }
    }

    @Test
    fun plusOpensTheFourChoicesAndAChoiceClosesTheMenu() {
        setMenu()
        assertThat(exists(LibraryTestTags.NEW_NOTE)).isFalse()
        composeRule.onNodeWithTag(LibraryTestTags.CREATE).performClick()
        listOf(LibraryTestTags.NEW_NOTE, LibraryTestTags.CHOOSE_TEMPLATE, LibraryTestTags.SCAN, LibraryTestTags.IMPORT_FILE).forEach {
            composeRule.onNodeWithTag(it).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(LibraryTestTags.SCAN).performClick()
        composeRule.waitForIdle()
        assertThat(picked).containsExactly("scan")
        assertThat(exists(LibraryTestTags.NEW_NOTE)).isFalse()
    }

    @Test
    fun menuClosesWithThePlusTheScrimAndBack() {
        setMenu()
        val plus = composeRule.onNodeWithTag(LibraryTestTags.CREATE)
        plus.performClick()
        plus.performClick() // now ×
        composeRule.waitForIdle()
        assertThat(exists(LibraryTestTags.NEW_NOTE)).isFalse()

        plus.performClick()
        composeRule.onNodeWithTag(LibraryTestTags.CREATE_SCRIM).performClick()
        composeRule.waitForIdle()
        assertThat(exists(LibraryTestTags.NEW_NOTE)).isFalse()

        plus.performClick()
        composeRule.waitForIdle() // Back is only taken over once the open menu has been composed
        composeRule.onNodeWithTag(LibraryTestTags.NEW_NOTE).assertIsDisplayed()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertThat(exists(LibraryTestTags.NEW_NOTE)).isFalse()
        assertThat(picked).isEmpty()
    }

    @Test
    fun selectionBarOffersTheRightActionsPerCollection() {
        var collection by mutableStateOf<NoteCollection>(NoteCollection.All)
        val done = mutableListOf<String>()
        composeRule.setContent {
            SelectionBar(
                count = 2,
                collection = collection,
                allPinned = false,
                actions = SelectionActions(
                    clear = { done += "clear" }, selectAll = { done += "all" }, pin = { done += "pin" }, move = { done += "move" },
                    archive = { done += "archive" }, trash = { done += "trash" }, restore = { done += "restore" },
                    deleteForever = { done += "delete" },
                ),
            )
        }
        listOf(LibraryTestTags.SELECTION_PIN, LibraryTestTags.SELECTION_MOVE, LibraryTestTags.SELECTION_ARCHIVE, LibraryTestTags.SELECTION_TRASH)
            .forEach { composeRule.onNodeWithTag(it).performClick() }
        assertThat(exists(LibraryTestTags.SELECTION_RESTORE)).isFalse()
        assertThat(done).containsExactly("pin", "move", "archive", "trash").inOrder()

        collection = NoteCollection.Trash
        composeRule.waitForIdle()
        assertThat(exists(LibraryTestTags.SELECTION_TRASH)).isFalse()
        assertThat(exists(LibraryTestTags.SELECTION_PIN)).isFalse()
        composeRule.onNodeWithTag(LibraryTestTags.SELECTION_RESTORE).performClick()
        composeRule.onNodeWithTag(LibraryTestTags.SELECTION_DELETE).performClick()
        composeRule.onNodeWithTag(LibraryTestTags.SELECTION_CLEAR).performClick()
        assertThat(done.takeLast(3)).containsExactly("restore", "delete", "clear").inOrder()
    }

    @Test
    fun renameDialogReturnsTheTypedName() {
        var renamed: String? = null
        composeRule.mainClock.autoAdvance = false // a focused field's cursor blinks forever
        composeRule.setContent { RenameNoteDialog(current = "Lecture", onRename = { renamed = it }, onDismiss = {}) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag(LibraryTestTags.RENAME_FIELD).performTextReplacement("Lecture 5")
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag(LibraryTestTags.RENAME_SAVE).performClick()
        composeRule.mainClock.advanceTimeBy(100)
        assertThat(renamed).isEqualTo("Lecture 5")
    }

    @Test
    fun templatePickerReturnsTheChosenPageStyle() {
        var chosen: PageTemplate? = null
        var dismissed = false
        composeRule.setContent { TemplatePickerDialog(onPick = { chosen = it }, onDismiss = { dismissed = true }) }
        PageTemplate.entries.forEach { composeRule.onNodeWithTag(LibraryTestTags.template(it)).assertIsDisplayed() }
        composeRule.onNodeWithTag(LibraryTestTags.template(PageTemplate.GRID)).performClick()
        assertThat(chosen).isEqualTo(PageTemplate.GRID)
        assertThat(dismissed).isTrue()
    }
}
