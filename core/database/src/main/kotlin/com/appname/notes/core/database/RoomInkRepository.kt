package com.appname.notes.core.database

import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.database.entity.InkItemEntity
import com.appname.notes.core.database.entity.InkPageEntity
import com.appname.notes.core.database.entity.NoteEntity
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.repository.InkPageContent
import com.appname.notes.core.domain.repository.InkPageInfo
import com.appname.notes.core.domain.repository.InkRepository
import com.appname.notes.core.ink.codec.DecodeResult
import com.appname.notes.core.ink.codec.InkCodec
import com.appname.notes.core.ink.edit.InkEdit
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.PageSizes
import com.appname.notes.core.ink.layout.PageArrangement
import com.appname.notes.core.ink.layout.PageMode
import com.appname.notes.core.ink.layout.ScrollAxis
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.database.entity.InkNoteSettingsEntity
import com.appname.notes.core.ink.template.TemplateJson
import com.appname.notes.core.ink.template.TemplateLayout

class RoomInkRepository(
    db: NotesDatabase,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val logger: AppLogger,
) : InkRepository {

    private val dao = db.inkDao()

    override suspend fun createInkNote(
        notebookId: NotebookId?,
        template: PageTemplate,
        layout: TemplateLayout?,
        arrangement: PageArrangement,
    ): AppResult<NoteId> =
        storageCall(logger, "Create handwritten note") {
            val now = clock.now().toEpochMilli()
            val noteId = ids.newId()
            dao.createNote(
                NoteEntity(
                    id = noteId,
                    notebookId = notebookId?.value,
                    title = "",
                    contentJson = "{}",
                    plainText = "",
                    preview = "",
                    noteType = NoteType.INK.name,
                    isPinned = false,
                    isFavourite = false,
                    isArchived = false,
                    createdAt = now,
                    updatedAt = now,
                    trashedAt = null,
                    deletedAt = null,
                    revision = 1,
                ),
                newPage(noteId, index = 0, template, layout, now),
                if (arrangement == PageArrangement.DEFAULT) null else settingsRow(noteId, arrangement, now),
            )
            NoteId(noteId)
        }

    private fun newPage(noteId: String, index: Int, template: PageTemplate, layout: TemplateLayout?, now: Long) = InkPageEntity(
        id = ids.newId(),
        noteId = noteId,
        pageIndex = index,
        template = (layout?.basicTemplate ?: template).code,
        width = layout?.width ?: PageSizes.A4_WIDTH,
        height = layout?.height ?: PageSizes.A4_HEIGHT,
        createdAt = now,
        updatedAt = now,
        layoutJson = layout?.let { TemplateJson.encode(it) },
    )

    private fun settingsRow(noteId: String, a: PageArrangement, now: Long) =
        InkNoteSettingsEntity(noteId, a.mode.name, a.axis.name, now)

    override suspend fun arrangement(noteId: NoteId): AppResult<PageArrangement> =
        storageCall(logger, "Load page settings") {
            dao.settings(noteId.value)?.let { row ->
                PageArrangement(
                    // Unknown values (from a newer version) fall back to the original layout.
                    mode = PageMode.entries.firstOrNull { it.name == row.pageMode } ?: PageMode.SPLIT,
                    axis = ScrollAxis.entries.firstOrNull { it.name == row.scrollAxis } ?: ScrollAxis.VERTICAL,
                )
            } ?: PageArrangement.DEFAULT
        }

    override suspend fun setArrangement(noteId: NoteId, arrangement: PageArrangement): AppResult<Unit> {
        val now = clock.now().toEpochMilli()
        return when (val r = storageCall(logger, "Save page settings") { dao.setSettings(settingsRow(noteId.value, arrangement, now)) }) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value) AppResult.Success(Unit) else AppResult.Failure(DomainError.NotFound("Note", noteId.value))
        }
    }

    override suspend fun resizePages(noteId: NoteId, width: Float, height: Float): AppResult<List<InkPageInfo>> {
        require(width > 0f && height > 0f) { "page size must be positive" }
        val now = clock.now().toEpochMilli()
        return savePages(noteId, "Resize pages", now) { page ->
            if (page.pdfPageIndex != null) return@savePages null // a PDF page keeps the document's size
            // Never smaller than the ink on it: changing the size must not hide anything.
            val ink = dao.items(page.id).mapNotNull { (InkCodec.decode(it.id, it.z, it.data) as? DecodeResult.Ok)?.item?.bounds() }
            val w = maxOf(width, ink.maxOfOrNull { it.right.toFloat() } ?: 0f)
            val h = maxOf(height, ink.maxOfOrNull { it.bottom.toFloat() } ?: 0f)
            val layout = TemplateJson.decodeOrNull(page.layoutJson)?.resized(w, h)
            page.copy(width = w, height = h, layoutJson = layout?.let { TemplateJson.encode(it) })
        }
    }

    override suspend fun setPageLayouts(noteId: NoteId, layouts: Map<String, TemplateLayout>): AppResult<List<InkPageInfo>> {
        val now = clock.now().toEpochMilli()
        return savePages(noteId, "Save page design", now) { page ->
            val layout = layouts[page.id] ?: return@savePages null
            if (page.pdfPageIndex != null) return@savePages null
            val fitted = layout.resized(page.width, page.height)
            page.copy(template = fitted.basicTemplate.code, layoutJson = TemplateJson.encode(fitted))
        }
    }

    /** Applies [change] to the note's pages (null = unchanged) and saves the changed ones in one transaction. */
    private suspend fun savePages(
        noteId: NoteId,
        what: String,
        now: Long,
        change: suspend (InkPageEntity) -> InkPageEntity?,
    ): AppResult<List<InkPageInfo>> {
        val r = storageCall(logger, what) {
            val changed = dao.pages(noteId.value).mapNotNull { change(it) }
            if (changed.isEmpty()) emptyList<InkPageEntity>() to true else changed to dao.updatePages(noteId.value, changed, now)
        }
        return when (r) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value.second) {
                AppResult.Success(r.value.first.map { it.toInfo() })
            } else {
                AppResult.Failure(DomainError.NotFound("Note", noteId.value))
            }
        }
    }

    override suspend fun loadPages(noteId: NoteId): AppResult<List<InkPageContent>> =
        storageCall(logger, "Load handwriting") { dao.pages(noteId.value).map { load(it) } }

    override suspend fun firstPage(noteId: NoteId): AppResult<InkPageContent?> =
        storageCall(logger, "Load thumbnail") { dao.pages(noteId.value).firstOrNull()?.let { load(it) } }

    private suspend fun load(page: InkPageEntity): InkPageContent {
        val items = ArrayList<InkItem>()
        var unreadable = 0
        for (row in dao.items(page.id)) {
            when (val r = InkCodec.decode(row.id, row.z, row.data)) {
                is DecodeResult.Ok -> items.add(r.item)
                is DecodeResult.NewerVersion -> unreadable++
                is DecodeResult.Corrupt -> {
                    unreadable++
                    logger.warn(TAG, "Unreadable ink item ${row.id.take(8)}…: ${r.reason}")
                }
            }
        }
        return InkPageContent(page.toInfo(), items, unreadable)
    }

    override suspend fun addPage(noteId: NoteId, index: Int, template: PageTemplate, layout: TemplateLayout?): AppResult<InkPageInfo> =
        storageCall(logger, "Add page") {
            val now = clock.now().toEpochMilli()
            val count = dao.pageCount(noteId.value)
            val page = newPage(noteId.value, index.coerceIn(0, count), template, layout, now)
            dao.addPage(page, now)
            page.toInfo()
        }

    override suspend fun deletePage(pageId: String): AppResult<Unit> {
        val page = dao.page(pageId) ?: return AppResult.Failure(DomainError.NotFound("Page", pageId))
        if (dao.pageCount(page.noteId) <= 1) {
            return AppResult.Failure(DomainError.Validation("page", "last page can't be deleted"))
        }
        return storageCall(logger, "Delete page") { dao.deletePage(page, clock.now().toEpochMilli()) }
    }

    override suspend fun setTemplate(pageId: String, template: PageTemplate): AppResult<Unit> =
        when (val r = storageCall(logger, "Set template") { dao.setTemplate(pageId, template.code, clock.now().toEpochMilli()) }) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value == 0) AppResult.Failure(DomainError.NotFound("Page", pageId)) else AppResult.Success(Unit)
        }

    override suspend fun commit(pageId: String, edit: InkEdit): AppResult<Unit> {
        if (edit.isEmpty) return AppResult.Success(Unit)
        val page = dao.page(pageId) ?: return AppResult.Failure(DomainError.NotFound("Page", pageId))
        val added = edit.added.map { InkItemEntity(it.id, pageId, it.z, InkCodec.encode(it)) }
        return when (val r = storageCall(logger, "Save handwriting") {
            dao.commit(page, edit.removed.map { it.id }, added, clock.now().toEpochMilli())
        }) {
            is AppResult.Failure -> r
            is AppResult.Success -> if (r.value) AppResult.Success(Unit) else AppResult.Failure(DomainError.NotFound("Note", page.noteId))
        }
    }

    private fun InkPageEntity.toInfo() = InkPageInfo(
        id = id,
        noteId = NoteId(noteId),
        index = pageIndex,
        template = PageTemplate.fromCode(template),
        width = width,
        height = height,
        pdfPageIndex = pdfPageIndex,
        // A damaged or unreadable design falls back to the plain background, never a crash.
        layout = TemplateJson.decodeOrNull(layoutJson),
    )

    private companion object {
        const val TAG = "InkRepository"
    }
}

/**
 * Copies the pages and ink of [sourceNoteId] with new ids, for duplicating a handwritten note. Stored
 * bytes are copied as they are, so even items this version can't read survive the copy.
 */
internal suspend fun copyInk(
    db: NotesDatabase,
    sourceNoteId: String,
    targetNoteId: String,
    ids: IdGenerator,
    now: Long,
): Pair<List<InkPageEntity>, List<InkItemEntity>> {
    val dao = db.inkDao()
    val pages = ArrayList<InkPageEntity>()
    val items = ArrayList<InkItemEntity>()
    for (page in dao.pages(sourceNoteId)) {
        val newPageId = ids.newId()
        pages.add(page.copy(id = newPageId, noteId = targetNoteId, createdAt = now, updatedAt = now))
        for (item in dao.items(page.id)) items.add(InkItemEntity(ids.newId(), newPageId, item.z, item.data.copyOf()))
    }
    return pages to items
}
