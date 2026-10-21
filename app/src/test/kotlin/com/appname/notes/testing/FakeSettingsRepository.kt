package com.appname.notes.testing

import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.settings.AppearanceSettings
import com.appname.notes.core.settings.LibrarySettings
import com.appname.notes.core.settings.SettingsRepository
import com.appname.notes.core.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory settings; set [failWrites] to simulate a full or broken disk. */
class FakeSettingsRepository(initial: AppearanceSettings = AppearanceSettings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)
    var failWrites = false

    override val appearance: Flow<AppearanceSettings> = state

    private val librarySettings = MutableStateFlow(LibrarySettings())
    override val library: Flow<LibrarySettings> = librarySettings

    override suspend fun setLibrarySort(sort: NoteSort): AppResult<Unit> =
        write { librarySettings.value = librarySettings.value.copy(sort = sort) }

    override suspend fun setThemeMode(mode: ThemeMode): AppResult<Unit> =
        write { state.value = state.value.copy(themeMode = mode) }

    override suspend fun setDynamicColor(enabled: Boolean): AppResult<Unit> =
        write { state.value = state.value.copy(dynamicColor = enabled) }

    private fun write(change: () -> Unit): AppResult<Unit> {
        if (failWrites) return AppResult.Failure(DomainError.Storage())
        change()
        return AppResult.Success(Unit)
    }
}
