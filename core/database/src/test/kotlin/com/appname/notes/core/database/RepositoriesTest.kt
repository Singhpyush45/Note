package com.appname.notes.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.id.UuidV7Generator
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.common.result.getOrNull
import com.appname.notes.core.common.time.FixedClock
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.core.domain.model.ModifiedWithin
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.NotebookDeleteMode
import com.appname.notes.core.domain.model.SearchFilters
import com.appname.notes.core.domain.model.SearchHit
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoriesTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T10:00:00Z"))
    private val ids = UuidV7Generator(clock)
    private val db = NotesDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val notes = RoomNoteRepository(db, clock, ids, NoOpLogger, zone = { ZoneOffset.UTC })
    private val notebooks = RoomNotebookRepository(db, clock, ids, NoOpLogger)
    private val tags = RoomTagRepository(db, clock, ids, NoOpLogger)

    @After
    fun close() = db.close()

    private fun doc(vararg lines: String) = NoteDocument(blocks = lines.map { TextBlock(ids.newId(), BlockKind.PARAGRAPH, RichText(it)) })

    private suspend fun note(title: String, vararg lines: String, notebook: com.appname.notes.core.domain.model.NotebookId? = null): NoteId {
        clock.advanceMillis(1_000)
        return (notes.createNote(title, doc(*lines), notebook) as AppResult.Success).value
    }

    private suspend fun all(collection: NoteCollection = NoteCollection.All, sort: NoteSort = NoteSort.MODIFIED) =
        notes.observeNotes(collection, sort).first()

    @Test
    fun createSaveAndReadBack() = runTest {
        val id = (notes.createNote() as AppResult.Success).value
        val created = notes.getNote(id)!!
        assertThat(created.document.textBlocks).hasSize(1)

        clock.advanceMillis(5_000)
        val content = doc("first line", "second line")
        assertThat(notes.saveContent(id, "Title", content)).isEqualTo(AppResult.Success(Unit))
        val saved = notes.getNote(id)!!
        assertThat(saved.title).isEqualTo("Title")
        assertThat(saved.document).isEqualTo(content)
        assertThat(saved.updatedAt).isEqualTo(clock.now())
        assertThat(saved.revision).isEqualTo(2)
    }

    @Test
    fun savingIdenticalContentKeepsModifiedTime() = runTest {
        val id = note("T", "body")
        val before = notes.getNote(id)!!
        clock.advanceMillis(60_000)
        notes.saveContent(id, before.title, before.document)
        assertThat(notes.getNote(id)!!.updatedAt).isEqualTo(before.updatedAt)
    }

    @Test
    fun savingAMissingNoteReportsNotFound() = runTest {
        val result = notes.saveContent(NoteId("nope"), "x", doc("y"))
        assertThat((result as AppResult.Failure).error).isInstanceOf(DomainError.NotFound::class.java)
    }

    @Test
    fun blankNewNotesAreDiscardedButRealOnesAreKept() = runTest {
        val blank = (notes.createNote() as AppResult.Success).value
        assertThat(notes.discardIfBlank(blank)).isTrue()
        assertThat(notes.getNote(blank)).isNull()

        val real = note("", "has text")
        assertThat(notes.discardIfBlank(real)).isFalse()
        assertThat(notes.getNote(real)).isNotNull()
    }

    @Test
    fun pinnedFirstThenMostRecent() = runTest {
        val a = note("A", "a")
        val b = note("B", "b")
        val c = note("C", "c")
        notes.setPinned(setOf(a), true)
        assertThat(all().map { it.id }).containsExactly(a, c, b).inOrder()
    }

    @Test
    fun sortByTitleUsesFirstLineWhenUntitled() = runTest {
        val z = note("zebra", "x")
        val untitled = note("", "apple pie")
        val m = note("Mango", "y")
        assertThat(all(sort = NoteSort.TITLE).map { it.id }).containsExactly(untitled, m, z).inOrder()
        assertThat(all(sort = NoteSort.CREATED).map { it.id }).containsExactly(m, untitled, z).inOrder()
    }

    @Test
    fun archiveFavouritesTrashCollections() = runTest {
        val plain = note("plain", "1")
        val fav = note("fav", "2")
        val archived = note("arch", "3")
        val trashed = note("trash", "4")
        notes.setFavourite(setOf(fav), true)
        notes.setArchived(setOf(archived), true)
        notes.moveToTrash(setOf(trashed))

        assertThat(all().map { it.id }).containsExactly(plain, fav)
        assertThat(all(NoteCollection.Favourites).map { it.id }).containsExactly(fav)
        assertThat(all(NoteCollection.Archive).map { it.id }).containsExactly(archived)
        assertThat(all(NoteCollection.Trash).map { it.id }).containsExactly(trashed)

        notes.restoreFromTrash(setOf(trashed))
        assertThat(all().map { it.id }).contains(trashed)
    }

    @Test
    fun permanentDeleteLeavesAContentFreeTombstone() = runTest {
        val id = note("secret", "private text")
        val tag = tags.createOrGet("exam").getOrNull()!!
        notes.setTags(id, setOf(tag.id))
        notes.moveToTrash(setOf(id))
        notes.deletePermanently(setOf(id))

        assertThat(notes.getNote(id)).isNull()
        assertThat(all(NoteCollection.Trash)).isEmpty()
        val row = db.noteDao().getEntity(id.value)!!
        assertThat(row.deletedAt).isNotNull()
        assertThat(row.title).isEmpty()
        assertThat(row.plainText).isEmpty()
        assertThat(notes.search("private", SearchFilters()).first()).isEmpty()
        assertThat(tags.observeTags().first().single().noteCount).isEqualTo(0)
    }

    @Test
    fun emptyTrashDeletesOnlyTrashedNotes() = runTest {
        val keep = note("keep", "k")
        val t1 = note("t1", "x")
        val t2 = note("t2", "y")
        notes.moveToTrash(setOf(t1, t2))
        assertThat(notes.emptyTrash()).isEqualTo(AppResult.Success(2))
        assertThat(all().map { it.id }).containsExactly(keep)
    }

    @Test
    fun duplicateCopiesContentAndTagsWithNewIds() = runTest {
        val id = note("Original", "a", "b")
        val tag = tags.createOrGet("work").getOrNull()!!
        notes.setTags(id, setOf(tag.id))
        notes.setPinned(setOf(id), true)

        val copyId = notes.duplicate(id).getOrNull()!!
        val original = notes.getNote(id)!!
        val copy = notes.getNote(copyId)!!
        assertThat(copy.title).isEqualTo("Original")
        assertThat(copy.document.textBlocks.map { it.text }).isEqualTo(original.document.textBlocks.map { it.text })
        assertThat(copy.document.textBlocks.map { it.id }).containsNoneIn(original.document.textBlocks.map { it.id })
        assertThat(copy.tags.map { it.name }).containsExactly("work")
        assertThat(copy.isPinned).isFalse()
    }

    @Test
    fun notebooksCountMoveRenameReorder() = runTest {
        val school = notebooks.create("School").getOrNull()!!
        val home = notebooks.create("  Home  ").getOrNull()!!
        assertThat(notebooks.create("   ")).isInstanceOf(AppResult.Failure::class.java)

        val n1 = note("n1", "x", notebook = school)
        note("n2", "y", notebook = school)
        val list = notebooks.observeNotebooks().first()
        assertThat(list.map { it.title }).containsExactly("School", "Home").inOrder()
        assertThat(list.first().noteCount).isEqualTo(2)

        notes.moveToNotebook(setOf(n1), home)
        assertThat(all(NoteCollection.InNotebook(home)).map { it.id }).containsExactly(n1)
        assertThat(all(NoteCollection.InNotebook(home)).single().notebookTitle).isEqualTo("Home")

        notebooks.rename(home, "House")
        notebooks.reorder(listOf(home, school))
        assertThat(notebooks.observeNotebooks().first().map { it.title }).containsExactly("House", "School").inOrder()
    }

    @Test
    fun deletingANotebookNeverDestroysNotes() = runTest {
        val keepBook = notebooks.create("Keep").getOrNull()!!
        val trashBook = notebooks.create("Trash me").getOrNull()!!
        val kept = note("kept", "1", notebook = keepBook)
        val trashed = note("trashed", "2", notebook = trashBook)

        notebooks.delete(keepBook, NotebookDeleteMode.KEEP_NOTES)
        notebooks.delete(trashBook, NotebookDeleteMode.TRASH_NOTES)

        assertThat(notebooks.observeNotebooks().first()).isEmpty()
        val visible = all().single()
        assertThat(visible.id).isEqualTo(kept)
        assertThat(visible.notebookId).isNull()
        assertThat(all(NoteCollection.Trash).map { it.id }).containsExactly(trashed)
        notes.restoreFromTrash(setOf(trashed))
        assertThat(notes.getNote(trashed)!!.notebookId).isNull()
    }

    @Test
    fun tagsAreCaseInsensitiveAndFilterable() = runTest {
        val exam = tags.createOrGet("#Exam").getOrNull()!!
        assertThat(tags.createOrGet("exam").getOrNull()!!.id).isEqualTo(exam.id)
        val other = tags.createOrGet("Maths").getOrNull()!!
        assertThat(tags.rename(other.id, "EXAM")).isInstanceOf(AppResult.Failure::class.java)

        val a = note("a", "1")
        note("b", "2")
        notes.setTags(a, setOf(exam.id, other.id))
        assertThat(all(NoteCollection.WithTag(exam.id)).map { it.id }).containsExactly(a)
        assertThat(all().first { it.id == a }.tags.map { it.name }).containsExactly("Exam", "Maths").inOrder()

        tags.delete(exam.id)
        assertThat(notes.getNote(a)!!.tags.map { it.name }).containsExactly("Maths")
        assertThat(notes.getNote(a)).isNotNull()
    }

    @Test
    fun searchFindsPrefixesInTitleAndBodyWithSnippets() = runTest {
        val calc = note("Calculus", "the chain rule explained", "derivatives")
        note("Groceries", "milk eggs")
        val hits = notes.search("chai", SearchFilters()).first()
        assertThat(hits.map { it.note.id }).containsExactly(calc)
        assertThat(hits.single().snippet).contains("${SearchHit.HIGHLIGHT_START}chain${SearchHit.HIGHLIGHT_END}")
        assertThat(notes.search("CALC", SearchFilters()).first().map { it.note.id }).containsExactly(calc)
        assertThat(notes.search("chain milk", SearchFilters()).first()).isEmpty() // all words must match
        assertThat(notes.search("\"; DROP TABLE notes; --", SearchFilters()).first()).isEmpty()
    }

    @Test
    fun searchSeesEditsAndHonoursFilters() = runTest {
        val id = note("Note", "old words")
        notes.saveContent(id, "Note", doc("brand new words"))
        assertThat(notes.search("old", SearchFilters()).first()).isEmpty()
        assertThat(notes.search("brand", SearchFilters()).first()).hasSize(1)

        val book = notebooks.create("Book").getOrNull()!!
        assertThat(notes.search("brand", SearchFilters(notebookId = book)).first()).isEmpty()
        notes.moveToNotebook(setOf(id), book)
        assertThat(notes.search("brand", SearchFilters(notebookId = book)).first()).hasSize(1)

        notes.setArchived(setOf(id), true)
        assertThat(notes.search("brand", SearchFilters()).first()).isEmpty()
        assertThat(notes.search("brand", SearchFilters(includeArchived = true)).first()).hasSize(1)

        clock.advanceMillis(40L * 24 * 3600 * 1000)
        assertThat(notes.search("brand", SearchFilters(includeArchived = true, modifiedWithin = ModifiedWithin.MONTH)).first()).isEmpty()

        notes.moveToTrash(setOf(id))
        assertThat(notes.search("brand", SearchFilters(includeArchived = true)).first()).isEmpty()
    }

    @Test
    fun hindiTextIsSearchable() = runTest {
        val id = note("नोट्स", "आज की कक्षा में अवकलन पढ़ा")
        assertThat(notes.search("अवकलन", SearchFilters()).first().map { it.note.id }).containsExactly(id)
    }

    @Test
    fun searchIndexCanBeRebuilt() = runTest {
        val id = note("Rebuild", "index me")
        assertThat(notes.rebuildSearchIndex()).isEqualTo(AppResult.Success(Unit))
        assertThat(notes.search("index", SearchFilters()).first().map { it.note.id }).containsExactly(id)
    }

    @Test
    fun corruptDocumentOpensReadOnlyInsteadOfCrashing() = runTest {
        val id = note("x", "y")
        db.openHelper.writableDatabase.execSQL("UPDATE notes SET contentJson = 'not json' WHERE id = ?", arrayOf(id.value))
        val note = notes.getNote(id)!!
        assertThat(note.document.isFullyEditable).isFalse()
    }

    @Test
    fun listPreviewShowsChecklistMarkers() = runTest {
        val doc = NoteDocument(
            blocks = listOf(
                TextBlock(ids.newId(), BlockKind.CHECKLIST, RichText("Milk"), checked = true),
                TextBlock(ids.newId(), BlockKind.CHECKLIST, RichText("Eggs")),
            ),
        )
        notes.createNote("Groceries", doc, null)
        assertThat(all().single().preview).isEqualTo("☑ Milk\n☐ Eggs")
    }

    @Test
    fun listUpdatesLiveWhenNotesChange() = runTest {
        val flow = notes.observeNotes(NoteCollection.All, NoteSort.MODIFIED)
        assertThat(flow.first()).isEmpty()
        note("live", "x")
        assertThat(flow.first()).hasSize(1)
    }

    @Test
    fun foldersInsideFolders() = runTest {
        val school = notebooks.create("School").getOrNull()!!
        val physics = notebooks.create("Physics", parent = school).getOrNull()!!
        val waves = notebooks.create("Waves", parent = physics).getOrNull()!!
        val home = notebooks.create("Home").getOrNull()!!
        val n = note("lecture", "x", notebook = waves)
        val list = notebooks.observeNotebooks().first()
        assertThat(list.first { it.id == waves }.parentId).isEqualTo(physics)
        assertThat(com.appname.notes.core.domain.model.FolderTree.ordered(list).map { it.first.title to it.second })
            .containsExactly("School" to 0, "Physics" to 1, "Waves" to 2, "Home" to 0).inOrder()
        assertThat(com.appname.notes.core.domain.model.FolderTree.path(list, waves).map { it.title }).containsExactly("School", "Physics", "Waves").inOrder()

        // Never into itself or a folder inside it.
        assertThat(notebooks.move(school, waves)).isInstanceOf(AppResult.Failure::class.java)
        assertThat(notebooks.move(school, school)).isInstanceOf(AppResult.Failure::class.java)
        assertThat(notebooks.move(physics, home)).isEqualTo(AppResult.Success(Unit))
        assertThat(notebooks.observeNotebooks().first().first { it.id == physics }.parentId).isEqualTo(home)

        // Deleting a folder deletes the folders inside it; their notes are kept.
        notebooks.delete(home, NotebookDeleteMode.KEEP_NOTES)
        val left = notebooks.observeNotebooks().first().map { it.title }
        assertThat(left).containsExactly("School")
        val kept = notes.getNote(n)!!
        assertThat(kept.trashedAt).isNull()
        assertThat(kept.notebookId).isNull()
    }
}
