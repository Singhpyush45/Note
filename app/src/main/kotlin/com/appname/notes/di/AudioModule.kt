package com.appname.notes.di

import android.content.Context
import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.feature.attachments.Attacher
import com.appname.notes.feature.audio.RecordingController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    @Provides
    @Singleton
    fun provideRecordingController(
        @ApplicationContext context: Context,
        attacher: Attacher,
        ids: IdGenerator,
        @ApplicationScope appScope: CoroutineScope,
        logger: AppLogger,
    ) = RecordingController(context, attacher, ids, appScope, logger)
}
