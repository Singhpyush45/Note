package com.appname.notes

import android.os.Looper
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.ExtractionMethod
import com.appname.notes.core.domain.model.IndexStatus
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.PageText
import com.appname.notes.core.domain.repository.PdfDocumentInfo
import com.appname.notes.di.AppDataEntryPoint
import com.appname.notes.feature.ink.InkTestTags
import com.appname.notes.feature.library.LibraryTestTags
import com.appname.notes.feature.search.SearchTestTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * SRS Journey 4: search finds words inside a document and opens it at the page (FR-011). The text is
 * seeded here; reading it from real PDFs and photos is tested on the emulator (DocumentTextDeviceTest).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class DocumentSearchRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val data by lazy { EntryPointAccessors.fromApplication(composeRule.activity.application, AppDataEntryPoint::class.java) }

    private fun waitAdvancing(condition: () -> Boolean) = composeRule.waitUntil(15_000) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        condition()
    }

    private fun exists(matcher: SemanticsMatcher, unmerged: Boolean = false) =
        composeRule.onAllNodes(matcher, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()

    /** A two-page PDF note whose page 2 says "Mitochondria…" (as if the indexer had read it). */
    private fun indexedPdfNote(): NoteId = runBlocking {
        val doc = PdfDocumentInfo("cells.pdf", "Cells.pdf", "0".repeat(64), 100, 2)
        val id = (data.pdf().createPdfNote(null, "Cells", doc, listOf(794f to 1123f, 794f to 1123f)) as AppResult.Success).value
        val source = (data.textIndex().pending(10, Instant.now().plusSeconds(60)) as AppResult.Success).value.single { it.noteId == id }
        data.textIndex().save(
            source,
            listOf(PageText(0, "Plants and light", ExtractionMethod.PDF_TEXT), PageText(1, "Mitochondria are the powerhouse", ExtractionMethod.PDF_TEXT)),
            IndexStatus.DONE,
        )
        id
    }

    @Test
    fun aWordOnAPdfPageIsFoundAndOpensThatPage() {
        indexedPdfNote()
        waitAdvancing { exists(hasTestTag(LibraryTestTags.SEARCH)) }
        composeRule.onNodeWithTag(LibraryTestTags.SEARCH).performClick()
        waitAdvancing { exists(hasTestTag(SearchTestTags.FIELD)) }
        composeRule.onNodeWithTag(SearchTestTags.FIELD).performTextInput("mitochon")

        val pageLabel = composeRule.activity.getString(R.string.search_in_page, 2)
        waitAdvancing { exists(hasText(pageLabel), unmerged = true) }
        composeRule.onNodeWithText(pageLabel, useUnmergedTree = true).performClick()

        waitAdvancing { exists(hasTestTag(InkTestTags.CANVAS)) }
        // Opened at page 2, not the top.
        waitAdvancing { exists(hasText("2 / 2"), unmerged = true) }
        assertThat(exists(hasText("1 / 2"), unmerged = true)).isFalse()
    }
}
