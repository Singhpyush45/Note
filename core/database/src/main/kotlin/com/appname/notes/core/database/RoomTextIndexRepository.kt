package com.appname.notes.core.database

import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.database.entity.ExtractedTextEntity
import com.appname.notes.core.domain.model.AttachmentId
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.core.domain.model.IndexStatus
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.PageText
import com.appname.notes.core.domain.model.TextSource
import com.appname.notes.core.domain.repository.TextIndexRepository
import java.time.Instant

class RoomTextIndexRepository(
    db: NotesDatabase,
    private val clock: AppClock,
    private val logger: AppLogger,
) : TextIndexRepository {

    private val dao = db.textIndexDao()

    override suspend fun pending(limit: Int, retryBefore: Instant): AppResult<List<TextSource>> =
        storageCall(logger, "List documents to index") {
            val before = retryBefore.toEpochMilli()
            val max = TextIndexRepository.MAX_ATTEMPTS
            // PDF notes first: they're what people search most (lecture slides), then attachments.
            val pdfs = dao.pendingPdfNotes(limit, before, max).map { TextSource.PdfNoteSource(NoteId(it.noteId), it.fileName) }
            val rest = (limit - pdfs.size).coerceAtLeast(0)
            val attachments = if (rest == 0) emptyList() else dao.pendingAttachments(rest, before, max).map {
                TextSource.AttachmentSource(AttachmentId(it.attachmentId!!), NoteId(it.noteId), AttachmentKind.fromName(it.kind ?: ""), it.fileName)
            }
            pdfs + attachments
        }

    override suspend fun save(source: TextSource, pages: List<PageText>, status: IndexStatus): AppResult<Unit> =
        storageCall(logger, "Save document text") {
            val attachmentId = (source as? TextSource.AttachmentSource)?.attachmentId?.value
            val rows = pages.filter { it.text.isNotBlank() }.map {
                ExtractedTextEntity(
                    sourceKey = source.key,
                    noteId = source.noteId.value,
                    attachmentId = attachmentId,
                    pageIndex = it.page,
                    text = it.text,
                    method = it.method.name,
                )
            }
            dao.save(source.key, source.noteId.value, attachmentId, rows, status.name, clock.now().toEpochMilli())
            Unit
        }

    override suspend fun clear(): AppResult<Unit> = storageCall(logger, "Clear document text") { dao.clear() }

    override suspend fun progress(): AppResult<Pair<Int, Int>> = storageCall(logger, "Document text progress") {
        val done = dao.doneCount()
        val waiting = dao.pendingPdfNotes(LARGE, 0, TextIndexRepository.MAX_ATTEMPTS).size +
            dao.pendingAttachments(LARGE, 0, TextIndexRepository.MAX_ATTEMPTS).size
        done to waiting
    }

    private companion object {
        const val LARGE = 100_000
    }
}
