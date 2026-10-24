package com.appname.notes.core.database

import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.database.entity.AttachmentEntity
import com.appname.notes.core.database.entity.NoteEntity
import com.appname.notes.core.domain.document.DocumentJson
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.preview
import com.appname.notes.core.domain.document.toPlainText
import com.appname.notes.core.domain.model.Attachment
import com.appname.notes.core.domain.model.AttachmentId
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.core.domain.model.NewAttachment
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.repository.AttachmentRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAttachmentRepository(
    db: NotesDatabase,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val logger: AppLogger,
) : AttachmentRepository {

    private val dao = db.attachmentDao()

    override fun observe(noteId: NoteId): Flow<List<Attachment>> = dao.observe(noteId.value).map { rows -> rows.map { it.toDomain() } }

    override suspend fun list(noteId: NoteId): AppResult<List<Attachment>> =
        storageCall(logger, "Load attachments") { dao.list(noteId.value).map { it.toDomain() } }

    override suspend fun add(noteId: NoteId, items: List<NewAttachment>): AppResult<List<Attachment>> {
        if (items.isEmpty()) return AppResult.Success(emptyList())
        val now = clock.now().toEpochMilli()
        return when (val r = storageCall(logger, "Attach files") { dao.append(noteId.value, items.map { it.toEntity(noteId.value, now) }, now) }) {
            is AppResult.Failure -> r
            is AppResult.Success -> r.value?.let { rows -> AppResult.Success(rows.map { it.toDomain() }) }
                ?: AppResult.Failure(DomainError.NotFound("Note", noteId.value))
        }
    }

    override suspend fun remove(id: AttachmentId): AppResult<Attachment> {
        val row = dao.get(id.value) ?: return AppResult.Failure(DomainError.NotFound("Attachment", id.value))
        return when (val r = storageCall(logger, "Remove attachment") { dao.remove(row, clock.now().toEpochMilli()) }) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value) AppResult.Success(row.toDomain()) else AppResult.Failure(DomainError.NotFound("Attachment", id.value))
        }
    }

    override suspend fun restore(attachment: Attachment): AppResult<Unit> =
        when (val r = storageCall(logger, "Restore attachment") { dao.restore(attachment.toEntity(), clock.now().toEpochMilli()) }) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value) AppResult.Success(Unit) else AppResult.Failure(DomainError.NotFound("Note", attachment.noteId.value))
        }

    override suspend fun createNoteWith(notebookId: NotebookId?, title: String, items: List<NewAttachment>): AppResult<NoteId> =
        storageCall(logger, "Create note with attachments") {
            val now = clock.now().toEpochMilli()
            val noteId = ids.newId()
            val document = NoteDocument.empty(ids::newId)
            dao.createNoteWith(
                NoteEntity(
                    id = noteId,
                    notebookId = notebookId?.value,
                    title = title.trim(),
                    contentJson = DocumentJson.encode(document),
                    plainText = document.toPlainText(),
                    preview = document.preview(),
                    noteType = NoteType.TEXT.name,
                    isPinned = false,
                    isFavourite = false,
                    isArchived = false,
                    createdAt = now,
                    updatedAt = now,
                    trashedAt = null,
                    deletedAt = null,
                    revision = 1,
                ),
                items.map { it.toEntity(noteId, now) },
            )
            NoteId(noteId)
        }

    override suspend fun referencedFiles(): AppResult<Set<String>> =
        storageCall(logger, "List attachment files") { dao.fileNames().toSet() }

    private fun NewAttachment.toEntity(noteId: String, now: Long) = AttachmentEntity(
        id = id?.value ?: ids.newId(),
        noteId = noteId,
        kind = kind.name,
        fileName = fileName,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        position = 0, // assigned by the DAO
        createdAt = now,
        durationMs = durationMs,
    )
}

internal fun AttachmentEntity.toDomain() = Attachment(
    id = AttachmentId(id),
    noteId = NoteId(noteId),
    kind = AttachmentKind.fromName(kind),
    fileName = fileName,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    position = position,
    createdAt = Instant.ofEpochMilli(createdAt),
    durationMs = durationMs,
)

internal fun Attachment.toEntity() = AttachmentEntity(
    id = id.value,
    noteId = noteId.value,
    kind = kind.name,
    fileName = fileName,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    position = position,
    createdAt = createdAt.toEpochMilli(),
    durationMs = durationMs,
)
