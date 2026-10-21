package com.appname.notes.di

import android.content.Context
import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.domain.repository.PdfRepository
import com.appname.notes.feature.pdf.PdfAnnotationWriter
import com.appname.notes.feature.pdf.PdfFileStore
import com.appname.notes.feature.pdf.PdfImporter
import com.appname.notes.feature.pdf.PdfThumbnails
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PdfModule {

    @Provides
    @Singleton
    fun provideAnnotationWriter(@ApplicationContext context: Context) = PdfAnnotationWriter(context)

    @Provides
    @Singleton
    fun provideFileStore(@ApplicationContext context: Context, writer: PdfAnnotationWriter, logger: AppLogger) =
        PdfFileStore(context, writer, logger)

    @Provides
    @Singleton
    fun provideImporter(store: PdfFileStore, pdf: PdfRepository, dispatchers: DispatcherProvider) =
        PdfImporter(store, pdf, dispatchers)

    @Provides
    @Singleton
    fun provideThumbnails(pdf: PdfRepository, store: PdfFileStore, dispatchers: DispatcherProvider, logger: AppLogger) =
        PdfThumbnails(pdf, store, dispatchers, logger)
}
