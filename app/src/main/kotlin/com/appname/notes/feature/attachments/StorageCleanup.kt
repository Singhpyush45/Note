package com.appname.notes.feature.attachments

import com.appname.notes.feature.pdf.PdfImporter

/**
 * Deletes stored files (imported PDFs, attachments) that no note uses any more: after permanent deletes,
 * removed attachments, or an import/attach the app was killed in the middle of. Recent files are always kept.
 */
class StorageCleanup(private val pdf: PdfImporter, private val attachments: Attacher) {
    suspend fun run(): Int = pdf.cleanUp() + attachments.cleanUp()
}
