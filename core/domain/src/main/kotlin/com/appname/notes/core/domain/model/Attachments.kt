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
    /** A voice recording made in the app (FR-012). */
    AUDIO,
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
    /** Length of a recording. */
    val durationMs: Long? = null,
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
    val durationMs: Long? = null,
    /** Use this id instead of a new one (a recording's markers refer to it before it is saved). */
    val id: AttachmentId? = null,
)

/**
 * A point in a recording, written into the note's text as a link (`audio:<attachmentId>?t=<ms>`), so it is
 * undoable, saved and searchable like the rest of the note (FR-012 "timestamped note markers").
 */
data class AudioMarker(val attachmentId: AttachmentId, val positionMs: Long) {
    val url: String get() = "$SCHEME:${attachmentId.value}?t=$positionMs"

    /** "12:40", or "1:02:03" past an hour. */
    val label: String get() = formatDuration(positionMs)

    companion object {
        const val SCHEME = "audio"

        fun parse(url: String): AudioMarker? {
            if (!url.startsWith("$SCHEME:")) return null
            val id = url.substringAfter(':').substringBefore('?').takeIf { it.isNotBlank() } ?: return null
            val t = url.substringAfter("?t=", "").toLongOrNull()?.takeIf { it >= 0 } ?: return null
            return AudioMarker(AttachmentId(id), t)
        }

        fun formatDuration(ms: Long): String {
            val total = (ms.coerceAtLeast(0) / 1000)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
