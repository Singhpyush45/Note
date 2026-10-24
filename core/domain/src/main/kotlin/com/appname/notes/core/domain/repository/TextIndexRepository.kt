package com.appname.notes.core.domain.repository

import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.IndexStatus
import com.appname.notes.core.domain.model.PageText
import com.appname.notes.core.domain.model.TextSource
import java.time.Instant

/** Storage for text extracted from documents (FR-011). Search over it goes through NoteRepository.search. */
interface TextIndexRepository {
    /**
     * Sources with no result yet, plus failed/unavailable ones last tried before [retryBefore] with attempts
     * left. Never sources of permanently deleted notes.
     */
    suspend fun pending(limit: Int, retryBefore: Instant): AppResult<List<TextSource>>

    /** Replaces everything stored for [source] with [pages] and records [status]. */
    suspend fun save(source: TextSource, pages: List<PageText>, status: IndexStatus): AppResult<Unit>

    /** Forgets all extracted text (feature turned off, or a rebuild). */
    suspend fun clear(): AppResult<Unit>

    /** How many sources are indexed / still waiting, for Settings. */
    suspend fun progress(): AppResult<Pair<Int, Int>>

    companion object {
        const val MAX_ATTEMPTS = 3
    }
}
