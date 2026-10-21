package com.appname.notes.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.domain.model.NoteSort
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [SettingsRepository] backed by Jetpack DataStore (an async, transactional key-value file).
 * DataStore does its file I/O off the main thread by itself.
 */
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val logger: AppLogger,
) : SettingsRepository {

    private val prefs: Flow<Preferences> = dataStore.data
        .catch { error ->
            // A read failure must not crash the app: fall back to defaults and log it.
            if (error is IOException) {
                logger.warn(TAG, "Reading settings failed; using defaults", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    override val appearance: Flow<AppearanceSettings> = prefs
        .map { prefs ->
            AppearanceSettings(
                themeMode = prefs[Keys.THEME_MODE].toThemeMode(),
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            )
        }
        .distinctUntilChanged()

    override val library: Flow<LibrarySettings> = prefs
        .map { prefs -> LibrarySettings(sort = prefs[Keys.LIBRARY_SORT].toNoteSort()) }
        .distinctUntilChanged()

    override suspend fun setLibrarySort(sort: NoteSort): AppResult<Unit> =
        write { it[Keys.LIBRARY_SORT] = sort.name }

    override suspend fun setThemeMode(mode: ThemeMode): AppResult<Unit> =
        write { it[Keys.THEME_MODE] = mode.name }

    override suspend fun setDynamicColor(enabled: Boolean): AppResult<Unit> =
        write { it[Keys.DYNAMIC_COLOR] = enabled }

    private suspend fun write(change: (MutablePreferences) -> Unit): AppResult<Unit> = try {
        dataStore.edit { change(it) }
        AppResult.Success(Unit)
    } catch (error: IOException) {
        logger.warn(TAG, "Saving settings failed", error)
        AppResult.Failure(DomainError.Storage())
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
    }

    private companion object {
        const val TAG = "Settings"
    }
}

/** Unknown or missing values (e.g. written by a future app version) fall back to SYSTEM. */
internal fun String?.toThemeMode(): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == this } ?: ThemeMode.SYSTEM

internal fun String?.toNoteSort(): NoteSort = NoteSort.entries.firstOrNull { it.name == this } ?: NoteSort.MODIFIED
