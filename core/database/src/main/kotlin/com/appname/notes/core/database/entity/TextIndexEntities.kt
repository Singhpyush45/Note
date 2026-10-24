package com.appname.notes.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * Document text index (schema v5, FR-011): text of PDF pages, attached PDFs and photos, so search finds
 * words inside documents. Rows are rebuilt per source; deleting an attachment or note deletes its rows.
 */

@Entity(
    tableName = "extracted_text",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AttachmentEntity::class, parentColumns = ["id"], childColumns = ["attachmentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("noteId"), Index("attachmentId"), Index(value = ["sourceKey", "pageIndex"], unique = true)],
)
data class ExtractedTextEntity(
    /** Integer key = SQLite rowid, which the FTS index refers to. */
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    /** TextSource.key: "a:<attachmentId>" or "p:<noteId>". */
    val sourceKey: String,
    val noteId: String,
    val attachmentId: String?,
    val pageIndex: Int,
    val text: String,
    /** ExtractionMethod name. */
    val method: String,
)

/** Full-text index over [ExtractedTextEntity.text], kept in sync by Room-generated triggers. */
@Fts4(contentEntity = ExtractedTextEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "extracted_text_fts")
data class ExtractedTextFtsEntity(val text: String)

/** What happened when a source was last processed, so each is done once and failures retried a few times. */
@Entity(
    tableName = "text_index_jobs",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AttachmentEntity::class, parentColumns = ["id"], childColumns = ["attachmentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("noteId"), Index("attachmentId")],
)
data class TextIndexJobEntity(
    @PrimaryKey val sourceKey: String,
    val noteId: String,
    val attachmentId: String?,
    /** IndexStatus name. */
    val status: String,
    val attempts: Int,
    val updatedAt: Long,
)

/** Row of the pending-sources query. */
data class PendingSourceRow(
    val noteId: String,
    val attachmentId: String?,
    val kind: String?,
    val fileName: String,
)

/** A search match inside a document. */
data class DocumentHitRow(
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
    val nextReminderAt: Long?,
    val snippet: String,
    val attachmentId: String?,
    val attachmentName: String?,
    val pageIndex: Int,
    val attachmentKind: String?,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(NoteTagCrossRef::class, parentColumn = "noteId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
)
