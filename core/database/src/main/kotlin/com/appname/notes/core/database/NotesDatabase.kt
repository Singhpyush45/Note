package com.appname.notes.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.appname.notes.core.database.dao.InkDao
import com.appname.notes.core.database.dao.NoteDao
import com.appname.notes.core.database.dao.NotebookDao
import com.appname.notes.core.database.dao.TagDao
import com.appname.notes.core.database.entity.InkItemEntity
import com.appname.notes.core.database.entity.InkPageEntity
import com.appname.notes.core.database.entity.NoteEntity
import com.appname.notes.core.database.entity.NoteFtsEntity
import com.appname.notes.core.database.entity.NoteTagCrossRef
import com.appname.notes.core.database.entity.NotebookEntity
import com.appname.notes.core.database.entity.TagEntity

/**
 * Schema history (every version's schema is committed under schemas/ and tested by MigrationTest):
 * - v1 (Phase 2): notebooks, notes (+ FTS index), tags, note_tags.
 * - v2 (Phase 3): ink_pages, ink_items. Only new tables, so Room generates the migration.
 */
@Database(
    entities = [
        NotebookEntity::class, NoteEntity::class, NoteFtsEntity::class, TagEntity::class, NoteTagCrossRef::class,
        InkPageEntity::class, InkItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun notebookDao(): NotebookDao
    abstract fun tagDao(): TagDao
    abstract fun inkDao(): InkDao

    /** Recreates the search index from the notes table (SRS 12.1). */
    fun rebuildSearchIndex() {
        openHelper.writableDatabase.execSQL("INSERT INTO notes_fts(notes_fts) VALUES('rebuild')")
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
