package com.appname.notes.feature.textindex

import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.time.FixedClock
import com.appname.notes.core.domain.model.ExtractionMethod
import com.appname.notes.core.domain.model.IndexStatus
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.PageText
import com.appname.notes.core.domain.model.TextSource
import com.appname.notes.core.domain.repository.TextIndexRepository
import com.appname.notes.testing.FakeSettingsRepository
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TextIndexerTest {

    private val clock = FixedClock(Instant.parse("2026-10-24T10:00:00Z"))
    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
    }
    private val settings = FakeSettingsRepository()

    /** In-memory repository: sources stay pending until saved. */
    private class FakeIndex(sources: List<TextSource>) : TextIndexRepository {
        val waiting = sources.toMutableList()
        val saved = mutableMapOf<String, Pair<List<PageText>, IndexStatus>>()
        override suspend fun pending(limit: Int, retryBefore: Instant) = AppResult.Success(waiting.take(limit))
        override suspend fun save(source: TextSource, pages: List<PageText>, status: IndexStatus): AppResult<Unit> {
            waiting.remove(source)
            saved[source.key] = pages to status
            return AppResult.Success(Unit)
        }
        override suspend fun clear() = AppResult.Success(Unit)
        override suspend fun progress() = AppResult.Success(saved.size to waiting.size)
    }

    private fun pdf(n: Int) = TextSource.PdfNoteSource(NoteId("n$n"), "f$n.pdf")

    private fun indexer(index: TextIndexRepository, extract: (TextSource) -> Extraction) =
        TextIndexer(index, extract, settings, clock, dispatchers, NoOpLogger)

    @Test
    fun `processes everything pending, in batches, and records each outcome`() = runTest {
        val index = FakeIndex(List(12) { pdf(it) })
        val n = indexer(index) { Extraction(listOf(PageText(0, "text of ${it.key}", ExtractionMethod.PDF_TEXT)), IndexStatus.DONE) }.run()
        assertThat(n).isEqualTo(12)
        assertThat(index.waiting).isEmpty()
        assertThat(index.saved["p:n3"]!!.first.single().text).isEqualTo("text of p:n3")
    }

    @Test
    fun `one bad document is recorded as failed and doesn't stop the others`() = runTest {
        val index = FakeIndex(List(3) { pdf(it) })
        indexer(index) { if (it.key == "p:n1") throw IllegalStateException("corrupt") else Extraction(emptyList(), IndexStatus.EMPTY) }.run()
        assertThat(index.saved.mapValues { it.value.second }).containsExactly(
            "p:n0", IndexStatus.EMPTY, "p:n1", IndexStatus.FAILED, "p:n2", IndexStatus.EMPTY,
        )
    }

    @Test
    fun `OCR missing on the device is recorded as unavailable, to retry later`() = runTest {
        val index = FakeIndex(listOf(pdf(0)))
        indexer(index) { throw OcrUnavailableException() }.run()
        assertThat(index.saved["p:n0"]!!.second).isEqualTo(IndexStatus.UNAVAILABLE)
    }

    @Test
    fun `turned off in settings, nothing is read`() = runTest {
        settings.documentTextEnabled.value = false
        val index = FakeIndex(listOf(pdf(0)))
        assertThat(indexer(index) { error("must not run") }.run()).isEqualTo(0)
        assertThat(index.waiting).hasSize(1)
    }

    @Test
    fun `stops between documents when asked`() = runTest {
        val index = FakeIndex(List(10) { pdf(it) })
        var count = 0
        val n = indexer(index) { count++; Extraction(emptyList(), IndexStatus.EMPTY) }.run(shouldStop = { count >= 2 })
        assertThat(n).isEqualTo(2)
        assertThat(index.waiting).hasSize(8)
    }
}
