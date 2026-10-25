package com.appname.notes.testing

import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.settings.AppearanceSettings
import com.appname.notes.core.settings.InkSettings
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

    fun current(): AppearanceSettings = state.value

    private val librarySettings = MutableStateFlow(LibrarySettings())
    override val library: Flow<LibrarySettings> = librarySettings

    val inkSettings = MutableStateFlow(InkSettings())
    override val ink: Flow<InkSettings> = inkSettings

    val documentTextEnabled = MutableStateFlow(true)
    override val documentText: Flow<Boolean> = documentTextEnabled

    override suspend fun setDocumentText(enabled: Boolean): AppResult<Unit> = write { documentTextEnabled.value = enabled }

    val voice = MutableStateFlow<String?>(null)
    override val voiceLanguage: Flow<String?> = voice

    override suspend fun setVoiceLanguage(tag: String): AppResult<Unit> = write { voice.value = tag }

    override suspend fun updateInk(change: (InkSettings) -> InkSettings): AppResult<Unit> =
        write { inkSettings.value = change(inkSettings.value) }

    val defaults = MutableStateFlow(com.appname.notes.core.settings.NoteDefaults())
    override val noteDefaults: Flow<com.appname.notes.core.settings.NoteDefaults> = defaults

    override suspend fun updateNoteDefaults(
        change: (com.appname.notes.core.settings.NoteDefaults) -> com.appname.notes.core.settings.NoteDefaults,
    ): AppResult<Unit> = write { defaults.value = change(defaults.value) }

    override suspend fun setLibrarySort(sort: NoteSort): AppResult<Unit> =
        write { librarySettings.value = librarySettings.value.copy(sort = sort) }

    override suspend fun setThemeMode(mode: ThemeMode): AppResult<Unit> =
        write { state.value = state.value.copy(themeMode = mode) }

    override suspend fun setDynamicColor(enabled: Boolean): AppResult<Unit> =
        write { state.value = state.value.copy(dynamicColor = enabled) }

    override suspend fun updateAppearance(change: (AppearanceSettings) -> AppearanceSettings): AppResult<Unit> =
        write { state.value = change(state.value).clamped() }

    override suspend fun resetAppearance(): AppResult<Unit> = write { state.value = AppearanceSettings() }

    private fun write(change: () -> Unit): AppResult<Unit> {
        if (failWrites) return AppResult.Failure(DomainError.Storage())
        change()
        return AppResult.Success(Unit)
    }
}
