package com.appname.notes.feature.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.domain.repository.PdfDocumentInfo
import com.appname.notes.core.ink.pdf.PdfPageBox
import com.appname.notes.core.ink.pdf.PdfPageMapping
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Why an import didn't work, for a clear message (SRS 7.1: unsupported/corrupt states without crashing). */
enum class PdfImportError { NOT_A_PDF, PASSWORD_PROTECTED, TOO_LARGE, EMPTY, STORAGE }

sealed interface PdfImportResult {
    /** The file is in app storage as [PdfDocumentInfo.fileName]; [pageSizes] in page units, as displayed. */
    data class Ok(val document: PdfDocumentInfo, val pageSizes: List<Pair<Float, Float>>, val title: String) : PdfImportResult
    data class Failed(val error: PdfImportError) : PdfImportResult
}

/**
 * Imported PDFs in app-private storage (`files/pdfs`). Files are written once at import and then only read:
 * the user's original is never modified (SRS 7.1, PDF-05). Not backed up (backup is off until FR-025).
 *
 * Blocking I/O: call from a background thread.
 */
class PdfFileStore(
    context: Context,
    private val writer: PdfAnnotationWriter,
    private val logger: AppLogger,
    /** Files newer than this may belong to an import still in progress, so cleanup leaves them alone. */
    private val importGraceMs: Long = IMPORT_GRACE_MS,
) {
    private val resolver: ContentResolver = context.contentResolver
    val dir: File = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(fileName: String): File = File(dir, fileName)

    fun import(uri: Uri): PdfImportResult {
        val displayName = displayName(uri)
        val target = File(dir, "${UUID.randomUUID()}.pdf")
        val temp = File(dir, "${target.name}.part")
        try {
            val (size, sha) = copyWithDigest(uri, temp) ?: return PdfImportResult.Failed(PdfImportError.TOO_LARGE)
            if (size == 0L) return PdfImportResult.Failed(PdfImportError.EMPTY)
            val sizes = when (val check = pageSizes(temp)) {
                is PageCheck.Ok -> check.sizes
                is PageCheck.Bad -> return PdfImportResult.Failed(check.error)
            }
            if (!temp.renameTo(target)) return PdfImportResult.Failed(PdfImportError.STORAGE)
            val title = displayName.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { "PDF" }
            return PdfImportResult.Ok(
                PdfDocumentInfo(target.name, displayName, sha, size, sizes.size),
                sizes,
                title,
            )
        } catch (e: IOException) {
            logger.warn(TAG, "PDF import failed", e)
            return PdfImportResult.Failed(PdfImportError.STORAGE)
        } catch (e: SecurityException) { // the picker's permission was revoked
            logger.warn(TAG, "PDF import not permitted", e)
            return PdfImportResult.Failed(PdfImportError.STORAGE)
        } finally {
            temp.delete()
        }
    }

    /**
     * Deletes stored PDFs no note refers to any more (after permanent deletes) and leftovers of failed imports.
     *
     * An import writes "x.pdf.part", renames it to "x.pdf" and only then creates the note, so for a moment a
     * new file is unreferenced. Recent files are therefore kept; they are removed by a later cleanup if still unused.
     */
    fun deleteUnreferenced(referenced: Set<String>): Int {
        var deleted = 0
        val staleBefore = System.currentTimeMillis() - importGraceMs
        dir.listFiles()?.forEach { f ->
            val maybeInProgress = f.lastModified() > staleBefore
            if (f.name !in referenced && !maybeInProgress && f.delete()) deleted++
        }
        return deleted
    }

    private fun displayName(uri: Uri): String = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "PDF"

    /** Copies [uri] to [out] and returns (bytes, SHA-256 hex), or null when larger than [MAX_BYTES]. */
    private fun copyWithDigest(uri: Uri, out: File): Pair<Long, String>? {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val input = resolver.openInputStream(uri) ?: throw IOException("cannot open")
        input.use { ins ->
            out.outputStream().use { os ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) return null
                    digest.update(buf, 0, n)
                    os.write(buf, 0, n)
                }
            }
        }
        return total to digest.digest().joinToString("") { "%02x".format(it) }
    }

    private sealed interface PageCheck {
        data class Ok(val sizes: List<Pair<Float, Float>>) : PageCheck
        data class Bad(val error: PdfImportError) : PageCheck
    }

    /**
     * Opens the file with Android's renderer (the one that will display it) to validate it, and reads exact page
     * boxes with PdfBox. If PdfBox can't parse a file the renderer can show, the renderer's sizes are used.
     */
    private fun pageSizes(file: File): PageCheck {
        val rendererSizes = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r ->
                    List(r.pageCount) { i ->
                        r.openPage(i).use { p -> (p.width / PdfPageMapping.POINTS_PER_UNIT).toFloat() to (p.height / PdfPageMapping.POINTS_PER_UNIT).toFloat() }
                    }
                }
            }
        } catch (e: SecurityException) {
            return PageCheck.Bad(PdfImportError.PASSWORD_PROTECTED)
        } catch (e: IOException) {
            return PageCheck.Bad(PdfImportError.NOT_A_PDF)
        } catch (e: RuntimeException) { // e.g. native library unavailable
            logger.warn(TAG, "PDF renderer failed", e)
            return PageCheck.Bad(PdfImportError.NOT_A_PDF)
        }
        if (rendererSizes.isEmpty()) return PageCheck.Bad(PdfImportError.EMPTY)
        val boxes: List<PdfPageBox>? = try {
            writer.readPageBoxes(file).takeIf { it.size == rendererSizes.size }
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
        return PageCheck.Ok(boxes?.map { PdfPageMapping.displayedSize(it) } ?: rendererSizes)
    }

    private companion object {
        const val DIR = "pdfs"
        const val TAG = "PdfFileStore"
        const val MAX_BYTES = 300L * 1024 * 1024
        const val IMPORT_GRACE_MS = 10 * 60 * 1000L
    }
}
