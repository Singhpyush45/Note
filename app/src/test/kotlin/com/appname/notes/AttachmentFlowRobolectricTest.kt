package com.appname.notes

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.Attachment
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.core.domain.model.NewAttachment
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.di.AppDataEntryPoint
import com.appname.notes.feature.attachments.AttachmentTestTags
import com.appname.notes.feature.editor.EditorTestTags
import com.appname.notes.feature.library.LibraryTestTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Attachments through the real app (real database) on the JVM (FR-009). Picking files and the camera are
 * system screens, and decoding photos is native, so attachments are seeded through the repository here;
 * the emulator tests (AttachmentDeviceTest, e2e) cover real files, previews and sharing from other apps.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AttachmentFlowRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val data by lazy { EntryPointAccessors.fromApplication(composeRule.activity.application, AppDataEntryPoint::class.java) }

    private fun string(id: Int, vararg args: Any) = composeRule.activity.getString(id, *args)

    private fun waitAdvancing(condition: () -> Boolean) = composeRule.waitUntil(15_000) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        condition()
    }

    private fun exists(matcher: SemanticsMatcher, unmerged: Boolean = false) =
        composeRule.onAllNodes(matcher, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()

    private fun attachments(id: NoteId): List<Attachment> = runBlocking { (data.attachments().list(id) as AppResult.Success).value }

    /** A text note holding a photo and a zip file; the files exist in storage (contents don't matter here). */
    private fun noteWithAttachments(): NoteId = runBlocking {
        val dir = File(composeRule.activity.filesDir, "attachments").apply { mkdirs() }
        File(dir, "p1.jpg").writeBytes(ByteArray(10))
        File(dir, "f1.zip").writeBytes(ByteArray(2048))
        val id = (data.notes().createNote(null) as AppResult.Success).value
        data.notes().saveContent(id, "Field trip", com.appname.notes.core.domain.document.NoteDocument.empty { "b1" })
        data.attachments().add(
            id,
            listOf(
                NewAttachment(AttachmentKind.IMAGE, "p1.jpg", "Board.jpg", "image/jpeg", 10, 400, 300),
                NewAttachment(AttachmentKind.FILE, "f1.zip", "notes.zip", "application/zip", 2048),
            ),
        )
        id
    }

    private fun openNote(id: NoteId) {
        waitAdvancing { exists(hasTestTag("note_card_${id.value}")) }
        composeRule.onNodeWithTag("note_card_${id.value}").performClick()
        waitAdvancing { exists(hasTestTag(EditorTestTags.BODY)) }
    }

    /** Runs a TalkBack custom action ("Share", "Remove from note") on a node, as a screen-reader user would. */
    private fun customAction(tag: String, label: String) {
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val action = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().single { it.label == label }
        composeRule.runOnUiThread { action.action() }
        composeRule.waitForIdle()
    }

    @Test
    fun attachmentsShowInTheNoteAndOnTheLibraryCard() {
        val id = noteWithAttachments()
        // Card: "2 attachments" for TalkBack, next to the date.
        waitAdvancing { exists(hasContentDescription(composeRule.activity.resources.getQuantityString(R.plurals.card_attachments, 2, 2)), unmerged = true) }

        openNote(id)
        waitAdvancing { exists(hasTestTag(AttachmentTestTags.STRIP)) }
        assertThat(exists(hasContentDescription(string(R.string.attach_photo_label, "Board.jpg")), unmerged = true)).isTrue()
        assertThat(exists(hasText("notes.zip"), unmerged = true)).isTrue()
        // The attach button is there for an editable note, with the scanner among its choices.
        composeRule.onNodeWithTag(AttachmentTestTags.ATTACH_BUTTON).performClick()
        waitAdvancing { exists(hasTestTag(AttachmentTestTags.SCAN)) }
        assertThat(exists(hasText(string(R.string.attach_photos)))).isTrue()
        assertThat(exists(hasText(string(R.string.attach_files)))).isTrue()
    }

    @Test
    fun removingAnAttachmentCanBeUndone() {
        val id = noteWithAttachments()
        openNote(id)
        val photo = attachments(id).first { it.kind == AttachmentKind.IMAGE }
        waitAdvancing { exists(hasTestTag(AttachmentTestTags.item(photo)), unmerged = true) }

        customAction(AttachmentTestTags.item(photo), string(R.string.attach_remove))
        waitAdvancing { attachments(id).size == 1 }
        waitAdvancing { exists(hasText(string(R.string.attach_removed, "Board.jpg"))) }
        composeRule.onNodeWithText(string(R.string.action_undo)).performClick()
        waitAdvancing { attachments(id).size == 2 }
        assertThat(attachments(id).first()).isEqualTo(photo) // back in the same place, same file
    }

    @Test
    fun markdownExportWarnsThatAttachmentsAreNotIncluded() {
        openNote(noteWithAttachments())
        composeRule.onNodeWithContentDescription(string(R.string.action_more)).performClick()
        composeRule.onNodeWithText(string(R.string.note_export_markdown)).performClick()
        waitAdvancing { exists(hasText(string(R.string.export_lost_attachments))) }
    }

    @Test
    fun aNoteWithOnlyAttachmentsIsKeptWhenLeft() {
        val id = runBlocking {
            val dir = File(composeRule.activity.filesDir, "attachments").apply { mkdirs() }
            File(dir, "only.jpg").writeBytes(ByteArray(10))
            val note = (data.notes().createNote(null) as AppResult.Success).value
            data.attachments().add(note, listOf(NewAttachment(AttachmentKind.IMAGE, "only.jpg", "Only.jpg", "image/jpeg", 10, 10, 10)))
            note
        }
        openNote(id)
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        waitAdvancing { exists(hasTestTag(LibraryTestTags.SCREEN)) }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val all = runBlocking { data.notes().observeNotes(NoteCollection.All, NoteSort.MODIFIED).first() }
        assertThat(all.map { it.id }).contains(id)
    }

    /** FR-012: a recording shows a player; markers written in the text appear as chips on it. */
    @Test
    fun aRecordingShowsAPlayerWithItsMarkers() {
        val recordingId = com.appname.notes.core.domain.model.AttachmentId("rec-42")
        val marker = com.appname.notes.core.domain.model.AudioMarker(recordingId, 125_000)
        val id = runBlocking {
            val dir = File(composeRule.activity.filesDir, "attachments").apply { mkdirs() }
            File(dir, "r.aac").writeBytes(ByteArray(100))
            val note = (data.notes().createNote(null) as AppResult.Success).value
            val text = "Exam topic ${marker.label}"
            val doc = com.appname.notes.core.domain.document.NoteDocument(
                blocks = listOf(
                    com.appname.notes.core.domain.document.TextBlock(
                        "b1",
                        text = com.appname.notes.core.domain.document.RichText(
                            text,
                            listOf(com.appname.notes.core.domain.document.StyleSpan(11, text.length, com.appname.notes.core.domain.document.SpanType.LINK, marker.url)),
                        ),
                    ),
                ),
            )
            data.notes().saveContent(note, "Lecture", doc)
            data.attachments().add(note, listOf(NewAttachment(AttachmentKind.AUDIO, "r.aac", "Recording.aac", "audio/aac", 100, durationMs = 300_000, id = recordingId)))
            note
        }
        openNote(id)
        val player = attachments(id).single()
        waitAdvancing { exists(hasTestTag(com.appname.notes.feature.audio.AudioTestTags.player(player)), unmerged = true) }
        assertThat(exists(hasTestTag(com.appname.notes.feature.audio.AudioTestTags.marker(marker)), unmerged = true)).isTrue()
        assertThat(exists(hasText("0:00 / 5:00"), unmerged = true)).isTrue()
        // Nothing is recording, so there's no recording bar; "Record audio" is in the attach menu.
        assertThat(exists(hasTestTag(com.appname.notes.feature.audio.AudioTestTags.RECORDING_BAR))).isFalse()
        composeRule.onNodeWithTag(AttachmentTestTags.ATTACH_BUTTON).performClick()
        waitAdvancing { exists(hasTestTag(com.appname.notes.feature.audio.AudioTestTags.RECORD)) }
    }
}
