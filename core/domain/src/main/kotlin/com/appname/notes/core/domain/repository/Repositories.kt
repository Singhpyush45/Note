package com.appname.notes.core.domain.repository

import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.model.Note
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookDeleteMode
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.model.SearchFilters
import com.appname.notes.core.domain.model.SearchHit
import com.appname.notes.core.domain.model.Tag
import com.appname.notes.core.domain.model.TagId
import kotlinx.coroutines.flow.Flow

/*
 * Contracts the UI depends on (SRS Appendix B.1). Implementations live in :core:database.
 * All writes are local and never wait for the network (SRS 10.2, 11.1).
 */

interface NoteRepository {
    fun observeNotes(collection: NoteCollection, sort: NoteSort): Flow<List<NoteSummary>>

    /** Emits null when the note doesn't exist or was permanently deleted. */
    fun observeNote(id: NoteId): Flow<Note?>

    suspend fun getNote(id: NoteId): Note?

    /** Creates an empty note immediately, so it is durable before the user types (SRS 4.2 journey 1). */
    suspend fun createNote(notebookId: NotebookId? = null): AppResult<NoteId>

    /** Creates a note with content (e.g. imported Markdown). */
    suspend fun createNote(title: String, document: NoteDocument, notebookId: NotebookId?): AppResult<NoteId>

    /** Autosave. Only bumps the modified time/revision when title or content actually changed. */
    suspend fun saveContent(id: NoteId, title: String, document: NoteDocument): AppResult<Unit>

    /** Renames a note without touching its content (used by handwritten notes). */
    suspend fun setTitle(id: NoteId, title: String): AppResult<Unit>

    /** Deletes a note the user created but never typed into. Returns true if it was discarded. */
    suspend fun discardIfBlank(id: NoteId): Boolean

    suspend fun duplicate(id: NoteId): AppResult<NoteId>

    suspend fun setPinned(ids: Set<NoteId>, pinned: Boolean): AppResult<Unit>
    suspend fun setFavourite(ids: Set<NoteId>, favourite: Boolean): AppResult<Unit>
    suspend fun setArchived(ids: Set<NoteId>, archived: Boolean): AppResult<Unit>
    suspend fun moveToNotebook(ids: Set<NoteId>, notebookId: NotebookId?): AppResult<Unit>
    suspend fun setTags(id: NoteId, tagIds: Set<TagId>): AppResult<Unit>

    /** Recoverable delete (FR-001). */
    suspend fun moveToTrash(ids: Set<NoteId>): AppResult<Unit>
    suspend fun restoreFromTrash(ids: Set<NoteId>): AppResult<Unit>

    /** Removes content for good; a tombstone row remains for future sync (SRS 9.2). */
    suspend fun deletePermanently(ids: Set<NoteId>): AppResult<Unit>
    suspend fun emptyTrash(): AppResult<Int>

    fun search(query: String, filters: SearchFilters): Flow<List<SearchHit>>

    /** Rebuilds the full-text index from note contents (SRS 12.1). */
    suspend fun rebuildSearchIndex(): AppResult<Unit>
}

interface NotebookRepository {
    fun observeNotebooks(): Flow<List<Notebook>>
    suspend fun create(title: String): AppResult<NotebookId>
    suspend fun rename(id: NotebookId, title: String): AppResult<Unit>

    /** Saves a new manual order; [orderedIds] lists every notebook. */
    suspend fun reorder(orderedIds: List<NotebookId>): AppResult<Unit>
    suspend fun delete(id: NotebookId, mode: NotebookDeleteMode): AppResult<Unit>
}

interface TagRepository {
    fun observeTags(): Flow<List<Tag>>

    /** Returns the existing tag when one with the same name (ignoring case) exists. */
    suspend fun createOrGet(name: String): AppResult<Tag>
    suspend fun rename(id: TagId, name: String): AppResult<Unit>

    /** Removes the tag from all notes; the notes themselves are untouched. */
    suspend fun delete(id: TagId): AppResult<Unit>
}
