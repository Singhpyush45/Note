package com.appname.notes.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.appname.notes.core.database.entity.NoteEntity
import com.appname.notes.core.database.entity.NoteListRow
import com.appname.notes.core.database.entity.NoteTagCrossRef
import com.appname.notes.core.database.entity.NoteWithTags
import com.appname.notes.core.database.entity.SearchRow
import kotlinx.coroutines.flow.Flow

/** Collection modes understood by [NoteDao.observeNotes]. */
object CollectionMode {
    const val ALL = "ALL"
    const val FAVOURITES = "FAVOURITES"
    const val ARCHIVE = "ARCHIVE"
    const val TRASH = "TRASH"
}

@Dao
abstract class NoteDao {

    /**
     * Notes of one collection. Trash shows only trashed notes (newest first); Archive only archived ones;
     * everything else hides archived and trashed notes. Pinned notes first, then the chosen sort.
     */
    @Transaction
    @Query(
        """
        SELECT n.id, n.title, n.preview, n.noteType, n.notebookId,
               nb.title AS notebookTitle, n.isPinned, n.isFavourite, n.isArchived, n.trashedAt,
               n.createdAt, n.updatedAt,
               (SELECT COUNT(*) FROM attachments a WHERE a.noteId = n.id) AS attachmentCount,
               (SELECT a.fileName FROM attachments a WHERE a.noteId = n.id AND a.kind = 'IMAGE'
                ORDER BY a.position LIMIT 1) AS coverImage
        FROM notes n
        LEFT JOIN notebooks nb ON nb.id = n.notebookId AND nb.deletedAt IS NULL
        WHERE n.deletedAt IS NULL
          AND ((:mode = 'TRASH' AND n.trashedAt IS NOT NULL) OR (:mode != 'TRASH' AND n.trashedAt IS NULL))
          AND (:mode = 'TRASH'
               OR (:mode = 'ARCHIVE' AND n.isArchived = 1)
               OR (:mode != 'ARCHIVE' AND n.isArchived = 0))
          AND (:mode != 'FAVOURITES' OR n.isFavourite = 1)
          AND (:notebookId IS NULL OR n.notebookId = :notebookId)
          AND (:tagId IS NULL OR n.id IN (SELECT noteId FROM note_tags WHERE tagId = :tagId))
        ORDER BY
          CASE WHEN :mode = 'TRASH' THEN 0 ELSE n.isPinned END DESC,
          CASE WHEN :mode = 'TRASH' THEN n.trashedAt END DESC,
          CASE WHEN :sort = 'TITLE' THEN lower(CASE WHEN n.title != '' THEN n.title ELSE n.plainText END) END ASC,
          CASE WHEN :sort = 'CREATED' THEN n.createdAt END DESC,
          n.updatedAt DESC,
          n.id
        """,
    )
    abstract fun observeNotes(mode: String, notebookId: String?, tagId: String?, sort: String): Flow<List<NoteListRow>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL")
    abstract fun observeNote(id: String): Flow<NoteWithTags?>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun getNote(id: String): NoteWithTags?

    @Query("SELECT * FROM notes WHERE id = :id")
    abstract suspend fun getEntity(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(note: NoteEntity)

    @Query(
        """
        UPDATE notes SET title = :title, contentJson = :contentJson, plainText = :plainText, preview = :preview,
               updatedAt = :now, revision = revision + 1
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    abstract suspend fun updateContent(id: String, title: String, contentJson: String, plainText: String, preview: String, now: Long): Int

    /** Returns 1 if renamed, 0 if the note is gone or already has that title (no modified-time bump then). */
    @Query("UPDATE notes SET title = :title, updatedAt = :now, revision = revision + 1 WHERE id = :id AND deletedAt IS NULL AND title != :title")
    abstract suspend fun setTitle(id: String, title: String, now: Long): Int

    @Query("UPDATE notes SET isPinned = :pinned, revision = revision + 1 WHERE id IN (:ids) AND deletedAt IS NULL")
    abstract suspend fun setPinned(ids: List<String>, pinned: Boolean): Int

    @Query("UPDATE notes SET isFavourite = :favourite, revision = revision + 1 WHERE id IN (:ids) AND deletedAt IS NULL")
    abstract suspend fun setFavourite(ids: List<String>, favourite: Boolean): Int

    @Query("UPDATE notes SET isArchived = :archived, revision = revision + 1 WHERE id IN (:ids) AND deletedAt IS NULL")
    abstract suspend fun setArchived(ids: List<String>, archived: Boolean): Int

    @Query("UPDATE notes SET notebookId = :notebookId, revision = revision + 1 WHERE id IN (:ids) AND deletedAt IS NULL")
    abstract suspend fun moveToNotebook(ids: List<String>, notebookId: String?): Int

    @Query("UPDATE notes SET trashedAt = :now, revision = revision + 1 WHERE id IN (:ids) AND deletedAt IS NULL AND trashedAt IS NULL")
    abstract suspend fun moveToTrash(ids: List<String>, now: Long): Int

    @Query("UPDATE notes SET trashedAt = NULL, revision = revision + 1 WHERE id IN (:ids) AND deletedAt IS NULL")
    abstract suspend fun restoreFromTrash(ids: List<String>): Int

    @Query(
        """
        UPDATE notes SET title = '', contentJson = '{}', plainText = '', preview = '', isPinned = 0, isFavourite = 0,
               trashedAt = NULL, notebookId = NULL, deletedAt = :now, revision = revision + 1
        WHERE id IN (:ids) AND deletedAt IS NULL
        """,
    )
    protected abstract suspend fun tombstone(ids: List<String>, now: Long): Int

    @Query("DELETE FROM note_tags WHERE noteId IN (:ids)")
    protected abstract suspend fun deleteTagLinks(ids: List<String>)

    @Query("DELETE FROM notes WHERE id = :id")
    protected abstract suspend fun hardDelete(id: String)

    @Query("SELECT id FROM notes WHERE trashedAt IS NOT NULL AND deletedAt IS NULL")
    abstract suspend fun trashedIds(): List<String>

    @Query("DELETE FROM ink_items WHERE pageId IN (SELECT id FROM ink_pages WHERE noteId IN (:ids))")
    protected abstract suspend fun deleteInkItems(ids: List<String>)

    @Query("DELETE FROM ink_pages WHERE noteId IN (:ids)")
    protected abstract suspend fun deleteInkPages(ids: List<String>)

    /** The stored PDF file itself is removed afterwards by the app once no note references it. */
    @Query("DELETE FROM pdf_documents WHERE noteId IN (:ids)")
    protected abstract suspend fun deletePdfDocuments(ids: List<String>)

    /** Attachment files themselves are removed afterwards by the app once nothing references them. */
    @Query("DELETE FROM attachments WHERE noteId IN (:ids)")
    protected abstract suspend fun deleteAttachments(ids: List<String>)

    /** Text extracted from the notes' documents (rows tied to attachments go with them anyway). */
    @Query("DELETE FROM extracted_text WHERE noteId IN (:ids)")
    protected abstract suspend fun deleteExtractedText(ids: List<String>)

    @Query("DELETE FROM text_index_jobs WHERE noteId IN (:ids)")
    protected abstract suspend fun deleteTextJobs(ids: List<String>)

    /** Permanent delete: content and handwriting removed, tags unlinked, tombstone kept (SRS 9.2). */
    @Transaction
    open suspend fun deletePermanently(ids: List<String>, now: Long): Int {
        var count = 0
        // SQLite limits bound parameters per statement.
        for (chunk in ids.chunked(500)) {
            deleteTagLinks(chunk)
            deleteInkItems(chunk)
            deleteInkPages(chunk)
            deletePdfDocuments(chunk)
            deleteExtractedText(chunk)
            deleteTextJobs(chunk)
            deleteAttachments(chunk)
            count += tombstone(chunk, now)
        }
        return count
    }

    /** For notes that never had content and were never synced: no tombstone needed. */
    @Transaction
    open suspend fun deleteUnusedNote(id: String) {
        deleteTagLinks(listOf(id))
        deleteInkItems(listOf(id))
        deleteInkPages(listOf(id))
        deletePdfDocuments(listOf(id))
        deleteExtractedText(listOf(id))
        deleteTextJobs(listOf(id))
        deleteAttachments(listOf(id))
        hardDelete(id)
    }

    @Query("SELECT tagId FROM note_tags WHERE noteId = :noteId")
    abstract suspend fun tagIdsFor(noteId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertTagLinks(links: List<NoteTagCrossRef>)

    @Query("UPDATE notes SET revision = revision + 1 WHERE id = :id")
    protected abstract suspend fun bumpRevision(id: String)

    @Transaction
    open suspend fun replaceTags(noteId: String, tagIds: Collection<String>) {
        deleteTagLinks(listOf(noteId))
        insertTagLinks(tagIds.map { NoteTagCrossRef(noteId, it) })
        bumpRevision(noteId)
    }

    @Transaction
    open suspend fun insertWithTags(note: NoteEntity, tagIds: Collection<String>) {
        insert(note)
        insertTagLinks(tagIds.map { NoteTagCrossRef(note.id, it) })
    }

    /** Full-text search. [match] must come from SearchQuery.toMatchExpression. */
    @Transaction
    @Query(
        """
        SELECT n.id, n.title, n.preview, n.noteType, n.notebookId,
               nb.title AS notebookTitle, n.isPinned, n.isFavourite, n.isArchived, n.trashedAt,
               n.createdAt, n.updatedAt,
               (SELECT COUNT(*) FROM attachments a WHERE a.noteId = n.id) AS attachmentCount,
               (SELECT a.fileName FROM attachments a WHERE a.noteId = n.id AND a.kind = 'IMAGE'
                ORDER BY a.position LIMIT 1) AS coverImage,
               snippet(notes_fts, char(2), char(3), '…', -1, 12) AS snippet
        FROM notes_fts
        JOIN notes n ON n.rowid = notes_fts.rowid
        LEFT JOIN notebooks nb ON nb.id = n.notebookId AND nb.deletedAt IS NULL
        WHERE notes_fts MATCH :match
          AND n.deletedAt IS NULL AND n.trashedAt IS NULL
          AND (:includeArchived = 1 OR n.isArchived = 0)
          AND (:notebookId IS NULL OR n.notebookId = :notebookId)
          AND (:tagId IS NULL OR n.id IN (SELECT noteId FROM note_tags WHERE tagId = :tagId))
          AND (:noteType IS NULL OR n.noteType = :noteType)
          AND (:modifiedSince IS NULL OR n.updatedAt >= :modifiedSince)
        ORDER BY n.isPinned DESC, n.updatedAt DESC
        LIMIT 200
        """,
    )
    abstract fun search(
        match: String,
        notebookId: String?,
        tagId: String?,
        noteType: String?,
        modifiedSince: Long?,
        includeArchived: Boolean,
    ): Flow<List<SearchRow>>

    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL")
    abstract suspend fun liveNoteCount(): Int
}
