package com.appname.notes.core.domain.model

import java.time.Instant

@JvmInline value class AttachmentId(val value: String)

/**
 * What an attachment is, which decides its preview and how it opens (FR-009). Stored by name: never rename.
 * An unknown name from a newer app version is read as [FILE], so it still shows and opens.
 */
enum class AttachmentKind {
    IMAGE,
    PDF,
    FILE,
    ;

    companion object {
        fun fromName(name: String): AttachmentKind = entries.firstOrNull { it.name == name } ?: FILE
    }
}

/**
 * A file attached to a note. The bytes live in app-private storage as [fileName] and are never modified;
 * duplicated notes share the file (it is deleted once no note uses it).
 */
data class Attachment(
    val id: AttachmentId,
    val noteId: NoteId,
    val kind: AttachmentKind,
    val fileName: String,
    /** The name the user saw, e.g. "Board photo.jpg". */
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Pixel size as displayed (EXIF rotation applied), for images. */
    val width: Int?,
    val height: Int?,
    /** Order in the note, 0 first. */
    val position: Int,
    val createdAt: Instant,
)

/** A file already copied into app storage, to be attached to a note. */
data class NewAttachment(
    val kind: AttachmentKind,
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
)
