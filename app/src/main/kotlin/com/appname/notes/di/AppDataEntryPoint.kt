package com.appname.notes.di

import com.appname.notes.core.domain.repository.InkRepository
import com.appname.notes.core.domain.repository.NoteRepository
import com.appname.notes.core.domain.repository.NotebookRepository
import com.appname.notes.core.domain.repository.TagRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets code that Hilt doesn't construct (tests that seed data, future widgets/shortcuts) reach the
 * repositories: `EntryPointAccessors.fromApplication(context, AppDataEntryPoint::class.java)`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppDataEntryPoint {
    fun notes(): NoteRepository
    fun notebooks(): NotebookRepository
    fun tags(): TagRepository
    fun ink(): InkRepository
}
