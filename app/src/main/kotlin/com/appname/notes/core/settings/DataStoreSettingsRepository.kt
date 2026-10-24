package com.appname.notes.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.ink.input.EditorTool
import com.appname.notes.core.ink.input.EraserMode
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
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

    override val ink: Flow<InkSettings> = prefs.map { it.toInk() }.distinctUntilChanged()

    override val documentText: Flow<Boolean> = prefs.map { it[Keys.DOCUMENT_TEXT] ?: true }.distinctUntilChanged()

    override suspend fun setDocumentText(enabled: Boolean): AppResult<Unit> = write { it[Keys.DOCUMENT_TEXT] = enabled }

    override suspend fun updateInk(change: (InkSettings) -> InkSettings): AppResult<Unit> = write { prefs ->
        val new = change(prefs.toInk())
        prefs[Keys.INK_SHAPES] = new.shapeCorrection
        prefs[Keys.INK_HOLD_MS] = new.holdDuration.millis
        prefs[Keys.INK_FINGER] = new.fingerMode.name
        prefs[Keys.INK_STYLUS_SEEN] = new.stylusSeen
        prefs[Keys.INK_TOOL] = new.lastTool.name
        prefs[Keys.INK_ERASER] = new.eraserMode.name
        for ((tool, brush) in new.brushes) {
            prefs[colorKey(tool)] = brush.colorArgb
            prefs[sizeKey(tool)] = brush.size
        }
    }

    private fun Preferences.toInk(): InkSettings {
        val defaults = InkSettings()
        return InkSettings(
            shapeCorrection = this[Keys.INK_SHAPES] ?: defaults.shapeCorrection,
            holdDuration = this[Keys.INK_HOLD_MS]?.let { HoldDuration.of(it) } ?: defaults.holdDuration,
            fingerMode = FingerMode.entries.firstOrNull { it.name == this[Keys.INK_FINGER] } ?: defaults.fingerMode,
            stylusSeen = this[Keys.INK_STYLUS_SEEN] ?: false,
            lastTool = EditorTool.entries.firstOrNull { it.name == this[Keys.INK_TOOL] } ?: defaults.lastTool,
            eraserMode = EraserMode.entries.firstOrNull { it.name == this[Keys.INK_ERASER] } ?: defaults.eraserMode,
            brushes = defaults.brushes.mapValues { (tool, default) ->
                val size = this[sizeKey(tool)]?.takeIf { it.isFinite() && it > 0f } ?: default.size
                InkBrush(tool, this[colorKey(tool)] ?: default.colorArgb, size)
            },
        )
    }

    private fun colorKey(tool: InkTool) = intPreferencesKey("ink_${tool.name.lowercase()}_color")
    private fun sizeKey(tool: InkTool) = floatPreferencesKey("ink_${tool.name.lowercase()}_size")

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
        val INK_SHAPES = booleanPreferencesKey("ink_shape_correction")
        val INK_HOLD_MS = longPreferencesKey("ink_hold_ms")
        val INK_FINGER = stringPreferencesKey("ink_finger_mode")
        val INK_STYLUS_SEEN = booleanPreferencesKey("ink_stylus_seen")
        val INK_TOOL = stringPreferencesKey("ink_last_tool")
        val INK_ERASER = stringPreferencesKey("ink_eraser_mode")
        val DOCUMENT_TEXT = booleanPreferencesKey("document_text_search")
    }

    private companion object {
        const val TAG = "Settings"
    }
}

/** Unknown or missing values (e.g. written by a future app version) fall back to SYSTEM. */
internal fun String?.toThemeMode(): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == this } ?: ThemeMode.SYSTEM

internal fun String?.toNoteSort(): NoteSort = NoteSort.entries.firstOrNull { it.name == this } ?: NoteSort.MODIFIED
