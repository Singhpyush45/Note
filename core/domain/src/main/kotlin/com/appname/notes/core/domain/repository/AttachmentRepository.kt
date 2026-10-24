package com.appname.notes.core.domain.repository

import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.Attachment
import com.appname.notes.core.domain.model.AttachmentId
import com.appname.notes.core.domain.model.NewAttachment
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NotebookId
import kotlinx.coroutines.flow.Flow

/** Files attached to notes (FR-009). Adding or removing one counts as editing the note. */
interface AttachmentRepository {
    /** The note's attachments in order; updates live. */
    fun observe(noteId: NoteId): Flow<List<Attachment>>

    suspend fun list(noteId: NoteId): AppResult<List<Attachment>>

    /** Appends [items] to the note's attachments, in that order. */
    suspend fun add(noteId: NoteId, items: List<NewAttachment>): AppResult<List<Attachment>>

    /** Removes one attachment and returns it, so it can be put back ([restore]) for undo. */
    suspend fun remove(id: AttachmentId): AppResult<Attachment>

    /** Changes the name shown for an attachment (the stored file keeps its name). Blank names are refused. */
    suspend fun rename(id: AttachmentId, displayName: String): AppResult<Unit>

    /** Puts a removed attachment back at its place. */
    suspend fun restore(attachment: Attachment): AppResult<Unit>

    /** New text note holding [items] (e.g. photos shared from another app), all or nothing. */
    suspend fun createNoteWith(notebookId: NotebookId?, title: String, items: List<NewAttachment>): AppResult<NoteId>

    /** Files still used by some attachment (also of trashed notes). Any other stored attachment file can go. */
    suspend fun referencedFiles(): AppResult<Set<String>>
}
