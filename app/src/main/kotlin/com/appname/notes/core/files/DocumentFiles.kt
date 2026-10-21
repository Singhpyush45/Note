package com.appname.notes.core.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/** A text file picked by the user through the system file picker. */
data class PickedText(val displayName: String, val text: String)

/**
 * Reads and writes documents the user chose with the Storage Access Framework (no storage permission
 * needed). Runs on the IO dispatcher and maps failures to typed errors (SRS 13.1 safe file handling).
 */
@Singleton
class DocumentFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun readText(uri: Uri): AppResult<PickedText> = withContext(dispatchers.io) {
        try {
            val name = displayName(uri) ?: "Imported note"
            val bytes = resolver.openInputStream(uri)?.use { input ->
                // Read at most MAX_BYTES + 1 so huge files are rejected without loading them.
                val buffer = input.readNBytesCompat(MAX_BYTES + 1)
                if (buffer.size > MAX_BYTES) return@withContext AppResult.Failure(DomainError.UnsupportedFormat("too-large"))
                buffer
            } ?: return@withContext AppResult.Failure(DomainError.NotFound("File", "uri"))
            if (bytes.any { it == 0.toByte() }) return@withContext AppResult.Failure(DomainError.UnsupportedFormat("binary"))
            AppResult.Success(PickedText(name, bytes.toString(Charsets.UTF_8).removePrefix(BYTE_ORDER_MARK)))
        } catch (e: IOException) {
            logger.warn(TAG, "Import read failed", e)
            AppResult.Failure(DomainError.Storage())
        } catch (e: SecurityException) {
            logger.warn(TAG, "Import not permitted", e)
            AppResult.Failure(DomainError.PermissionDenied("document"))
        }
    }

    suspend fun writeText(uri: Uri, text: String): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: return@withContext AppResult.Failure(DomainError.NotFound("File", "uri"))
            AppResult.Success(Unit)
        } catch (e: IOException) {
            logger.warn(TAG, "Export write failed", e)
            AppResult.Failure(DomainError.Storage())
        } catch (e: SecurityException) {
            logger.warn(TAG, "Export not permitted", e)
            AppResult.Failure(DomainError.PermissionDenied("document"))
        }
    }

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.substringBeforeLast('.')

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val read = read(chunk, 0, minOf(chunk.size, limit - out.size()))
            if (read < 0) break
            out.write(chunk, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val TAG = "DocumentFiles"
        const val MAX_BYTES = 2 * 1024 * 1024

        /** Some editors (e.g. Windows Notepad) start UTF-8 files with this invisible marker. */
        const val BYTE_ORDER_MARK = "\uFEFF"
    }
}
