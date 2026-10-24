package com.appname.notes.feature.attachments

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.core.domain.model.NewAttachment
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

/** Why a file couldn't be attached, for a clear message (FR-009 "storage errors", SRS 15.1 "storage low"). */
enum class AttachError { UNREADABLE, TOO_LARGE, STORAGE_FULL, STORAGE }

sealed interface AttachResult {
    data class Ok(val attachment: NewAttachment) : AttachResult
    data class Failed(val error: AttachError) : AttachResult
}

/**
 * Attached files in app-private storage (`files/attachments`). Written once when attached, then only read;
 * never shown to other apps except one file at a time through the FileProvider when the user opens or shares it.
 *
 * Blocking I/O: call from a background thread.
 */
class AttachmentFileStore(
    context: Context,
    private val logger: AppLogger,
    private val maxBytes: Long = MAX_BYTES,
    /** Free bytes where attachments are stored; replaceable in tests. */
    private val freeBytes: (File) -> Long = { StatFs(it.path).availableBytes },
    /** Files newer than this may belong to an attach still in progress, so cleanup leaves them alone. */
    private val graceMs: Long = GRACE_MS,
) {
    private val resolver: ContentResolver = context.contentResolver
    val dir: File = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(fileName: String): File = File(dir, fileName)

    /** Copies [uri] into storage and describes it. Nothing is left behind on failure. */
    fun import(uri: Uri): AttachResult {
        val (name, declaredSize) = nameAndSize(uri)
        val displayName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "File"
        val mime = mimeOf(uri, displayName)
        if (declaredSize != null && declaredSize > maxBytes) return AttachResult.Failed(AttachError.TOO_LARGE)
        // Unknown size (some apps don't say): at least the safety reserve must be free.
        if (freeBytes(dir) < (declaredSize ?: 0) + RESERVE_BYTES) return AttachResult.Failed(AttachError.STORAGE_FULL)
        val ext = extensionFor(displayName, mime)
        val target = File(dir, UUID.randomUUID().toString() + ext)
        val temp = File(dir, "${target.name}.part")
        try {
            val size = copy(uri, temp) ?: return AttachResult.Failed(AttachError.TOO_LARGE)
            val d = describe(temp, mime)
            if (!temp.renameTo(target)) return AttachResult.Failed(AttachError.STORAGE)
            return AttachResult.Ok(NewAttachment(d.kind, target.name, displayName, mime, size, d.width, d.height, d.durationMs))
        } catch (e: IOException) {
            // Most often the disk filled up while copying.
            val full = freeBytes(dir) < RESERVE_BYTES || e.message?.contains("ENOSPC") == true || e.message?.contains("No space") == true
            logger.warn(TAG, "Attach failed", e)
            return AttachResult.Failed(if (full) AttachError.STORAGE_FULL else AttachError.UNREADABLE)
        } catch (e: SecurityException) { // permission to the picked file was revoked
            logger.warn(TAG, "Attach not permitted", e)
            return AttachResult.Failed(AttachError.UNREADABLE)
        } catch (e: IllegalArgumentException) { // a URI no app can open any more
            return AttachResult.Failed(AttachError.UNREADABLE)
        } finally {
            temp.delete()
        }
    }

    /**
     * Deletes stored files no attachment refers to (after permanent deletes, removed attachments, interrupted
     * attaches). Recent files are kept: an attach writes the file before the database row.
     */
    fun deleteUnreferenced(referenced: Set<String>): Int {
        var deleted = 0
        val staleBefore = System.currentTimeMillis() - graceMs
        dir.listFiles()?.forEach { f ->
            if (f.name !in referenced && f.lastModified() <= staleBefore && f.delete()) deleted++
        }
        return deleted
    }

    private fun nameAndSize(uri: Uri): Pair<String?, Long?> {
        // A plain file (e.g. our own camera photo) has no provider to ask: read it from the file system.
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val f = uri.path?.let(::File)
            return f?.name to f?.takeIf { it.isFile }?.length()
        }
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null to null
                val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getString)
                val s = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong)?.takeIf { it >= 0 }
                n to s
            } ?: (null to null)
        } catch (e: SecurityException) {
            null to null
        } catch (e: IllegalArgumentException) {
            null to null
        } catch (e: UnsupportedOperationException) {
            null to null
        }
    }

    private fun mimeOf(uri: Uri, displayName: String): String {
        val fromResolver = try { resolver.getType(uri) } catch (e: SecurityException) { null }
        if (fromResolver != null && fromResolver != "application/octet-stream") return fromResolver
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: fromResolver ?: "application/octet-stream"
    }

    /** Keeps a sensible extension so other apps recognise the file when opened from the note. */
    private fun extensionFor(displayName: String, mime: String): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (fromName.isNotEmpty() && fromName.length <= 8 && fromName.all { it.isLetterOrDigit() }) return ".$fromName"
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { ".$it" } ?: ""
    }

    /** Copies with a size limit. Returns the byte count, or null when larger than [maxBytes]. */
    private fun copy(uri: Uri, out: File): Long? {
        var total = 0L
        val input = resolver.openInputStream(uri) ?: throw IOException("cannot open")
        input.use { ins ->
            out.outputStream().use { os ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) return null
                    os.write(buf, 0, n)
                }
                os.fd.sync() // on disk before the database row says it's there
            }
        }
        return total
    }

    private data class Description(val kind: AttachmentKind, val width: Int? = null, val height: Int? = null, val durationMs: Long? = null)

    /**
     * Kind, displayed pixel size, and length for audio. An "image" that can't be decoded, or "audio" the phone
     * can't read (no length), is kept as a plain file that other apps may still open.
     */
    private fun describe(file: File, mime: String): Description {
        if (mime == "application/pdf") return Description(AttachmentKind.PDF)
        if (mime.startsWith("audio/")) {
            val duration = audioDurationMs(file) ?: return Description(AttachmentKind.FILE)
            return Description(AttachmentKind.AUDIO, durationMs = duration)
        }
        if (!mime.startsWith("image/")) return Description(AttachmentKind.FILE)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Description(AttachmentKind.FILE)
        return if (isRotatedQuarter(file)) {
            Description(AttachmentKind.IMAGE, bounds.outHeight, bounds.outWidth)
        } else {
            Description(AttachmentKind.IMAGE, bounds.outWidth, bounds.outHeight)
        }
    }

    /** Length of an audio file the phone can decode (M4A/AAC/MP3/WAV/OGG...), or null. */
    private fun audioDurationMs(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
        } catch (e: RuntimeException) { // not audio after all, or a format this phone can't read
            null
        } finally {
            try {
                retriever.release()
            } catch (e: java.io.IOException) {
                // nothing to do
            }
        }
    }

    private companion object {
        const val DIR = "attachments"
        const val TAG = "AttachmentFileStore"
        const val MAX_BYTES = 250L * 1024 * 1024
        /** Leave this much free so attaching never fills the phone completely. */
        const val RESERVE_BYTES = 50L * 1024 * 1024
        const val GRACE_MS = 10 * 60 * 1000L
    }
}

/** EXIF orientation of a camera photo: degrees to rotate clockwise, and whether it's mirrored. */
internal fun exifRotation(file: File): Pair<Int, Boolean> = try {
    when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90 to false
        ExifInterface.ORIENTATION_ROTATE_180 -> 180 to false
        ExifInterface.ORIENTATION_ROTATE_270 -> 270 to false
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0 to true
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180 to true
        ExifInterface.ORIENTATION_TRANSPOSE -> 90 to true
        ExifInterface.ORIENTATION_TRANSVERSE -> 270 to true
        else -> 0 to false
    }
} catch (e: IOException) {
    0 to false
}

internal fun isRotatedQuarter(file: File): Boolean = exifRotation(file).first % 180 == 90
