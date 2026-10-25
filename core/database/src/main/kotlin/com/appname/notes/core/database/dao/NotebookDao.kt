package com.appname.notes.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.appname.notes.core.database.entity.NotebookEntity
import com.appname.notes.core.database.entity.NotebookRow
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NotebookDao {

    /** Live notebooks in manual order, each with its count of visible (not archived/trashed) notes. */
    @Query(
        """
        SELECT nb.*, (
            SELECT COUNT(*) FROM notes n
            WHERE n.notebookId = nb.id AND n.deletedAt IS NULL AND n.trashedAt IS NULL AND n.isArchived = 0
        ) AS noteCount
        FROM notebooks nb
        WHERE nb.deletedAt IS NULL
        ORDER BY nb.sortOrder, nb.createdAt
        """,
    )
    abstract fun observeNotebooks(): Flow<List<NotebookRow>>

    @Query("SELECT * FROM notebooks WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun get(id: String): NotebookEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM notebooks WHERE deletedAt IS NULL")
    abstract suspend fun maxSortOrder(): Int

    @Insert
    abstract suspend fun insert(notebook: NotebookEntity)

    @Query("UPDATE notebooks SET title = :title, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun rename(id: String, title: String, now: Long): Int

    @Query("UPDATE notebooks SET parentId = :parentId, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun setParent(id: String, parentId: String?, now: Long): Int

    /** Live folder ids with their parent, for walking the tree. */
    @Query("SELECT id, parentId FROM notebooks WHERE deletedAt IS NULL")
    abstract suspend fun links(): List<FolderLink>

    @Query("UPDATE notebooks SET sortOrder = :sortOrder, updatedAt = :now WHERE id = :id")
    protected abstract suspend fun setSortOrder(id: String, sortOrder: Int, now: Long)

    @Transaction
    open suspend fun reorder(orderedIds: List<String>, now: Long) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index, now) }
    }

    @Query(
        """
        UPDATE notes SET trashedAt = :now, revision = revision + 1
        WHERE notebookId = :notebookId AND trashedAt IS NULL AND deletedAt IS NULL
        """,
    )
    protected abstract suspend fun trashNotesIn(notebookId: String, now: Long)

    @Query("UPDATE notes SET notebookId = NULL, revision = revision + 1 WHERE notebookId = :notebookId")
    protected abstract suspend fun unfileNotesIn(notebookId: String)

    @Query("UPDATE notebooks SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    protected abstract suspend fun tombstone(id: String, now: Long)

    /**
     * Deletes a notebook without destroying notes (FR-003): notes either move to "No notebook"
     * or to the recoverable Trash; in both cases they stay restorable.
     */
    @Transaction
    open suspend fun delete(id: String, trashNotes: Boolean, now: Long) {
        if (trashNotes) trashNotesIn(id, now)
        unfileNotesIn(id)
        tombstone(id, now)
    }

    /** Deletes a folder and every folder inside it, in one transaction. */
    @Transaction
    open suspend fun deleteTree(ids: List<String>, trashNotes: Boolean, now: Long) {
        ids.forEach { delete(it, trashNotes, now) }
    }
}

data class FolderLink(val id: String, val parentId: String?)
