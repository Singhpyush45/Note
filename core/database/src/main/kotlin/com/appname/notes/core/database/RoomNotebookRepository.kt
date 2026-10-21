package com.appname.notes.core.database

import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.database.entity.NotebookEntity
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookDeleteMode
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.repository.NotebookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomNotebookRepository(
    private val db: NotesDatabase,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val logger: AppLogger,
) : NotebookRepository {

    private val dao = db.notebookDao()

    override fun observeNotebooks(): Flow<List<Notebook>> = dao.observeNotebooks().map { rows ->
        rows.map {
            Notebook(
                id = NotebookId(it.notebook.id),
                title = it.notebook.title,
                sortOrder = it.notebook.sortOrder,
                noteCount = it.noteCount,
                createdAt = it.notebook.createdAt.toInstant(),
                updatedAt = it.notebook.updatedAt.toInstant(),
            )
        }
    }.distinctUntilChanged()

    override suspend fun create(title: String): AppResult<NotebookId> {
        val clean = validTitle(title) ?: return invalid()
        return storageCall(logger, "Create notebook") {
            val now = clock.now().toEpochMilli()
            val id = ids.newId()
            dao.insert(NotebookEntity(id, clean, dao.maxSortOrder() + 1, now, now))
            NotebookId(id)
        }
    }

    override suspend fun rename(id: NotebookId, title: String): AppResult<Unit> {
        val clean = validTitle(title) ?: return invalid()
        return when (val r = storageCall(logger, "Rename notebook") { dao.rename(id.value, clean, clock.now().toEpochMilli()) }) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value == 0) AppResult.Failure(DomainError.NotFound("Notebook", id.value)) else AppResult.Success(Unit)
        }
    }

    override suspend fun reorder(orderedIds: List<NotebookId>): AppResult<Unit> =
        storageCall(logger, "Reorder notebooks") { dao.reorder(orderedIds.map { it.value }, clock.now().toEpochMilli()) }

    override suspend fun delete(id: NotebookId, mode: NotebookDeleteMode): AppResult<Unit> =
        storageCall(logger, "Delete notebook") {
            dao.delete(id.value, trashNotes = mode == NotebookDeleteMode.TRASH_NOTES, now = clock.now().toEpochMilli())
        }

    private fun validTitle(title: String): String? = title.trim().take(MAX_TITLE).takeIf { it.isNotEmpty() }

    private fun invalid() = AppResult.Failure(DomainError.Validation("title", "empty"))

    private companion object {
        const val MAX_TITLE = 80
    }
}
