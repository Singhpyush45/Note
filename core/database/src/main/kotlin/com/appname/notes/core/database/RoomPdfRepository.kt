package com.appname.notes.core.database

import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.database.entity.InkPageEntity
import com.appname.notes.core.database.entity.NoteEntity
import com.appname.notes.core.database.entity.PdfDocumentEntity
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.repository.PdfDocumentInfo
import com.appname.notes.core.domain.repository.PdfRepository
import com.appname.notes.core.ink.model.PageTemplate

class RoomPdfRepository(
    db: NotesDatabase,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val logger: AppLogger,
) : PdfRepository {

    private val dao = db.inkDao()

    override suspend fun createPdfNote(
        notebookId: NotebookId?,
        title: String,
        document: PdfDocumentInfo,
        pageSizes: List<Pair<Float, Float>>,
    ): AppResult<NoteId> {
        if (pageSizes.isEmpty() || pageSizes.size != document.pageCount) {
            return AppResult.Failure(DomainError.Validation("pages", "page sizes must match the page count"))
        }
        return storageCall(logger, "Create PDF note") {
            val now = clock.now().toEpochMilli()
            val noteId = ids.newId()
            dao.createPdfNote(
                NoteEntity(
                    id = noteId,
                    notebookId = notebookId?.value,
                    title = title.trim(),
                    contentJson = "{}",
                    plainText = "",
                    preview = "",
                    noteType = NoteType.PDF.name,
                    isPinned = false,
                    isFavourite = false,
                    isArchived = false,
                    createdAt = now,
                    updatedAt = now,
                    trashedAt = null,
                    deletedAt = null,
                    revision = 1,
                ),
                PdfDocumentEntity(
                    noteId = noteId,
                    fileName = document.fileName,
                    displayName = document.displayName,
                    sha256 = document.sha256,
                    sizeBytes = document.sizeBytes,
                    pageCount = document.pageCount,
                    importedAt = now,
                ),
                pageSizes.mapIndexed { i, (w, h) ->
                    InkPageEntity(
                        id = ids.newId(),
                        noteId = noteId,
                        pageIndex = i,
                        template = PageTemplate.BLANK.code,
                        width = w,
                        height = h,
                        createdAt = now,
                        updatedAt = now,
                        pdfPageIndex = i,
                    )
                },
            )
            NoteId(noteId)
        }
    }

    override suspend fun document(noteId: NoteId): AppResult<PdfDocumentInfo?> =
        storageCall(logger, "Load PDF") { dao.pdfDocument(noteId.value)?.toInfo() }

    override suspend fun referencedFiles(): AppResult<Set<String>> =
        storageCall(logger, "List PDF files") { dao.pdfFileNames().toSet() }

    private fun PdfDocumentEntity.toInfo() = PdfDocumentInfo(fileName, displayName, sha256, sizeBytes, pageCount)
}
