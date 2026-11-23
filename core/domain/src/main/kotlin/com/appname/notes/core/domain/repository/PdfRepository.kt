package com.appname.notes.core.domain.repository

import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NotebookId

/** An imported PDF as stored for a note. The file is in app-private storage under [fileName]. */
data class PdfDocumentInfo(
    val fileName: String,
    val displayName: String,
    val sha256: String,
    val sizeBytes: Long,
    val pageCount: Int,
)

/** PDF notes (ADR-0009 Part B). Handwriting on their pages goes through [InkRepository] like any ink page. */
interface PdfRepository {
    /**
     * New PDF note for a file already copied into app storage, with one ink page per PDF page sized as it is
     * displayed ([pageSizes] in page units, in PDF page order).
     */
    suspend fun createPdfNote(
        notebookId: NotebookId?,
        title: String,
        document: PdfDocumentInfo,
        pageSizes: List<Pair<Float, Float>>,
    ): AppResult<NoteId>

    suspend fun document(noteId: NoteId): AppResult<PdfDocumentInfo?>

    /** Files still used by some note (also trashed ones). Any other stored PDF file can be deleted. */
    suspend fun referencedFiles(): AppResult<Set<String>>
}
