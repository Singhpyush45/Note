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
    /** For PDF notes: the 0-based PDF page drawn under this page's ink (schema v3). Null for plain ink pages. */
    val pdfPageIndex: Int? = null,
)

/**
 * The imported file of a PDF note (schema v3). The file itself lives in app-private storage under
 * [fileName] and is never modified; duplicates of the note share it (it is deleted when unreferenced).
 */
@Entity(
    tableName = "pdf_documents",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("fileName")],
)
data class PdfDocumentEntity(
    @PrimaryKey val noteId: String,
    val fileName: String,
    /** Name of the file the user imported, e.g. "Lecture 5.pdf". */
    val displayName: String,
    /** SHA-256 of the file at import, hex. Lets us prove the original was never changed (PDF-05). */
    val sha256: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val importedAt: Long,
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
