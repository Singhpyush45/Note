package com.appname.notes.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.OnConflictStrategy
import com.appname.notes.core.database.entity.InkItemEntity
import com.appname.notes.core.database.entity.InkNoteSettingsEntity
import com.appname.notes.core.database.entity.InkPageEntity
import com.appname.notes.core.database.entity.NoteEntity
import com.appname.notes.core.database.entity.NoteTagCrossRef
import com.appname.notes.core.database.entity.PdfDocumentEntity

/**
 * Pages and ink items. Methods marked @Transaction run all-or-nothing (BEGIN … COMMIT), so a crash in the
 * middle never leaves half a change behind.
 */
@Dao
abstract class InkDao {

    @Query("SELECT * FROM ink_pages WHERE noteId = :noteId ORDER BY pageIndex, createdAt, id")
    abstract suspend fun pages(noteId: String): List<InkPageEntity>

    @Query("SELECT * FROM ink_pages WHERE id = :id")
    abstract suspend fun page(id: String): InkPageEntity?

    @Query("SELECT * FROM ink_items WHERE pageId = :pageId ORDER BY z, id")
    abstract suspend fun items(pageId: String): List<InkItemEntity>

    @Query("SELECT COUNT(*) FROM ink_items WHERE pageId IN (SELECT id FROM ink_pages WHERE noteId = :noteId)")
    abstract suspend fun itemCount(noteId: String): Int

    @Query("SELECT COUNT(*) FROM ink_pages WHERE noteId = :noteId")
    abstract suspend fun pageCount(noteId: String): Int

    @Insert
    protected abstract suspend fun insertNote(note: NoteEntity)

    @Insert
    protected abstract suspend fun insertTagLinks(links: List<NoteTagCrossRef>)

    @Insert
    protected abstract suspend fun insertPage(page: InkPageEntity)

    @Insert
    protected abstract suspend fun insertPages(pages: List<InkPageEntity>)

    @Insert
    protected abstract suspend fun insertItems(items: List<InkItemEntity>)

    @Query("DELETE FROM ink_items WHERE pageId = :pageId AND id IN (:ids)")
    protected abstract suspend fun deleteItems(pageId: String, ids: List<String>): Int

    @Query("DELETE FROM ink_items WHERE pageId = :pageId")
    protected abstract suspend fun deleteItemsOfPage(pageId: String)

    @Query("DELETE FROM ink_pages WHERE id = :pageId")
    protected abstract suspend fun deletePageRow(pageId: String)

    @Query("UPDATE ink_pages SET pageIndex = pageIndex + 1 WHERE noteId = :noteId AND pageIndex >= :from")
    protected abstract suspend fun shiftPagesDown(noteId: String, from: Int)

    @Query("UPDATE ink_pages SET pageIndex = pageIndex - 1 WHERE noteId = :noteId AND pageIndex > :from")
    protected abstract suspend fun shiftPagesUp(noteId: String, from: Int)

    /** A plain background replaces any template design on the page. */
    @Query("UPDATE ink_pages SET template = :template, layoutJson = NULL, updatedAt = :now WHERE id = :pageId")
    abstract suspend fun setTemplate(pageId: String, template: Int, now: Long): Int

    @Query("UPDATE ink_pages SET updatedAt = :now WHERE id = :pageId")
    protected abstract suspend fun touchPage(pageId: String, now: Long)

    /**
     * Marks the note modified and strictly advances updatedAt so note list observers and thumbnail caches
     * always invalidate even when commits happen within the same millisecond as note creation.
     * Returns 0 if the note is gone (deleted).
     */
    @Query(
        """
        UPDATE notes
        SET updatedAt = CASE WHEN :now > updatedAt THEN :now ELSE updatedAt + 1 END,
            revision = revision + 1
        WHERE id = :noteId AND deletedAt IS NULL
        """,
    )
    protected abstract suspend fun touchNote(noteId: String, now: Long): Int

    @Insert
    protected abstract suspend fun insertPdfDocument(document: PdfDocumentEntity)

    @Query("SELECT * FROM pdf_documents WHERE noteId = :noteId")
    abstract suspend fun pdfDocument(noteId: String): PdfDocumentEntity?

    @Query("SELECT DISTINCT fileName FROM pdf_documents")
    abstract suspend fun pdfFileNames(): List<String>

    @Transaction
    open suspend fun createNote(note: NoteEntity, firstPage: InkPageEntity, settings: InkNoteSettingsEntity? = null) {
        insertNote(note)
        insertPage(firstPage)
        settings?.let { upsertSettings(it) }
    }

    @Query("SELECT * FROM ink_note_settings WHERE noteId = :noteId")
    abstract suspend fun settings(noteId: String): InkNoteSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertSettings(settings: InkNoteSettingsEntity)

    /** Saves the arrangement; false (nothing changed) when the note is gone. */
    @Transaction
    open suspend fun setSettings(settings: InkNoteSettingsEntity): Boolean {
        if (touchNote(settings.noteId, settings.updatedAt) == 0) return false
        upsertSettings(settings)
        return true
    }

    @Query("UPDATE ink_pages SET template = :template, layoutJson = :layoutJson, width = :width, height = :height, updatedAt = :now WHERE id = :pageId")
    protected abstract suspend fun updatePage(pageId: String, template: Int, layoutJson: String?, width: Float, height: Float, now: Long): Int

    /** Several pages' size and design at once, all or nothing; false when the note is gone. */
    @Transaction
    open suspend fun updatePages(noteId: String, pages: List<InkPageEntity>, now: Long): Boolean {
        if (touchNote(noteId, now) == 0) return false
        for (p in pages) updatePage(p.id, p.template, p.layoutJson, p.width, p.height, now)
        return true
    }

    /** A PDF note with its document row and one page per PDF page, all or nothing. */
    @Transaction
    open suspend fun createPdfNote(note: NoteEntity, document: PdfDocumentEntity, pages: List<InkPageEntity>) {
        insertNote(note)
        insertPdfDocument(document)
        insertPages(pages)
    }

    /** Copy of a note with its tags, pages and ink (all new ids prepared by the caller). */
    @Transaction
    open suspend fun insertCopy(
        note: NoteEntity,
        tagIds: List<String>,
        pages: List<InkPageEntity>,
        items: List<InkItemEntity>,
        pdfDocument: PdfDocumentEntity? = null,
        settings: InkNoteSettingsEntity? = null,
    ) {
        insertNote(note)
        settings?.let { upsertSettings(it) }
        insertTagLinks(tagIds.map { NoteTagCrossRef(note.id, it) })
        pdfDocument?.let { insertPdfDocument(it) }
        insertPages(pages)
        if (items.isNotEmpty()) insertItems(items)
    }

    /**
     * One ink change. Returns false (and changes nothing) when the note no longer exists, e.g. it was
     * deleted on another screen.
     */
    @Transaction
    open suspend fun commit(page: InkPageEntity, removedIds: List<String>, added: List<InkItemEntity>, now: Long): Boolean {
        if (touchNote(page.noteId, now) == 0) return false
        // SQLite limits bound parameters per statement; erase/lasso edits can touch many items.
        removedIds.chunked(500).forEach { deleteItems(page.id, it) }
        if (added.isNotEmpty()) insertItems(added)
        touchPage(page.id, now)
        return true
    }

    @Transaction
    open suspend fun addPage(page: InkPageEntity, now: Long) {
        shiftPagesDown(page.noteId, page.pageIndex)
        insertPage(page)
        touchNote(page.noteId, now)
    }

    @Transaction
    open suspend fun deletePage(page: InkPageEntity, now: Long) {
        deleteItemsOfPage(page.id)
        deletePageRow(page.id)
        shiftPagesUp(page.noteId, page.pageIndex)
        touchNote(page.noteId, now)
    }
}
