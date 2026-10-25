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

    val ink: Flow<InkSettings>

    /** How new notes start (Settings › Note defaults). */
    val noteDefaults: Flow<NoteDefaults>

    suspend fun updateNoteDefaults(change: (NoteDefaults) -> NoteDefaults): AppResult<Unit>

    /** "Find text in photos and PDFs" (FR-011): extract text on the device for search. On by default. */
    val documentText: Flow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode): AppResult<Unit>

    suspend fun setDynamicColor(enabled: Boolean): AppResult<Unit>

    /** Reads the appearance settings, applies [change] and saves the result (values are clamped to valid ranges). */
    suspend fun updateAppearance(change: (AppearanceSettings) -> AppearanceSettings): AppResult<Unit>

    /** Back to the default look (theme, accent, glass, corners, wallpaper, density). */
    suspend fun resetAppearance(): AppResult<Unit>

    suspend fun setLibrarySort(sort: NoteSort): AppResult<Unit>

    /** Reads the current ink settings, applies [change] and saves the result in one transaction. */
    suspend fun updateInk(change: (InkSettings) -> InkSettings): AppResult<Unit>

    suspend fun setDocumentText(enabled: Boolean): AppResult<Unit>

    /** Language last used for voice typing (BCP-47 tag, e.g. "en-IN"); null until chosen. */
    val voiceLanguage: Flow<String?>

    suspend fun setVoiceLanguage(tag: String): AppResult<Unit>
}
