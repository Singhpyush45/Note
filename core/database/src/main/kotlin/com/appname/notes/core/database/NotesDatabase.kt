package com.appname.notes.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.appname.notes.core.database.dao.AttachmentDao
import com.appname.notes.core.database.dao.InkDao
import com.appname.notes.core.database.dao.NoteDao
import com.appname.notes.core.database.dao.NotebookDao
import com.appname.notes.core.database.dao.TagDao
import com.appname.notes.core.database.dao.TextIndexDao
import com.appname.notes.core.database.dao.ReminderDao
import com.appname.notes.core.database.dao.TemplateDao
import com.appname.notes.core.database.entity.CustomTemplateEntity
import com.appname.notes.core.database.entity.ReminderEntity
import com.appname.notes.core.database.entity.AttachmentEntity
import com.appname.notes.core.database.entity.InkItemEntity
import com.appname.notes.core.database.entity.InkPageEntity
import com.appname.notes.core.database.entity.NoteEntity
import com.appname.notes.core.database.entity.NoteFtsEntity
import com.appname.notes.core.database.entity.NoteTagCrossRef
import com.appname.notes.core.database.entity.NotebookEntity
import com.appname.notes.core.database.entity.PdfDocumentEntity
import com.appname.notes.core.database.entity.TagEntity
import com.appname.notes.core.database.entity.ExtractedTextEntity
import com.appname.notes.core.database.entity.ExtractedTextFtsEntity
import com.appname.notes.core.database.entity.TextIndexJobEntity

/**
 * Schema history (every version's schema is committed under schemas/ and tested by MigrationTest):
 * - v1 (Phase 2): notebooks, notes (+ FTS index), tags, note_tags.
 * - v2 (Phase 3): ink_pages, ink_items. Only new tables, so Room generates the migration.
 * - v3 (Phase 4): pdf_documents; ink_pages.pdfPageIndex (nullable). Generated migration too.
 * - v4 (Phase 5): attachments. New table only; generated migration.
 * - v5 (Phase 5): extracted_text (+ FTS index), text_index_jobs. New tables only; generated migration.
 * - v6 (Phase 5): attachments.durationMs (nullable) for recordings. Generated migration.
 * - v7 (Phase 5): reminders. New table only; generated migration.
 * - v8 (Phase 6): custom_templates; ink_pages.layoutJson (nullable). Generated migration.
 */
@Database(
    entities = [
        NotebookEntity::class, NoteEntity::class, NoteFtsEntity::class, TagEntity::class, NoteTagCrossRef::class,
        InkPageEntity::class, InkItemEntity::class, PdfDocumentEntity::class, AttachmentEntity::class,
        ExtractedTextEntity::class, ExtractedTextFtsEntity::class, TextIndexJobEntity::class, ReminderEntity::class, CustomTemplateEntity::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8)],
)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun notebookDao(): NotebookDao
    abstract fun tagDao(): TagDao
    abstract fun inkDao(): InkDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun textIndexDao(): TextIndexDao
    abstract fun reminderDao(): ReminderDao
    abstract fun templateDao(): TemplateDao

    /** Recreates the search index from the notes table (SRS 12.1). */
    fun rebuildSearchIndex() {
        openHelper.writableDatabase.execSQL("INSERT INTO notes_fts(notes_fts) VALUES('rebuild')")
        openHelper.writableDatabase.execSQL("INSERT INTO extracted_text_fts(extracted_text_fts) VALUES('rebuild')")
    }

    companion object {
        const val FILE_NAME = "notes.db"

        /**
         * Production database. There is deliberately no destructive-migration fallback (SRS 9.2):
         * a missing migration must fail loudly in testing, never wipe user notes.
         */
        fun create(context: Context): NotesDatabase =
            Room.databaseBuilder(context, NotesDatabase::class.java, FILE_NAME).build()

        fun inMemory(context: Context): NotesDatabase =
            Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
    }
}
