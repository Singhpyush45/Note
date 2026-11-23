package com.appname.notes.core.domain.repository

import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.ink.template.TemplateDefinition
import com.appname.notes.core.ink.template.TemplateLayout
import kotlinx.coroutines.flow.Flow

/**
 * Templates (owner brief §4): the built-in ones plus the user's own. Built-ins can't be changed or deleted;
 * duplicating one gives an editable custom copy.
 */
interface TemplateRepository {
    /** The user's templates, oldest first. */
    fun observeCustom(): Flow<List<TemplateDefinition>>

    /** A built-in or custom template by id, or null if there's no such template (any more). */
    suspend fun get(id: String): TemplateDefinition?

    /** Saves a new custom template and returns its id. */
    suspend fun create(name: String, layout: TemplateLayout): AppResult<String>

    /** Changes a custom template's name and design. Notes already made from it keep their own copy. */
    suspend fun update(id: String, name: String, layout: TemplateLayout): AppResult<Unit>

    suspend fun rename(id: String, name: String): AppResult<Unit>

    /** A custom copy of any template ("Weekly planner copy"); returns the copy's id. */
    suspend fun duplicate(id: String): AppResult<String>

    suspend fun delete(id: String): AppResult<Unit>

    companion object {
        const val MAX_NAME = 60

        /** The name as stored: trimmed and at most [MAX_NAME] characters; null if blank. */
        fun cleanName(name: String): String? = name.trim().take(MAX_NAME).trim().ifEmpty { null }
    }
}
