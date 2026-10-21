package com.appname.notes.core.database

import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.domain.document.DocumentJson
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.UnknownBlock
import com.appname.notes.core.database.entity.NoteListRow
import com.appname.notes.core.database.entity.NoteWithTags
import com.appname.notes.core.database.entity.SearchRow
import com.appname.notes.core.database.entity.TagEntity
import com.appname.notes.core.domain.model.Note
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.model.SearchHit
import com.appname.notes.core.domain.model.Tag
import com.appname.notes.core.domain.model.TagId
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun String.toNoteType(): NoteType = NoteType.entries.firstOrNull { it.name == this } ?: NoteType.TEXT

internal fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

internal fun TagEntity.toDomain(): Tag = Tag(TagId(id), name)

internal fun NoteListRow.toDomain() = NoteSummary(
    id = NoteId(id),
    title = title,
    preview = preview,
    type = noteType.toNoteType(),
    notebookId = notebookId?.let(::NotebookId),
    notebookTitle = notebookTitle,
    tags = tags.map { it.toDomain() }.sortedBy { it.name.lowercase() },
    isPinned = isPinned,
    isFavourite = isFavourite,
    isArchived = isArchived,
    trashedAt = trashedAt?.toInstant(),
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

internal fun SearchRow.toDomain() = SearchHit(
    note = NoteSummary(
        id = NoteId(id),
        title = title,
        preview = preview,
        type = noteType.toNoteType(),
        notebookId = notebookId?.let(::NotebookId),
        notebookTitle = notebookTitle,
        tags = tags.map { it.toDomain() }.sortedBy { it.name.lowercase() },
        isPinned = isPinned,
        isFavourite = isFavourite,
        isArchived = isArchived,
        trashedAt = trashedAt?.toInstant(),
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
    ),
    snippet = snippet,
)

/**
 * Decodes the stored document. If the JSON is unreadable (corruption, bug), the raw text is wrapped in an
 * [UnknownBlock] so the note opens read-only and nothing is overwritten (SRS 15.1: never destroy content).
 */
internal fun decodeDocument(json: String, noteId: String, logger: AppLogger): NoteDocument = try {
    DocumentJson.decode(json)
} catch (e: Exception) {
    logger.error("NoteRepository", "Unreadable document for note ${noteId.take(8)}…", e)
    NoteDocument(
        blocks = listOf(
            UnknownBlock(id = "corrupt", type = "corrupt", raw = JsonObject(mapOf("type" to JsonPrimitive("corrupt"), "raw" to JsonPrimitive(json)))),
        ),
    )
}

internal fun NoteWithTags.toDomain(logger: AppLogger) = Note(
    id = NoteId(note.id),
    title = note.title,
    document = decodeDocument(note.contentJson, note.id, logger),
    type = note.noteType.toNoteType(),
    notebookId = note.notebookId?.let(::NotebookId),
    tags = tags.map { it.toDomain() }.sortedBy { it.name.lowercase() },
    isPinned = note.isPinned,
    isFavourite = note.isFavourite,
    isArchived = note.isArchived,
    trashedAt = note.trashedAt?.toInstant(),
    createdAt = note.createdAt.toInstant(),
    updatedAt = note.updatedAt.toInstant(),
    revision = note.revision,
)
