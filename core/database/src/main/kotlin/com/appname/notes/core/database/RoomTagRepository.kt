package com.appname.notes.core.database

import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.database.entity.TagEntity
import com.appname.notes.core.domain.model.Tag
import com.appname.notes.core.domain.model.TagId
import com.appname.notes.core.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomTagRepository(
    private val db: NotesDatabase,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val logger: AppLogger,
) : TagRepository {

    private val dao = db.tagDao()

    override fun observeTags(): Flow<List<Tag>> =
        dao.observeTags().map { rows -> rows.map { Tag(TagId(it.id), it.name, it.noteCount) } }.distinctUntilChanged()

    override suspend fun createOrGet(name: String): AppResult<Tag> {
        val clean = clean(name) ?: return AppResult.Failure(DomainError.Validation("name", "empty"))
        return storageCall(logger, "Create tag") {
            dao.findByName(clean)?.toDomain() ?: run {
                val tag = TagEntity(ids.newId(), clean, clock.now().toEpochMilli())
                dao.insert(tag)
                tag.toDomain()
            }
        }
    }

    override suspend fun rename(id: TagId, name: String): AppResult<Unit> {
        val clean = clean(name) ?: return AppResult.Failure(DomainError.Validation("name", "empty"))
        val existing = dao.findByName(clean)
        if (existing != null && existing.id != id.value) return AppResult.Failure(DomainError.Validation("name", "exists"))
        return storageCall(logger, "Rename tag") { dao.rename(id.value, clean); Unit }
    }

    override suspend fun delete(id: TagId): AppResult<Unit> = storageCall(logger, "Delete tag") { dao.delete(id.value) }

    /** Tags are single words or short phrases; leading '#' is dropped so "#exam" and "exam" are the same tag. */
    private fun clean(name: String): String? =
        name.trim().removePrefix("#").trim().replace(Regex("\\s+"), " ").take(MAX_NAME).takeIf { it.isNotEmpty() }

    private companion object {
        const val MAX_NAME = 40
    }
}
