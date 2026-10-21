package com.appname.notes.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.appname.notes.core.database.entity.TagEntity
import com.appname.notes.core.database.entity.TagRow
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TagDao {

    @Query(
        """
        SELECT t.id, t.name, (
            SELECT COUNT(*) FROM note_tags nt JOIN notes n ON n.id = nt.noteId
            WHERE nt.tagId = t.id AND n.deletedAt IS NULL AND n.trashedAt IS NULL
        ) AS noteCount
        FROM tags t
        ORDER BY t.name COLLATE NOCASE
        """,
    )
    abstract fun observeTags(): Flow<List<TagRow>>

    /** Case-insensitive, because the `name` column uses NOCASE collation. */
    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    abstract suspend fun findByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :id")
    abstract suspend fun get(id: String): TagEntity?

    @Insert
    abstract suspend fun insert(tag: TagEntity)

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: String, name: String): Int

    @Query("UPDATE notes SET revision = revision + 1 WHERE id IN (SELECT noteId FROM note_tags WHERE tagId = :tagId)")
    protected abstract suspend fun bumpTaggedNotes(tagId: String)

    @Query("DELETE FROM note_tags WHERE tagId = :tagId")
    protected abstract suspend fun unlink(tagId: String)

    @Query("DELETE FROM tags WHERE id = :id")
    protected abstract suspend fun deleteRow(id: String)

    /** Removes the tag everywhere; notes stay untouched apart from losing the tag. */
    @Transaction
    open suspend fun delete(id: String) {
        bumpTaggedNotes(id)
        unlink(id)
        deleteRow(id)
    }
}
