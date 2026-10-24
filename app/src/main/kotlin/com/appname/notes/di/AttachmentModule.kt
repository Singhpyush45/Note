package com.appname.notes.di

import android.content.Context
import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.domain.repository.AttachmentRepository
import com.appname.notes.feature.attachments.AttachmentFileStore
import com.appname.notes.feature.attachments.AttachmentIntents
import com.appname.notes.feature.attachments.AttachmentPreviews
import com.appname.notes.feature.attachments.Attacher
import com.appname.notes.feature.attachments.StorageCleanup
import com.appname.notes.feature.pdf.PdfImporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AttachmentModule {

    @Provides
    @Singleton
    fun provideStore(@ApplicationContext context: Context, logger: AppLogger) = AttachmentFileStore(context, logger)

    @Provides
    @Singleton
    fun providePreviews(store: AttachmentFileStore, dispatchers: DispatcherProvider) = AttachmentPreviews(store, dispatchers)

    @Provides
    @Singleton
    fun provideAttacher(store: AttachmentFileStore, repository: AttachmentRepository, dispatchers: DispatcherProvider) =
        Attacher(store, repository, dispatchers)

    @Provides
    @Singleton
    fun provideIntents(@ApplicationContext context: Context, store: AttachmentFileStore) = AttachmentIntents(context, store)

    @Provides
    @Singleton
    fun provideCleanup(pdf: PdfImporter, attacher: Attacher) = StorageCleanup(pdf, attacher)
}
