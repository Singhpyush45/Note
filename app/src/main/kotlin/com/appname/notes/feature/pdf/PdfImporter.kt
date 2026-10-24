package com.appname.notes.feature.pdf

import android.net.Uri
import androidx.annotation.StringRes
import com.appname.notes.R
import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.repository.PdfRepository
import kotlinx.coroutines.withContext

/** The message shown when an import fails. */
val PdfImportError.messageRes: Int
    @StringRes get() = when (this) {
        PdfImportError.NOT_A_PDF -> R.string.pdf_import_not_pdf
        PdfImportError.PASSWORD_PROTECTED -> R.string.pdf_import_password
        PdfImportError.TOO_LARGE -> R.string.pdf_import_too_large
        PdfImportError.EMPTY -> R.string.pdf_import_empty
        PdfImportError.STORAGE -> R.string.pdf_import_storage
    }

sealed interface PdfImport {
    data class Created(val noteId: NoteId) : PdfImport
    data class Failed(val error: PdfImportError) : PdfImport
}

/** Import use case: file into app storage, then the note with one page per PDF page (all or nothing). */
class PdfImporter(
    private val store: PdfFileStore,
    private val pdf: PdfRepository,
    private val dispatchers: DispatcherProvider,
    /** Called after a PDF note was created (starts text indexing). */
    private val onImported: () -> Unit = {},
) {
    suspend fun import(uri: Uri, notebookId: NotebookId?): PdfImport = withContext(dispatchers.io) {
        when (val r = store.import(uri)) {
            is PdfImportResult.Failed -> PdfImport.Failed(r.error)
            is PdfImportResult.Ok -> when (val created = pdf.createPdfNote(notebookId, r.title, r.document, r.pageSizes)) {
                is AppResult.Success -> PdfImport.Created(created.value).also { onImported() }
                is AppResult.Failure -> {
                    store.file(r.document.fileName).delete() // no note refers to it
                    PdfImport.Failed(PdfImportError.STORAGE)
                }
            }
        }
    }

    /** Removes stored files that no note uses any more. Safe to call any time. */
    suspend fun cleanUp(): Int = withContext(dispatchers.io) {
        when (val refs = pdf.referencedFiles()) {
            is AppResult.Success -> store.deleteUnreferenced(refs.value)
            is AppResult.Failure -> 0
        }
    }
}
