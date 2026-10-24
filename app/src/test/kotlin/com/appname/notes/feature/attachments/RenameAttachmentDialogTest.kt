package com.appname.notes.feature.attachments

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.domain.model.Attachment
import com.appname.notes.core.domain.model.AttachmentId
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.core.domain.model.NoteId
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The rename dialog on its own (FR-009 / owner brief §10): trims the name, refuses blank names. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class RenameAttachmentDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zip = Attachment(
        AttachmentId("a1"), NoteId("n1"), AttachmentKind.FILE, "f.zip", "notes.zip", "application/zip", 2048, null, null, 0, Instant.EPOCH,
    )

    @Test
    fun renamesWithTheTrimmedNameAndRefusesBlankNames() {
        var renamed: String? = null
        composeRule.mainClock.autoAdvance = false // a focused field's cursor blinks forever
        composeRule.setContent { RenameAttachmentDialog(zip, onRename = { renamed = it }, onDismiss = {}) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag(AttachmentTestTags.RENAME_FIELD).performTextReplacement("   ")
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag(AttachmentTestTags.RENAME_SAVE).assertIsNotEnabled()
        composeRule.onNodeWithTag(AttachmentTestTags.RENAME_FIELD).performTextReplacement("  Week 3 slides.zip ")
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag(AttachmentTestTags.RENAME_SAVE).performClick()
        composeRule.mainClock.advanceTimeBy(100)
        assertThat(renamed).isEqualTo("Week 3 slides.zip")
    }
}
