package com.appname.notes

import android.os.Looper
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.ink.template.BuiltInTemplates
import com.appname.notes.core.ink.template.Ruling
import com.appname.notes.core.ink.template.TemplateCategory
import com.appname.notes.di.AppDataEntryPoint
import com.appname.notes.feature.ink.InkTestTags
import com.appname.notes.feature.library.LibraryTestTags
import com.appname.notes.feature.settings.SettingsTestTags
import com.appname.notes.feature.templates.TemplateEditorTestTags
import com.appname.notes.feature.templates.TemplatesTestTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Templates through the real app (owner brief §4): choose, use, search, make and delete your own. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class TemplatesFlowRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val data by lazy { EntryPointAccessors.fromApplication(composeRule.activity.application, AppDataEntryPoint::class.java) }

    private fun waitAdvancing(condition: () -> Boolean) = composeRule.waitUntil(15_000) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        condition()
    }

    private fun exists(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = waitAdvancing { exists(hasTestTag(tag)) }
    private fun click(tag: String) {
        waitFor(tag)
        composeRule.onNodeWithTag(tag).performClick()
    }

    /** Options further down the editor form are scrolled into view first (a tap off-screen would miss). */
    private fun editorOption(tag: String) {
        waitFor(TemplateEditorTestTags.FORM)
        composeRule.onNodeWithTag(TemplateEditorTestTags.FORM).performScrollToNode(hasTestTag(tag))
        click(tag)
    }

    private fun allNotes() = runBlocking { data.notes().observeNotes(NoteCollection.All, NoteSort.MODIFIED).first() }

    private fun openTemplates() {
        click(LibraryTestTags.CREATE)
        click(LibraryTestTags.CHOOSE_TEMPLATE)
        waitFor(TemplatesTestTags.SCREEN)
    }

    /** Scrolls the grid to a template (tiles further down aren't composed until then). */
    private fun showTile(id: String) {
        waitFor(TemplatesTestTags.GRID)
        composeRule.onNodeWithTag(TemplatesTestTags.GRID).performScrollToNode(hasTestTag(TemplatesTestTags.tile(id)))
    }

    @Test
    fun aPlannerBecomesAHandwrittenNoteWithItsDesign() {
        waitFor(LibraryTestTags.SCREEN)
        openTemplates()
        // Every category is there, starting with Basic.
        TemplateCategory.entries.forEach { waitFor(TemplatesTestTags.chip(it)) }
        click(TemplatesTestTags.chip(TemplateCategory.PLANNERS))
        showTile("planners.weekly")
        click(TemplatesTestTags.tile("planners.weekly"))
        click(TemplatesTestTags.USE)
        waitFor(InkTestTags.CANVAS)

        waitAdvancing { allNotes().size == 1 }
        val note = allNotes().single()
        assertThat(note.type).isEqualTo(NoteType.INK)
        val page = (runBlocking { data.ink().loadPages(note.id) } as AppResult.Success).value.single().info
        assertThat(page.layout).isEqualTo(BuiltInTemplates.byId("planners.weekly")!!.layout)

        // Back goes to the notes, not to the template screen.
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor(LibraryTestTags.SCREEN)
        assertThat(exists(hasTestTag(TemplatesTestTags.SCREEN))).isFalse()
    }

    @Test
    fun aTemplateNoteGoesIntoTheNotebookBeingShown() {
        val maths = runBlocking { (data.notebooks().create("Maths") as AppResult.Success).value }
        val notebook = runBlocking { data.notebooks().observeNotebooks().first() }.single()
        click(LibraryTestTags.chip(notebook))
        openTemplates()
        click(TemplatesTestTags.tile("basic.grid"))
        click(TemplatesTestTags.USE)
        waitFor(InkTestTags.CANVAS)
        waitAdvancing { allNotes().size == 1 }
        assertThat(allNotes().single().notebookId).isEqualTo(maths)
    }

    @Test
    fun searchNarrowsTheTemplates() {
        openTemplates()
        click(TemplatesTestTags.SEARCH)
        waitFor(TemplatesTestTags.SEARCH_FIELD)
        composeRule.onNodeWithTag(TemplatesTestTags.SEARCH_FIELD).performTextInput("budget")
        waitFor(TemplatesTestTags.tile("planners.budget"))
        waitAdvancing { !exists(hasTestTag(TemplatesTestTags.tile("basic.lined"))) }
        composeRule.onNodeWithTag(TemplatesTestTags.SEARCH_FIELD).performTextInput("zzz")
        waitFor(TemplatesTestTags.EMPTY_SEARCH)
    }

    @Test
    fun makeUseAndDeleteYourOwnTemplate() {
        // Settings › Templates › New template
        click("nav_settings")
        waitFor(SettingsTestTags.LIST)
        composeRule.onNodeWithTag(SettingsTestTags.LIST).performScrollToNode(hasTestTag(SettingsTestTags.TEMPLATES))
        click(SettingsTestTags.TEMPLATES)
        click(TemplatesTestTags.NEW)
        waitFor(TemplateEditorTestTags.NAME)
        composeRule.onNodeWithTag(TemplateEditorTestTags.NAME).performTextInput("Exam grid")
        editorOption(TemplateEditorTestTags.ruling(Ruling.GRID))
        click(TemplateEditorTestTags.SAVE)

        // Back on the templates screen, in Custom.
        waitAdvancing { runBlocking { data.templates().observeCustom().first() }.size == 1 }
        val mine = runBlocking { data.templates().observeCustom().first() }.single()
        assertThat(mine.name).isEqualTo("Exam grid")
        assertThat(mine.layout.paper.ruling).isEqualTo(Ruling.GRID)
        showTile(mine.id)

        // Use it…
        click(TemplatesTestTags.tile(mine.id))
        click(TemplatesTestTags.USE)
        waitFor(InkTestTags.CANVAS)
        waitAdvancing { allNotes().size == 1 }
        val page = (runBlocking { data.ink().loadPages(allNotes().single().id) } as AppResult.Success).value.single().info
        assertThat(page.layout).isEqualTo(mine.layout)

        // …then delete it: the note keeps its design.
        runBlocking { data.templates().delete(mine.id) }
        waitAdvancing { runBlocking { data.templates().observeCustom().first() }.isEmpty() }
        val again = (runBlocking { data.ink().loadPages(allNotes().single().id) } as AppResult.Success).value.single().info
        assertThat(again.layout).isEqualTo(mine.layout)
    }

    @Test
    fun builtInsAreCustomisedAsACopy() {
        openTemplates()
        click(TemplatesTestTags.chip(TemplateCategory.JOURNALS))
        showTile("journals.daily")
        click(TemplatesTestTags.tile("journals.daily"))
        click(TemplatesTestTags.EDIT) // "Customise"
        waitFor(TemplateEditorTestTags.PREVIEW)
        val copy = runBlocking { data.templates().observeCustom().first() }.single()
        assertThat(copy.name).isEqualTo("Daily journal copy")
        assertThat(copy.layout).isEqualTo(BuiltInTemplates.byId("journals.daily")!!.layout)
        editorOption(TemplateEditorTestTags.format(com.appname.notes.core.ink.template.PageFormat.A4_LANDSCAPE))
        click(TemplateEditorTestTags.SAVE)
        waitAdvancing { runBlocking { data.templates().get(copy.id) }!!.layout.width == 1123f }
        // The built-in itself is unchanged.
        assertThat(BuiltInTemplates.byId("journals.daily")!!.layout.width).isEqualTo(794f)
    }
}
