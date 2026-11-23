package com.appname.notes.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.appname.notes.core.database.entity.DueReminderRow
import com.appname.notes.core.database.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

/** Pending reminders whose note is neither trashed nor deleted. */
private const val PENDING_LIVE = """
    FROM reminders r JOIN notes n ON n.id = r.noteId
    WHERE r.firedAt IS NULL AND n.trashedAt IS NULL AND n.deletedAt IS NULL
"""

@Dao
abstract class ReminderDao {

    @Query("SELECT * FROM reminders WHERE noteId = :noteId ORDER BY firedAt IS NOT NULL, triggerAt, createdAt")
    abstract fun observe(noteId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    abstract suspend fun get(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE noteId = :noteId ORDER BY triggerAt")
    abstract suspend fun list(noteId: String): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRow(row: ReminderEntity)

    @Upsert
    abstract suspend fun upsert(row: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    abstract suspend fun delete(id: String): Int

    /** Returns 0 if the note is gone or trashed (no reminders can be set on it then). */
    @Query("SELECT COUNT(*) FROM notes WHERE id = :noteId AND deletedAt IS NULL AND trashedAt IS NULL")
    protected abstract suspend fun liveNote(noteId: String): Int

    /** Adds [row] if its note can take it. */
    @Transaction
    open suspend fun insert(row: ReminderEntity): Boolean {
        if (liveNote(row.noteId) == 0) return false
        insertRow(row)
        return true
    }

    @Query("SELECT MIN(r.triggerAt) $PENDING_LIVE")
    abstract fun observeNextDue(): Flow<Long?>

    @Query("SELECT MIN(r.triggerAt) $PENDING_LIVE")
    abstract suspend fun nextDue(): Long?

    @Query(
        """
        SELECT r.*, n.title AS noteTitle, n.preview AS notePreview, n.noteType, n.contentJson
        $PENDING_LIVE AND r.triggerAt <= :now
        ORDER BY r.triggerAt
        """,
    )
    abstract suspend fun due(now: Long): List<DueReminderRow>

    @Query("UPDATE reminders SET firedAt = :now, updatedAt = :now WHERE id IN (:ids) AND firedAt IS NULL")
    protected abstract suspend fun markFired(ids: List<String>, now: Long)

    @Query("UPDATE reminders SET triggerAt = :next, updatedAt = :now WHERE id = :id AND firedAt IS NULL")
    protected abstract suspend fun advance(id: String, next: Long, now: Long)

    /** Records what [due] returned as handled: one-offs gone off, repeats moved on. */
    @Transaction
    open suspend fun settle(fired: List<String>, advanced: Map<String, Long>, now: Long) {
        fired.chunked(500).forEach { markFired(it, now) }
        advanced.forEach { (id, next) -> advance(id, next, now) }
    }
}
