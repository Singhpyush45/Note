package com.appname.notes.di

import android.content.Context
import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.database.NotesDatabase
import com.appname.notes.core.database.RoomAttachmentRepository
import com.appname.notes.core.database.RoomInkRepository
import com.appname.notes.core.database.RoomNoteRepository
import com.appname.notes.core.database.RoomPdfRepository
import com.appname.notes.core.database.RoomNotebookRepository
import com.appname.notes.core.database.RoomTagRepository
import com.appname.notes.core.database.RoomTextIndexRepository
import com.appname.notes.core.database.RoomReminderRepository
import com.appname.notes.core.domain.repository.ReminderRepository
import com.appname.notes.core.domain.repository.AttachmentRepository
import com.appname.notes.core.domain.repository.InkRepository
import com.appname.notes.core.domain.repository.NoteRepository
import com.appname.notes.core.domain.repository.NotebookRepository
import com.appname.notes.core.domain.repository.PdfRepository
import com.appname.notes.core.domain.repository.TagRepository
import com.appname.notes.core.domain.repository.TextIndexRepository
import com.appname.notes.feature.settings.SearchIndexRebuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A scope that lives as long as the app process. Used for work that must finish even after the screen
 * that started it is gone — e.g. the last autosave when the user leaves the editor (FR-002, FR-008).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NotesDatabase = NotesDatabase.create(context)

    @Provides
    @Singleton
    fun provideNoteRepository(db: NotesDatabase, clock: AppClock, ids: IdGenerator, logger: AppLogger): NoteRepository =
        RoomNoteRepository(db, clock, ids, logger)

    @Provides
    fun provideSearchIndexRebuilder(notes: NoteRepository): SearchIndexRebuilder =
        SearchIndexRebuilder { notes.rebuildSearchIndex() }

    @Provides
    @Singleton
    fun provideNotebookRepository(db: NotesDatabase, clock: AppClock, ids: IdGenerator, logger: AppLogger): NotebookRepository =
        RoomNotebookRepository(db, clock, ids, logger)

    @Provides
    @Singleton
    fun provideTagRepository(db: NotesDatabase, clock: AppClock, ids: IdGenerator, logger: AppLogger): TagRepository =
        RoomTagRepository(db, clock, ids, logger)

    @Provides
    @Singleton
    fun provideInkRepository(db: NotesDatabase, clock: AppClock, ids: IdGenerator, logger: AppLogger): InkRepository =
        RoomInkRepository(db, clock, ids, logger)

    @Provides
    @Singleton
    fun providePdfRepository(db: NotesDatabase, clock: AppClock, ids: IdGenerator, logger: AppLogger): PdfRepository =
        RoomPdfRepository(db, clock, ids, logger)

    @Provides
    @Singleton
    fun provideAttachmentRepository(db: NotesDatabase, clock: AppClock, ids: IdGenerator, logger: AppLogger): AttachmentRepository =
        RoomAttachmentRepository(db, clock, ids, logger)

    @Provides
    @Singleton
    fun provideTextIndexRepository(db: NotesDatabase, clock: AppClock, logger: AppLogger): TextIndexRepository =
        RoomTextIndexRepository(db, clock, logger)

    @Provides
    @Singleton
    fun provideReminderRepository(db: NotesDatabase, clock: AppClock, ids: IdGenerator, logger: AppLogger): ReminderRepository =
        RoomReminderRepository(db, clock, ids, logger)

    @Provides
    @Singleton
    fun provideTemplateRepository(db: NotesDatabase, clock: AppClock, ids: IdGenerator, logger: AppLogger): com.appname.notes.core.domain.repository.TemplateRepository =
        com.appname.notes.core.database.RoomTemplateRepository(db, clock, ids, logger)
}
