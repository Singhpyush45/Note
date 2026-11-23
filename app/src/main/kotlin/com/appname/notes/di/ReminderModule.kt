package com.appname.notes.di

import android.content.Context
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.domain.repository.ReminderRepository
import com.appname.notes.feature.reminders.ReminderNotifier
import com.appname.notes.feature.reminders.ReminderScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object ReminderModule {

    @Provides
    @Singleton
    fun provideReminderNotifier(@ApplicationContext context: Context) = ReminderNotifier(context)

    @Provides
    @Singleton
    fun provideReminderScheduler(
        @ApplicationContext context: Context,
        reminders: ReminderRepository,
        notifier: ReminderNotifier,
        clock: AppClock,
        @ApplicationScope appScope: CoroutineScope,
        logger: AppLogger,
    ) = ReminderScheduler(context, reminders, notifier, clock, appScope, logger)
}
