package com.appname.notes.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.appname.notes.core.database.entity.AttachmentEntity
import com.appname.notes.core.database.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AttachmentDao {

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY position, createdAt")
    abstract fun observe(noteId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY position, createdAt")
    abstract suspend fun list(noteId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    abstract suspend fun get(id: String): AttachmentEntity?

    @Query("SELECT COUNT(*) FROM attachments WHERE noteId = :noteId")
    abstract suspend fun count(noteId: String): Int

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM attachments WHERE noteId = :noteId")
    protected abstract suspend fun nextPosition(noteId: String): Int

    @Query("SELECT DISTINCT fileName FROM attachments")
    abstract suspend fun fileNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAll(rows: List<AttachmentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertNote(note: NoteEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    protected abstract suspend fun deleteRow(id: String): Int

    /** Makes room at [position] (for putting a removed attachment back where it was). */
    @Query("UPDATE attachments SET position = position + 1 WHERE noteId = :noteId AND position >= :position")
    protected abstract suspend fun shiftDown(noteId: String, position: Int)

    @Query("UPDATE attachments SET position = position - 1 WHERE noteId = :noteId AND position > :position")
    protected abstract suspend fun shiftUp(noteId: String, position: Int)

    /** Marks the note modified. Returns 0 if the note is gone (deleted). */
    @Query("UPDATE notes SET updatedAt = :now, revision = revision + 1 WHERE id = :noteId AND deletedAt IS NULL")
    protected abstract suspend fun touchNote(noteId: String, now: Long): Int

    /** Appends rows (their positions are assigned here, in order). Null when the note doesn't exist any more. */
    @Transaction
    open suspend fun append(noteId: String, rows: List<AttachmentEntity>, now: Long): List<AttachmentEntity>? {
        if (touchNote(noteId, now) == 0) return null
        val start = nextPosition(noteId)
        val placed = rows.mapIndexed { i, r -> r.copy(noteId = noteId, position = start + i) }
        insertAll(placed)
        return placed
    }

    /** Removes one attachment, closing the gap. Returns false if it was already gone. */
    @Transaction
    open suspend fun remove(row: AttachmentEntity, now: Long): Boolean {
        if (deleteRow(row.id) == 0) return false
        shiftUp(row.noteId, row.position)
        touchNote(row.noteId, now)
        return true
    }

    /** Puts a removed attachment back at its old position. False if its note is gone. */
    @Transaction
    open suspend fun restore(row: AttachmentEntity, now: Long): Boolean {
        if (touchNote(row.noteId, now) == 0) return false
        val position = row.position.coerceIn(0, nextPosition(row.noteId))
        shiftDown(row.noteId, position)
        insertAll(listOf(row.copy(position = position)))
        return true
    }

    @Transaction
    open suspend fun createNoteWith(note: NoteEntity, rows: List<AttachmentEntity>) {
        insertNote(note)
        insertAll(rows.mapIndexed { i, r -> r.copy(noteId = note.id, position = i) })
    }

    /** Copies for a duplicated note: same files, new ids. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertCopies(rows: List<AttachmentEntity>)
}
