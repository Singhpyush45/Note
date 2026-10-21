package com.appname.notes.core.settings

import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.NoteSort
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes user preferences. ViewModels depend on this interface, never on DataStore directly
 * (SRS 10.2), so tests can use a fake.
 */
interface SettingsRepository {
    /** Emits the current settings immediately, then again whenever they change. */
    val appearance: Flow<AppearanceSettings>

    val library: Flow<LibrarySettings>

    suspend fun setThemeMode(mode: ThemeMode): AppResult<Unit>

    suspend fun setDynamicColor(enabled: Boolean): AppResult<Unit>

    suspend fun setLibrarySort(sort: NoteSort): AppResult<Unit>
}
