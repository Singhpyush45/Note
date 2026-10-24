package com.appname.notes.core.database

import androidx.room.withTransaction
import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.database.dao.CollectionMode
import com.appname.notes.core.database.entity.NoteEntity
import com.appname.notes.core.domain.document.DocumentJson
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.core.domain.document.isBlank
import com.appname.notes.core.domain.document.preview
import com.appname.notes.core.domain.document.toPlainText
import com.appname.notes.core.domain.model.ModifiedWithin
import com.appname.notes.core.domain.model.Note
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.model.SearchFilters
import com.appname.notes.core.domain.model.SearchHit
import com.appname.notes.core.domain.model.TagId
import com.appname.notes.core.domain.repository.NoteRepository
import com.appname.notes.core.domain.search.SearchQuery
import java.time.Duration
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RoomNoteRepository(
    private val db: NotesDatabase,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val logger: AppLogger,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : NoteRepository {

    private val dao = db.noteDao()

    override fun observeNotes(collection: NoteCollection, sort: NoteSort): Flow<List<NoteSummary>> {
        val (mode, notebookId, tagId) = when (collection) {
            NoteCollection.All -> Triple(CollectionMode.ALL, null, null)
            NoteCollection.Favourites -> Triple(CollectionMode.FAVOURITES, null, null)
            NoteCollection.Archive -> Triple(CollectionMode.ARCHIVE, null, null)
            NoteCollection.Trash -> Triple(CollectionMode.TRASH, null, null)
            is NoteCollection.InNotebook -> Triple(CollectionMode.ALL, collection.id.value, null)
            is NoteCollection.WithTag -> Triple(CollectionMode.ALL, null, collection.id.value)
        }
        return dao.observeNotes(mode, notebookId, tagId, sort.name)
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()
    }

    override fun observeNote(id: NoteId): Flow<Note?> =
        dao.observeNote(id.value).map { it?.toDomain(logger) }.distinctUntilChanged()

    override suspend fun getNote(id: NoteId): Note? = dao.getNote(id.value)?.toDomain(logger)

    override suspend fun createNote(notebookId: NotebookId?): AppResult<NoteId> =
        createNote(title = "", document = NoteDocument.empty(ids::newId), notebookId = notebookId)

    override suspend fun createNote(title: String, document: NoteDocument, notebookId: NotebookId?): AppResult<NoteId> =
        storageCall(logger, "Create note") {
            val now = clock.now().toEpochMilli()
            val id = ids.newId()
            dao.insert(
                NoteEntity(
                    id = id,
                    notebookId = notebookId?.value,
                    title = title,
                    contentJson = DocumentJson.encode(document),
                    plainText = document.toPlainText(),
                    preview = document.preview(),
                    noteType = NoteType.TEXT.name,
                    isPinned = false,
                    isFavourite = false,
                    isArchived = false,
                    createdAt = now,
                    updatedAt = now,
                    trashedAt = null,
                    deletedAt = null,
                    revision = 1,
                ),
            )
            NoteId(id)
        }

    override suspend fun saveContent(id: NoteId, title: String, document: NoteDocument): AppResult<Unit> {
        val json = DocumentJson.encode(document)
        return when (val result = storageCall(logger, "Save note") {
            val current = dao.getEntity(id.value)
            when {
                current == null || current.deletedAt != null -> -1
                current.title == title && current.contentJson == json -> 0 // nothing changed: keep modified time
                else -> dao.updateContent(id.value, title, json, document.toPlainText(), document.preview(), clock.now().toEpochMilli())
            }
        }) {
            is AppResult.Failure -> result
            is AppResult.Success ->
                if (result.value < 0) AppResult.Failure(DomainError.NotFound("Note", id.value)) else AppResult.Success(Unit)
        }
    }

    override suspend fun setTitle(id: NoteId, title: String): AppResult<Unit> {
        val clean = title.trim()
        return when (val r = storageCall(logger, "Rename note") {
            val current = dao.getEntity(id.value)
            when {
                current == null || current.deletedAt != null -> -1
                else -> dao.setTitle(id.value, clean, clock.now().toEpochMilli())
            }
        }) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value < 0) AppResult.Failure(DomainError.NotFound("Note", id.value)) else AppResult.Success(Unit)
        }
    }

    override suspend fun discardIfBlank(id: NoteId): Boolean {
        val note = dao.getNote(id.value) ?: return false
        val contentBlank = when (note.note.noteType.toNoteType()) {
            NoteType.TEXT -> decodeDocument(note.note.contentJson, note.note.id, logger).isBlank()
            // A handwritten note is blank when it has no ink at all (pages alone don't count).
            NoteType.INK -> db.inkDao().itemCount(note.note.id) == 0
            NoteType.PDF -> false // an imported document is content
        }
        val blank = note.note.title.isBlank() && contentBlank && note.tags.isEmpty() &&
            db.attachmentDao().count(note.note.id) == 0 // attached files are content too
        if (blank) storageCall(logger, "Discard empty note") { dao.deleteUnusedNote(id.value) }
        return blank
    }

    override suspend fun duplicate(id: NoteId): AppResult<NoteId> {
        val source = dao.getNote(id.value) ?: return AppResult.Failure(DomainError.NotFound("Note", id.value))
        return storageCall(logger, "Duplicate note") { copyOf(source) }
    }

    private suspend fun copyOf(source: com.appname.notes.core.database.entity.NoteWithTags): NoteId = db.withTransaction {
        val copy = copyNoteOnly(source)
        // Attachments: new rows pointing at the same (never modified) files.
        val now = clock.now().toEpochMilli()
        val attachments = db.attachmentDao().list(source.note.id).map { it.copy(id = ids.newId(), noteId = copy.value, createdAt = now) }
        if (attachments.isNotEmpty()) db.attachmentDao().insertCopies(attachments)
        copy
    }

    private suspend fun copyNoteOnly(source: com.appname.notes.core.database.entity.NoteWithTags): NoteId {
        if (source.note.noteType.toNoteType() != NoteType.TEXT) return copyOfInkNote(source)
        val document = decodeDocument(source.note.contentJson, source.note.id, logger)
        // Fresh block IDs so the copy is independent for future per-block sync.
        val copyDocument = document.copy(blocks = document.blocks.map { if (it is TextBlock) it.copy(id = ids.newId()) else it })
        val now = clock.now().toEpochMilli()
        val newId = ids.newId()
        dao.insertWithTags(
            source.note.copy(
                id = newId,
                contentJson = DocumentJson.encode(copyDocument),
                isPinned = false,
                createdAt = now,
                updatedAt = now,
                trashedAt = null,
                revision = 1,
            ),
            source.tags.map { it.id },
        )
        return NoteId(newId)
    }

    private suspend fun copyOfInkNote(source: com.appname.notes.core.database.entity.NoteWithTags): NoteId {
        val now = clock.now().toEpochMilli()
        val newId = ids.newId()
        val (pages, items) = copyInk(db, source.note.id, newId, ids, now)
        // A PDF copy shares the (never modified) file; it is deleted only when no note uses it.
        val pdf = db.inkDao().pdfDocument(source.note.id)?.copy(noteId = newId, importedAt = now)
        db.inkDao().insertCopy(
            source.note.copy(id = newId, isPinned = false, createdAt = now, updatedAt = now, trashedAt = null, revision = 1),
            source.tags.map { it.id },
            pages,
            items,
            pdf,
        )
        return NoteId(newId)
    }

    private fun Set<NoteId>.raw() = map { it.value }

    override suspend fun setPinned(ids: Set<NoteId>, pinned: Boolean) =
        storageCall(logger, "Pin") { dao.setPinned(ids.raw(), pinned); Unit }

    override suspend fun setFavourite(ids: Set<NoteId>, favourite: Boolean) =
        storageCall(logger, "Favourite") { dao.setFavourite(ids.raw(), favourite); Unit }

    override suspend fun setArchived(ids: Set<NoteId>, archived: Boolean) =
        storageCall(logger, "Archive") { dao.setArchived(ids.raw(), archived); Unit }

    override suspend fun moveToNotebook(ids: Set<NoteId>, notebookId: NotebookId?): AppResult<Unit> {
        if (notebookId != null && db.notebookDao().get(notebookId.value) == null) {
            return AppResult.Failure(DomainError.NotFound("Notebook", notebookId.value))
        }
        return storageCall<Unit>(logger, "Move") { dao.moveToNotebook(ids.raw(), notebookId?.value) }
    }

    override suspend fun setTags(id: NoteId, tagIds: Set<TagId>) =
        storageCall(logger, "Set tags") { dao.replaceTags(id.value, tagIds.map { it.value }) }

    override suspend fun moveToTrash(ids: Set<NoteId>) =
        storageCall(logger, "Trash") { dao.moveToTrash(ids.raw(), clock.now().toEpochMilli()); Unit }

    override suspend fun restoreFromTrash(ids: Set<NoteId>) =
        storageCall(logger, "Restore") { dao.restoreFromTrash(ids.raw()); Unit }

    override suspend fun deletePermanently(ids: Set<NoteId>) =
        storageCall(logger, "Delete") { dao.deletePermanently(ids.raw(), clock.now().toEpochMilli()); Unit }

    override suspend fun emptyTrash(): AppResult<Int> = storageCall(logger, "Empty trash") {
        val trashed = dao.trashedIds()
        if (trashed.isEmpty()) 0 else dao.deletePermanently(trashed, clock.now().toEpochMilli())
    }

    override fun search(query: String, filters: SearchFilters): Flow<List<SearchHit>> {
        val match = SearchQuery.toMatchExpression(query) ?: return flowOf(emptyList())
        val now = clock.now()
        val since = when (filters.modifiedWithin) {
            ModifiedWithin.ANY -> null
            ModifiedWithin.TODAY -> now.atZone(zone()).toLocalDate().atStartOfDay(zone()).toInstant()
            ModifiedWithin.WEEK -> now.minus(Duration.ofDays(7))
            ModifiedWithin.MONTH -> now.minus(Duration.ofDays(30))
        }
        val notesHits = dao.search(
            match = match,
            notebookId = filters.notebookId?.value,
            tagId = filters.tagId?.value,
            noteType = filters.type?.name,
            modifiedSince = since?.toEpochMilli(),
            includeArchived = filters.includeArchived,
        ).map { rows -> rows.map { it.toDomain() } }
        val documentHits = db.textIndexDao().searchDocuments(
            match = match,
            notebookId = filters.notebookId?.value,
            tagId = filters.tagId?.value,
            noteType = filters.type?.name,
            modifiedSince = since?.toEpochMilli(),
            includeArchived = filters.includeArchived,
        ).map { rows -> rows.map { it.toDomain() } }
        // Matches in a note's own text come first; then where the words appear in its documents (a few per note).
        return combine(notesHits, documentHits) { inText, inDocs ->
            inText + inDocs.groupBy { it.note.id }.values.flatMap { it.take(MAX_DOCUMENT_HITS_PER_NOTE) }
        }.distinctUntilChanged()
    }

    override suspend fun rebuildSearchIndex(): AppResult<Unit> =
        storageCall(logger, "Rebuild search index") { db.rebuildSearchIndex() }
}

private const val MAX_DOCUMENT_HITS_PER_NOTE = 3
