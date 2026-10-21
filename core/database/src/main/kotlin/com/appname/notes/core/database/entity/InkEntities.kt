package com.appname.notes.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Handwriting tables (schema v2, ADR-0009). One row per page and one row per stroke/shape, so saving a new
 * stroke inserts one small row instead of rewriting the page.
 */

@Entity(
    tableName = "ink_pages",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["noteId", "pageIndex"])],
)
data class InkPageEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    /** 0-based order in the note (not unique-indexed: inserting a page shifts the others in one UPDATE). */
    val pageIndex: Int,
    /** PageTemplate.code */
    val template: Int,
    val width: Float,
    val height: Float,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "ink_items",
    foreignKeys = [
        ForeignKey(entity = InkPageEntity::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["pageId", "z"])],
)
class InkItemEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    /** Paint order on the page (InkItem.z). */
    val z: Long,
    /** InkCodec bytes (starts with its own format version). */
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val data: ByteArray,
)
