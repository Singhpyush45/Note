package com.appname.notes

import android.Manifest
import android.os.Looper
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.Reminder
import com.appname.notes.core.domain.model.ReminderPresets
import com.appname.notes.core.domain.model.ReminderRepeat
import com.appname.notes.di.AppDataEntryPoint
import com.appname.notes.feature.editor.EditorTestTags
import com.appname.notes.feature.reminders.ReminderDates
import com.appname.notes.feature.reminders.ReminderTestTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Setting, showing, editing and deleting reminders through the real app (FR-010). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ReminderFlowRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val data by lazy { EntryPointAccessors.fromApplication(composeRule.activity.application, AppDataEntryPoint::class.java) }

    @Before
    fun grant() {
        Shadows.shadowOf(composeRule.activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun string(id: Int, vararg args: Any) = composeRule.activity.getString(id, *args)

    private fun waitAdvancing(condition: () -> Boolean) = composeRule.waitUntil(15_000) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        condition()
    }

    private fun exists(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun reminders(id: NoteId): List<Reminder> = runBlocking { data.reminders().observe(id).first() }

    private fun note(vararg tasks: String): NoteId = runBlocking {
        val id = (data.notes().createNote(null) as AppResult.Success).value
        val blocks = if (tasks.isEmpty()) {
            listOf(TextBlock("b1", text = RichText("Chapter 4 questions")))
        } else {
            tasks.mapIndexed { i, t -> TextBlock("t$i", BlockKind.CHECKLIST, RichText(t)) }
        }
        data.notes().saveContent(id, "Revision", NoteDocument(blocks = blocks))
        id
    }

    private fun openNote(id: NoteId) {
        waitAdvancing { exists(hasTestTag("note_card_${id.value}")) }
        composeRule.onNodeWithTag("note_card_${id.value}").performClick()
        waitAdvancing { exists(hasTestTag(EditorTestTags.BODY)) }
    }

    @Test
    fun setAReminderFromTheMenuThenEditAndDeleteIt() {
        val id = note()
        openNote(id)
        composeRule.onNodeWithContentDescription(string(R.string.action_more)).performClick()
        composeRule.onNodeWithText(string(R.string.reminder_add)).performClick()
        waitAdvancing { exists(hasTestTag(ReminderTestTags.DIALOG)) }

        // "Tomorrow morning", repeat every week.
        composeRule.onNodeWithTag(ReminderTestTags.preset(ReminderPresets.Kind.TOMORROW_MORNING)).performClick()
        composeRule.onNodeWithTag(ReminderTestTags.REPEAT).performClick()
        composeRule.onNodeWithText(string(R.string.reminder_repeat_weekly)).performClick()
        composeRule.onNodeWithTag(ReminderTestTags.SAVE).performClick()

        waitAdvancing { reminders(id).isNotEmpty() }
        val r = reminders(id).single()
        val tomorrow9 = LocalDate.now().plusDays(1).atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant()
        assertThat(r.triggerAt).isEqualTo(tomorrow9)
        assertThat(r.repeat).isEqualTo(ReminderRepeat.WEEKLY)
        assertThat(r.blockId).isNull()
        waitAdvancing { exists(hasTestTag(ReminderTestTags.chip(r))) }
        // Alerts are allowed, so no warning.
        assertThat(exists(hasTestTag(ReminderTestTags.ALERTS_OFF))).isFalse()

        // Tap the chip to edit: same time, now one-off.
        composeRule.onNodeWithTag(ReminderTestTags.chip(r)).performClick()
        waitAdvancing { exists(hasTestTag(ReminderTestTags.DELETE)) }
        composeRule.onNodeWithTag(ReminderTestTags.REPEAT).performClick()
        composeRule.onNodeWithText(string(R.string.reminder_repeat_none)).performClick()
        composeRule.onNodeWithTag(ReminderTestTags.SAVE).performClick()
        waitAdvancing { reminders(id).single().repeat == ReminderRepeat.NONE }
        assertThat(reminders(id).single().id).isEqualTo(r.id)
        assertThat(reminders(id).single().triggerAt).isEqualTo(tomorrow9)

        // Delete, then undo.
        composeRule.onNodeWithTag(ReminderTestTags.chip(r)).performClick()
        waitAdvancing { exists(hasTestTag(ReminderTestTags.DELETE)) }
        composeRule.onNodeWithTag(ReminderTestTags.DELETE).performClick()
        waitAdvancing { reminders(id).isEmpty() }
        waitAdvancing { exists(hasText(string(R.string.reminder_removed))) }
        composeRule.onNodeWithText(string(R.string.action_undo)).performClick()
        waitAdvancing { reminders(id).size == 1 }
    }

    @Test
    fun aChecklistItemGetsItsOwnReminderFromTheToolbar() {
        val id = note("Book train tickets")
        openNote(id)
        // The cursor starts in the checklist item, so the toolbar offers "Remind me about this task".
        waitAdvancing { exists(hasTestTag(EditorTestTags.REMIND_TASK)) }
        composeRule.onNodeWithTag(EditorTestTags.REMIND_TASK).performScrollTo().performClick()
        waitAdvancing { exists(hasTestTag(ReminderTestTags.DIALOG)) }
        assertThat(exists(hasText(string(R.string.reminder_for_task, "Book train tickets")))).isTrue()
        composeRule.onNodeWithTag(ReminderTestTags.preset(ReminderPresets.Kind.TOMORROW_MORNING)).performClick()
        composeRule.onNodeWithTag(ReminderTestTags.SAVE).performClick()

        waitAdvancing { reminders(id).isNotEmpty() }
        val r = reminders(id).single()
        assertThat(r.blockId).isEqualTo("t0")
        // The chip names the task.
        waitAdvancing { exists(hasTestTag(ReminderTestTags.chip(r)) and hasContentDescription("Book train tickets", substring = true)) }
    }

    @Test
    fun aHandwrittenNoteGetsRemindersFromItsMenu() {
        val id = runBlocking {
            (data.ink().createInkNote(null, com.appname.notes.core.ink.model.PageTemplate.BLANK) as AppResult.Success).value
        }
        waitAdvancing { exists(hasTestTag("note_card_${id.value}")) }
        composeRule.onNodeWithTag("note_card_${id.value}").performClick()
        waitAdvancing { exists(hasContentDescription(string(R.string.ink_more_options))) }
        // No reminders yet: no alarm button in the top bar.
        assertThat(exists(hasTestTag(ReminderTestTags.MENU))).isFalse()
        composeRule.onNodeWithContentDescription(string(R.string.ink_more_options)).performClick()
        composeRule.onNodeWithText(string(R.string.reminder_add)).performClick()
        waitAdvancing { exists(hasTestTag(ReminderTestTags.DIALOG)) }
        composeRule.onNodeWithTag(ReminderTestTags.preset(ReminderPresets.Kind.TOMORROW_MORNING)).performClick()
        composeRule.onNodeWithTag(ReminderTestTags.SAVE).performClick()
        waitAdvancing { reminders(id).isNotEmpty() }
        waitAdvancing { exists(hasTestTag(ReminderTestTags.MENU)) }
    }

    @Test
    fun theLibraryCardShowsTheNextReminderAndDeniedNotificationsAreExplained() {
        val id = note()
        runBlocking {
            data.reminders().add(id, null, LocalDate.now().plusDays(1).atTime(9, 0), ReminderRepeat.NONE, ZoneId.systemDefault())
        }
        val time = ReminderDates.format(composeRule.activity, LocalDate.now().plusDays(1).atTime(9, 0))
        waitAdvancing { exists(hasContentDescription(string(R.string.card_reminder, time))) }

        Shadows.shadowOf(composeRule.activity.application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        openNote(id)
        waitAdvancing { exists(hasTestTag(ReminderTestTags.ALERTS_OFF)) }
        assertThat(exists(hasText(string(R.string.reminder_alerts_off)))).isTrue()
    }
}
