package com.appname.notes.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.appname.notes.BuildConfig
import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.id.UuidV7Generator
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.common.time.SystemClock
import com.appname.notes.core.logging.AndroidLogger
import com.appname.notes.core.settings.DataStoreSettingsRepository
import com.appname.notes.core.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/*
 * Hilt wiring: tells Hilt which concrete class to create when someone asks for an interface.
 * Think of it as a factory table built at compile time.
 */

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {

    @Provides
    @Singleton
    fun provideDispatchers(): DispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    @Provides
    @Singleton
    fun provideLogger(): AppLogger =
        AndroidLogger(minPriority = if (BuildConfig.DEBUG) Log.DEBUG else Log.WARN)

    @Provides
    @Singleton
    fun provideClock(): AppClock = SystemClock

    @Provides
    @Singleton
    fun provideIdGenerator(clock: AppClock): IdGenerator = UuidV7Generator(clock)

    /** Exactly one DataStore per file per process — DataStore enforces this. */
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        dispatchers: DispatcherProvider,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + dispatchers.io),
        produceFile = { context.preferencesDataStoreFile("settings") },
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
