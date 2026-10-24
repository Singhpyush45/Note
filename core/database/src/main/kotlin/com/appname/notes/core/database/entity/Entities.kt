package com.appname.notes.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * Tables (schema v1). Timestamps are epoch milliseconds (UTC). IDs are UUIDv7 strings.
 * `deletedAt` marks a tombstone: the row is kept (without content) so a future sync can learn about the delete.
 */

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("notebookId"),
        Index("updatedAt"),
        Index(value = ["deletedAt", "trashedAt", "isArchived"]),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val notebookId: String?,
    val title: String,
    /** The block document as JSON (ADR-0007). */
    val contentJson: String,
    /** Plain-text projection of the document, indexed for search. */
    val plainText: String,
    /** Card preview with list markers (see NoteDocument.preview). */
    val preview: String,
    val noteType: String,
    val isPinned: Boolean,
    val isFavourite: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val trashedAt: Long?,
    val deletedAt: Long?,
    /** Incremented on every change; used for sync conflict detection later (SRS 11.2). */
    val revision: Long,
)

/**
 * Full-text index over note title + text, kept in sync with `notes` by Room-generated triggers.
 * See ADR-0008 for the rowid caveat.
 */
@Fts4(contentEntity = NoteEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    val title: String,
    val plainText: String,
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class NoteTagCrossRef(
    val noteId: String,
    val tagId: String,
)

// ------------------------------------------------------------------- query results

/** A row of a note list: no document JSON, just what a card shows. */
data class NoteListRow(
    val id: String,
    val title: String,
    val preview: String,
    val noteType: String,
    val notebookId: String?,
    val notebookTitle: String?,
    val isPinned: Boolean,
    val isFavourite: Boolean,
    val isArchived: Boolean,
    val trashedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val attachmentCount: Int,
    val coverImage: String?,
    /** Next pending reminder (epoch millis), FR-010. */
    val nextReminderAt: Long?,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(NoteTagCrossRef::class, parentColumn = "noteId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
)

data class SearchRow(
    val id: String,
    val title: String,
    val preview: String,
    val noteType: String,
    val notebookId: String?,
    val notebookTitle: String?,
    val isPinned: Boolean,
    val isFavourite: Boolean,
    val isArchived: Boolean,
    val trashedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val attachmentCount: Int,
    val coverImage: String?,
    /** Next pending reminder (epoch millis), FR-010. */
    val nextReminderAt: Long?,
    val snippet: String,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(NoteTagCrossRef::class, parentColumn = "noteId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
)

data class NoteWithTags(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(NoteTagCrossRef::class, parentColumn = "noteId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
)

data class NotebookRow(
    @Embedded val notebook: NotebookEntity,
    val noteCount: Int,
)

data class TagRow(
    val id: String,
    val name: String,
    val noteCount: Int,
)
