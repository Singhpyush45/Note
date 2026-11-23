package com.appname.notes.core.database

import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.database.entity.CustomTemplateEntity
import com.appname.notes.core.domain.repository.TemplateRepository
import com.appname.notes.core.ink.template.BuiltInTemplates
import com.appname.notes.core.ink.template.TemplateCategory
import com.appname.notes.core.ink.template.TemplateDefinition
import com.appname.notes.core.ink.template.TemplateJson
import com.appname.notes.core.ink.template.TemplateLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTemplateRepository(
    db: NotesDatabase,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val logger: AppLogger,
) : TemplateRepository {

    private val dao = db.templateDao()

    override fun observeCustom(): Flow<List<TemplateDefinition>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toDefinition() } }

    override suspend fun get(id: String): TemplateDefinition? =
        BuiltInTemplates.byId(id) ?: (storageCall(logger, "Load template") { dao.get(id) } as? AppResult.Success)?.value?.toDefinition()

    override suspend fun create(name: String, layout: TemplateLayout): AppResult<String> {
        val clean = TemplateRepository.cleanName(name) ?: return invalidName()
        val now = clock.now().toEpochMilli()
        val id = ids.newId()
        return storageCall(logger, "Save template") {
            dao.insert(CustomTemplateEntity(id, clean, TemplateJson.encode(layout), now, now))
            id
        }
    }

    override suspend fun update(id: String, name: String, layout: TemplateLayout): AppResult<Unit> {
        val clean = TemplateRepository.cleanName(name) ?: return invalidName()
        return changed(id, storageCall(logger, "Save template") { dao.update(id, clean, TemplateJson.encode(layout), clock.now().toEpochMilli()) })
    }

    override suspend fun rename(id: String, name: String): AppResult<Unit> {
        val clean = TemplateRepository.cleanName(name) ?: return invalidName()
        return changed(id, storageCall(logger, "Rename template") { dao.rename(id, clean, clock.now().toEpochMilli()) })
    }

    override suspend fun duplicate(id: String): AppResult<String> {
        val source = get(id) ?: return AppResult.Failure(DomainError.NotFound("Template", id))
        return create(copyName(source.name), source.layout)
    }

    override suspend fun delete(id: String): AppResult<Unit> =
        changed(id, storageCall(logger, "Delete template") { dao.delete(id) })

    private fun changed(id: String, result: AppResult<Int>): AppResult<Unit> = when (result) {
        is AppResult.Failure -> result
        // Built-in ids never match a row, so they can't be changed or deleted.
        is AppResult.Success -> if (result.value == 0) AppResult.Failure(DomainError.NotFound("Template", id)) else AppResult.Success(Unit)
    }

    private fun invalidName(): AppResult<Nothing> = AppResult.Failure(DomainError.Validation("name", "blank"))

    /** A row whose design can't be read (damaged, or from a much newer version) is left out, not a crash. */
    private fun CustomTemplateEntity.toDefinition(): TemplateDefinition? {
        val layout = TemplateJson.decodeOrNull(layoutJson)
        if (layout == null) {
            logger.warn(TAG, "Unreadable custom template ${id.take(8)}…")
            return null
        }
        return TemplateDefinition(id, name, TemplateCategory.CUSTOM, layout)
    }

    private companion object {
        const val TAG = "TemplateRepository"

        fun copyName(name: String): String {
            val suffix = " copy"
            return name.take(TemplateRepository.MAX_NAME - suffix.length) + suffix
        }
    }
}
