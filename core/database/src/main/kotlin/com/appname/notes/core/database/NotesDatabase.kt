package com.appname.notes.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.appname.notes.core.database.dao.NoteDao
import com.appname.notes.core.database.dao.NotebookDao
import com.appname.notes.core.database.dao.TagDao
import com.appname.notes.core.database.entity.NoteEntity
import com.appname.notes.core.database.entity.NoteFtsEntity
import com.appname.notes.core.database.entity.NoteTagCrossRef
import com.appname.notes.core.database.entity.NotebookEntity
import com.appname.notes.core.database.entity.TagEntity

@Database(
    entities = [NotebookEntity::class, NoteEntity::class, NoteFtsEntity::class, TagEntity::class, NoteTagCrossRef::class],
    version = 1,
    exportSchema = true,
)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun notebookDao(): NotebookDao
    abstract fun tagDao(): TagDao

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
