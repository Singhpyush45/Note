package com.appname.notes.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.id.UuidV7Generator
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.time.FixedClock
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.SearchFilters
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NFR-06: with 10,000 notes the library list and search stay quick. Times are printed for the release report; the
 * limits are loose because CI machines vary (a phone is usually faster than the JVM SQLite here).
 */
@RunWith(AndroidJUnit4::class)
class ScaleTest {
    private val clock = FixedClock(Instant.parse("2026-10-26T10:00:00Z"))
    private val ids = UuidV7Generator(clock)
    private val db = NotesDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val notes = RoomNoteRepository(db, clock, ids, NoOpLogger, zone = { ZoneOffset.UTC })

    @After
    fun close() = db.close()

    private val words = listOf("physics", "waves", "optics", "chemistry", "bonds", "maths", "calculus", "history", "notes", "lecture")

    @Test
    fun tenThousandNotesListAndSearchQuickly() = runBlocking {
        repeat(N) { i ->
            clock.advanceMillis(1_000)
            val body = (0 until 6).joinToString(" ") { words[(i * 7 + it * 3) % words.size] } + " item$i"
            notes.createNote("Note $i ${words[i % words.size]}", NoteDocument(blocks = listOf(TextBlock(ids.newId(), BlockKind.PARAGRAPH, RichText(body)))), null)
        }
        var t = System.nanoTime()
        val all = notes.observeNotes(NoteCollection.All, NoteSort.MODIFIED).first()
        val listMs = (System.nanoTime() - t) / 1_000_000
        assertThat(all).hasSize(N)

        t = System.nanoTime()
        val hits = notes.search("optics", SearchFilters()).first()
        val searchMs = (System.nanoTime() - t) / 1_000_000
        assertThat(hits).isNotEmpty()

        t = System.nanoTime()
        val one = notes.search("item9876", SearchFilters()).first()
        val exactMs = (System.nanoTime() - t) / 1_000_000
        assertThat(one).isNotEmpty()

        println("NFR-06 with $N notes: library ${listMs} ms, search 'optics' ${searchMs} ms (${hits.size} hits), search 'item9876' ${exactMs} ms")
        assertThat(listMs).isLessThan(5_000)
        assertThat(searchMs).isLessThan(3_000)
        assertThat(exactMs).isLessThan(2_000)
    }

    private companion object {
        const val N = 10_000
    }
}
