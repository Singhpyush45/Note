package com.appname.notes.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A file attached to a note (schema v4, FR-009). The bytes live in app-private storage under [fileName]
 * and are never modified; duplicated notes share the file, which is deleted once no row refers to it.
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["noteId", "position"]), Index("fileName")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    /** AttachmentKind name. */
    val kind: String,
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val position: Int,
    val createdAt: Long,
    /** Length of a recording (schema v6). */
    val durationMs: Long? = null,
)
