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
                parentId = it.notebook.parentId?.let(::NotebookId),
            )
        }
    }.distinctUntilChanged()

    override suspend fun create(title: String, parent: NotebookId?): AppResult<NotebookId> {
        val clean = validTitle(title) ?: return invalid()
        return storageCall(logger, "Create notebook") {
            val now = clock.now().toEpochMilli()
            val id = ids.newId()
            // A parent that no longer exists puts the folder at the top level.
            val parentId = parent?.value?.takeIf { dao.get(it) != null }
            dao.insert(NotebookEntity(id, clean, dao.maxSortOrder() + 1, now, now, parentId = parentId))
            NotebookId(id)
        }
    }

    override suspend fun move(id: NotebookId, parent: NotebookId?): AppResult<Unit> {
        val r = storageCall(logger, "Move notebook") {
            val links = dao.links()
            if (links.none { it.id == id.value }) return@storageCall MoveResult.NOT_FOUND
            if (parent != null) {
                if (links.none { it.id == parent.value }) return@storageCall MoveResult.NOT_FOUND
                if (parent.value in subtree(links, id.value)) return@storageCall MoveResult.INTO_ITSELF
            }
            dao.setParent(id.value, parent?.value, clock.now().toEpochMilli())
            MoveResult.OK
        }
        return when (r) {
            is AppResult.Failure -> r
            is AppResult.Success -> when (r.value) {
                MoveResult.OK -> AppResult.Success(Unit)
                MoveResult.NOT_FOUND -> AppResult.Failure(DomainError.NotFound("Notebook", id.value))
                MoveResult.INTO_ITSELF -> AppResult.Failure(DomainError.Validation("parent", "inside_itself"))
            }
        }
    }

    private enum class MoveResult { OK, NOT_FOUND, INTO_ITSELF }

    private fun subtree(links: List<com.appname.notes.core.database.dao.FolderLink>, root: String): List<String> {
        val out = mutableListOf(root)
        var i = 0
        while (i < out.size) {
            val cur = out[i++]
            links.forEach { if (it.parentId == cur && it.id !in out) out += it.id }
        }
        return out
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
            // Children first, then the folder itself.
            dao.deleteTree(subtree(dao.links(), id.value).reversed(), trashNotes = mode == NotebookDeleteMode.TRASH_NOTES, now = clock.now().toEpochMilli())
        }

    private fun validTitle(title: String): String? = title.trim().take(MAX_TITLE).takeIf { it.isNotEmpty() }

    private fun invalid() = AppResult.Failure(DomainError.Validation("title", "empty"))

    private companion object {
        const val MAX_TITLE = 80
    }
}
