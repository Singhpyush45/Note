package com.appname.notes.di

import android.content.Context
import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.domain.repository.TextIndexRepository
import com.appname.notes.core.settings.SettingsRepository
import com.appname.notes.feature.attachments.AttachmentFileStore
import com.appname.notes.feature.pdf.PdfAnnotationWriter
import com.appname.notes.feature.pdf.PdfFileStore
import com.appname.notes.feature.textindex.DocumentTextExtractor
import com.appname.notes.feature.textindex.DocumentTextSwitch
import com.appname.notes.feature.textindex.IndexingDocumentTextSwitch
import com.appname.notes.feature.textindex.MlKitTextRecognizer
import com.appname.notes.feature.textindex.TextIndexScheduler
import com.appname.notes.feature.textindex.TextIndexer
import com.appname.notes.feature.textindex.TextRecognizerEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TextIndexModule {

    @Provides
    @Singleton
    fun provideRecognizer(): TextRecognizerEngine = MlKitTextRecognizer()

    @Provides
    @Singleton
    fun provideExtractor(
        attachments: AttachmentFileStore,
        pdfs: PdfFileStore,
        ocr: TextRecognizerEngine,
        writer: PdfAnnotationWriter,
        logger: AppLogger,
    ) = DocumentTextExtractor(attachments, pdfs, ocr, writer, logger)

    @Provides
    @Singleton
    fun provideIndexer(
        repository: TextIndexRepository,
        extractor: DocumentTextExtractor,
        settings: SettingsRepository,
        clock: AppClock,
        dispatchers: DispatcherProvider,
        logger: AppLogger,
    ) = TextIndexer(repository, extractor::extract, settings, clock, dispatchers, logger, release = extractor::release)

    @Provides
    @Singleton
    fun provideScheduler(@ApplicationContext context: Context, logger: AppLogger) = TextIndexScheduler(context, logger)

    @Provides
    fun provideSwitch(repository: TextIndexRepository, scheduler: TextIndexScheduler): DocumentTextSwitch =
        IndexingDocumentTextSwitch(repository, scheduler)
}
