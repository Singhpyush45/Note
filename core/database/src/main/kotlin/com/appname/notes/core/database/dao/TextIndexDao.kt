package com.appname.notes.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.appname.notes.core.database.entity.DocumentHitRow
import com.appname.notes.core.database.entity.ExtractedTextEntity
import com.appname.notes.core.database.entity.PendingSourceRow
import com.appname.notes.core.database.entity.TextIndexJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TextIndexDao {

    /** Attached photos and PDFs still to process (or to retry). */
    @Query(
        """
        SELECT a.noteId, a.id AS attachmentId, a.kind, a.fileName
        FROM attachments a
        JOIN notes n ON n.id = a.noteId AND n.deletedAt IS NULL
        LEFT JOIN text_index_jobs j ON j.sourceKey = 'a:' || a.id
        WHERE a.kind IN ('IMAGE', 'PDF')
          AND (j.sourceKey IS NULL OR (j.status IN ('FAILED', 'UNAVAILABLE') AND j.attempts < :maxAttempts AND j.updatedAt < :retryBefore))
        ORDER BY a.createdAt DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun pendingAttachments(limit: Int, retryBefore: Long, maxAttempts: Int): List<PendingSourceRow>

    /** Documents of PDF notes still to process (or to retry). */
    @Query(
        """
        SELECT p.noteId, NULL AS attachmentId, NULL AS kind, p.fileName
        FROM pdf_documents p
        JOIN notes n ON n.id = p.noteId AND n.deletedAt IS NULL
        LEFT JOIN text_index_jobs j ON j.sourceKey = 'p:' || p.noteId
        WHERE j.sourceKey IS NULL OR (j.status IN ('FAILED', 'UNAVAILABLE') AND j.attempts < :maxAttempts AND j.updatedAt < :retryBefore)
        ORDER BY p.importedAt DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun pendingPdfNotes(limit: Int, retryBefore: Long, maxAttempts: Int): List<PendingSourceRow>

    @Query("SELECT * FROM text_index_jobs WHERE sourceKey = :key")
    abstract suspend fun job(key: String): TextIndexJobEntity?

    @Query("DELETE FROM extracted_text WHERE sourceKey = :key")
    protected abstract suspend fun deleteText(key: String)

    @Insert
    protected abstract suspend fun insertText(rows: List<ExtractedTextEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertJob(job: TextIndexJobEntity)

    @Query("SELECT COUNT(*) FROM notes WHERE id = :noteId AND deletedAt IS NULL")
    protected abstract suspend fun noteExists(noteId: String): Int

    @Query("SELECT COUNT(*) FROM attachments WHERE id = :id")
    protected abstract suspend fun attachmentExists(id: String): Int

    /**
     * Replaces the text of one source and records the outcome, atomically. Skipped (false) when the note or
     * attachment was deleted meanwhile, so a slow extraction never resurrects removed content.
     */
    @Transaction
    open suspend fun save(key: String, noteId: String, attachmentId: String?, rows: List<ExtractedTextEntity>, status: String, now: Long): Boolean {
        if (noteExists(noteId) == 0) return false
        if (attachmentId != null && attachmentExists(attachmentId) == 0) return false
        val attempts = (job(key)?.attempts ?: 0) + 1
        deleteText(key)
        if (rows.isNotEmpty()) insertText(rows)
        upsertJob(TextIndexJobEntity(key, noteId, attachmentId, status, attempts, now))
        return true
    }

    @Query("DELETE FROM extracted_text")
    protected abstract suspend fun deleteAllText()

    @Query("DELETE FROM text_index_jobs")
    protected abstract suspend fun deleteAllJobs()

    @Transaction
    open suspend fun clear() {
        deleteAllText()
        deleteAllJobs()
    }

    @Query("SELECT COUNT(*) FROM text_index_jobs WHERE status IN ('DONE', 'EMPTY')")
    abstract suspend fun doneCount(): Int

    /** Matches inside documents, for search. [match] must come from SearchQuery.toMatchExpression. */
    @Transaction
    @Query(
        """
        SELECT n.id, n.title, n.preview, n.noteType, n.notebookId,
               nb.title AS notebookTitle, n.isPinned, n.isFavourite, n.isArchived, n.trashedAt,
               n.createdAt, n.updatedAt,
               (SELECT COUNT(*) FROM attachments a2 WHERE a2.noteId = n.id) AS attachmentCount,
               (SELECT a3.fileName FROM attachments a3 WHERE a3.noteId = n.id AND a3.kind = 'IMAGE'
                ORDER BY a3.position LIMIT 1) AS coverImage,
               (SELECT MIN(r.triggerAt) FROM reminders r WHERE r.noteId = n.id AND r.firedAt IS NULL) AS nextReminderAt,
               snippet(extracted_text_fts, char(2), char(3), '…', -1, 12) AS snippet,
               e.attachmentId, a.displayName AS attachmentName, e.pageIndex, a.kind AS attachmentKind
        FROM extracted_text_fts
        JOIN extracted_text e ON e.rowId = extracted_text_fts.rowid
        JOIN notes n ON n.id = e.noteId
        LEFT JOIN attachments a ON a.id = e.attachmentId
        LEFT JOIN notebooks nb ON nb.id = n.notebookId AND nb.deletedAt IS NULL
        WHERE extracted_text_fts MATCH :match
          AND n.deletedAt IS NULL AND n.trashedAt IS NULL
          AND (:includeArchived = 1 OR n.isArchived = 0)
          AND (:notebookId IS NULL OR n.notebookId = :notebookId)
          AND (:tagId IS NULL OR n.id IN (SELECT noteId FROM note_tags WHERE tagId = :tagId))
          AND (:noteType IS NULL OR n.noteType = :noteType)
          AND (:modifiedSince IS NULL OR n.updatedAt >= :modifiedSince)
        ORDER BY n.isPinned DESC, n.updatedAt DESC, e.attachmentId, e.pageIndex
        LIMIT 200
        """,
    )
    abstract fun searchDocuments(
        match: String,
        notebookId: String?,
        tagId: String?,
        noteType: String?,
        modifiedSince: Long?,
        includeArchived: Boolean,
    ): Flow<List<DocumentHitRow>>
}
