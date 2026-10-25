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
import com.appname.notes.core.designsystem.theme.Accent
import com.appname.notes.core.designsystem.theme.UiDensity
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.ink.input.EditorTool
import com.appname.notes.core.ink.input.EraserMode
import com.appname.notes.core.ink.model.BrushStyle
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
            prefs.toAppearance()
        }
        .distinctUntilChanged()

    override val library: Flow<LibrarySettings> = prefs
        .map { prefs -> LibrarySettings(sort = prefs[Keys.LIBRARY_SORT].toNoteSort()) }
        .distinctUntilChanged()

    override val ink: Flow<InkSettings> = prefs.map { it.toInk() }.distinctUntilChanged()

    override val noteDefaults: Flow<NoteDefaults> = prefs.map { it.toNoteDefaults() }.distinctUntilChanged()

    override suspend fun updateNoteDefaults(change: (NoteDefaults) -> NoteDefaults): AppResult<Unit> = write { prefs ->
        val new = change(prefs.toNoteDefaults())
        prefs[Keys.BLANK_NOTE] = new.blankNote.name
        prefs[Keys.PAGE_STYLE] = new.pageStyle
        prefs[Keys.PAGE_FORMAT] = new.pageFormat.name
        prefs[Keys.PAGE_MODE] = new.pageMode.name
        prefs[Keys.SCROLL_AXIS] = new.scrollAxis.name
        prefs[Keys.PAPER_COLOR] = new.paperColor
        prefs[Keys.DARK_PAPER] = new.darkPaper
    }

    /** Unknown values (e.g. from a newer version) fall back to the defaults. */
    private fun Preferences.toNoteDefaults(): NoteDefaults {
        val d = NoteDefaults()
        return NoteDefaults(
            blankNote = BlankNoteKind.entries.firstOrNull { it.name == this[Keys.BLANK_NOTE] } ?: d.blankNote,
            pageStyle = this[Keys.PAGE_STYLE]?.takeIf { it in NoteDefaults.STYLES } ?: d.pageStyle,
            pageFormat = com.appname.notes.core.ink.template.PageFormat.entries.firstOrNull { it.name == this[Keys.PAGE_FORMAT] } ?: d.pageFormat,
            pageMode = com.appname.notes.core.ink.layout.PageMode.entries.firstOrNull { it.name == this[Keys.PAGE_MODE] } ?: d.pageMode,
            scrollAxis = com.appname.notes.core.ink.layout.ScrollAxis.entries.firstOrNull { it.name == this[Keys.SCROLL_AXIS] } ?: d.scrollAxis,
            paperColor = this[Keys.PAPER_COLOR] ?: d.paperColor,
            darkPaper = this[Keys.DARK_PAPER] ?: d.darkPaper,
        )
    }

    override val documentText: Flow<Boolean> = prefs.map { it[Keys.DOCUMENT_TEXT] ?: true }.distinctUntilChanged()

    override suspend fun setDocumentText(enabled: Boolean): AppResult<Unit> = write { it[Keys.DOCUMENT_TEXT] = enabled }

    override val voiceLanguage: Flow<String?> = prefs.map { it[Keys.VOICE_LANGUAGE] }.distinctUntilChanged()

    override suspend fun setVoiceLanguage(tag: String): AppResult<Unit> = write { it[Keys.VOICE_LANGUAGE] = tag }

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
            prefs[styleKey(tool)] = brush.effectiveStyle.code
            prefs[pressureKey(tool)] = brush.pressure
        }
        prefs[Keys.RECENT_BRUSHES] = Recents.encodeBrushes(new.recentBrushes)
        prefs[Keys.RECENT_COLORS] = Recents.encodeColors(new.recentColors)
        prefs[Keys.SAVED_COLORS] = Recents.encodeColors(new.savedColors)
        prefs[Keys.LASER_STYLE] = new.laser.style.name
        prefs[Keys.LASER_RAINBOW] = new.laser.rainbow
        prefs[Keys.LASER_COLOR] = new.laser.colorArgb
        prefs[Keys.LASER_WIDTH] = new.laser.widthDp
        prefs[Keys.SHAPE_KIND] = new.shape.kind.code
        prefs[Keys.SHAPE_COLOR] = new.shape.colorArgb
        prefs[Keys.SHAPE_SIZE] = new.shape.size
        prefs[Keys.SHAPE_FILLED] = new.shape.fillArgb != null
        new.shape.fillArgb?.let { prefs[Keys.SHAPE_FILL] = it }
        prefs[Keys.SHAPE_GRADIENT] = new.shape.gradient
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
            recentBrushes = Recents.decodeBrushes(this[Keys.RECENT_BRUSHES]),
            recentColors = Recents.decodeColors(this[Keys.RECENT_COLORS], Recents.MAX_COLORS),
            savedColors = Recents.decodeColors(this[Keys.SAVED_COLORS], Recents.MAX_SAVED),
            laser = LaserSettings(
                style = LaserStyle.entries.firstOrNull { it.name == this[Keys.LASER_STYLE] } ?: LaserStyle.SOLID,
                rainbow = this[Keys.LASER_RAINBOW] ?: false,
                colorArgb = this[Keys.LASER_COLOR] ?: LaserSettings().colorArgb,
                widthDp = this[Keys.LASER_WIDTH]?.takeIf { it.isFinite() && it in 1f..40f } ?: LaserSettings().widthDp,
            ),
            shape = ShapeSettings(
                kind = this[Keys.SHAPE_KIND]?.let { com.appname.notes.core.ink.model.ShapeKind.fromCode(it) } ?: ShapeSettings().kind,
                colorArgb = this[Keys.SHAPE_COLOR] ?: ShapeSettings().colorArgb,
                size = this[Keys.SHAPE_SIZE]?.takeIf { it.isFinite() && it in 0.5f..40f } ?: ShapeSettings().size,
                fillArgb = if (this[Keys.SHAPE_FILLED] == true) this[Keys.SHAPE_FILL] else null,
                gradient = this[Keys.SHAPE_GRADIENT] ?: false,
            ),
            brushes = defaults.brushes.mapValues { (tool, default) ->
                val size = this[sizeKey(tool)]?.takeIf { it.isFinite() && it > 0f } ?: default.size
                // An unknown or mismatched style (e.g. from a newer version) falls back to the tool's default.
                val style = this[styleKey(tool)]?.let { BrushStyle.fromCode(it) }?.takeIf { it.tool == tool } ?: default.style
                InkBrush(tool, this[colorKey(tool)] ?: default.colorArgb, size, style, this[pressureKey(tool)] ?: true).normalized()
            },
        )
    }

    private fun colorKey(tool: InkTool) = intPreferencesKey("ink_${tool.name.lowercase()}_color")
    private fun styleKey(tool: InkTool) = intPreferencesKey("ink_${tool.name.lowercase()}_style")
    private fun pressureKey(tool: InkTool) = booleanPreferencesKey("ink_${tool.name.lowercase()}_pressure")
    private fun sizeKey(tool: InkTool) = floatPreferencesKey("ink_${tool.name.lowercase()}_size")

    override suspend fun setLibrarySort(sort: NoteSort): AppResult<Unit> =
        write { it[Keys.LIBRARY_SORT] = sort.name }

    override suspend fun setThemeMode(mode: ThemeMode): AppResult<Unit> =
        write { it[Keys.THEME_MODE] = mode.name }

    override suspend fun setDynamicColor(enabled: Boolean): AppResult<Unit> =
        write { it[Keys.DYNAMIC_COLOR] = enabled }

    override suspend fun updateAppearance(change: (AppearanceSettings) -> AppearanceSettings): AppResult<Unit> = write { prefs ->
        val new = change(prefs.toAppearance()).clamped()
        prefs[Keys.THEME_MODE] = new.themeMode.name
        prefs[Keys.DYNAMIC_COLOR] = new.dynamicColor
        prefs[Keys.ACCENT] = new.accent?.name ?: CUSTOM_ACCENT
        prefs[Keys.CUSTOM_HUE] = new.customHue
        prefs[Keys.GLASS] = new.glassIntensity
        prefs[Keys.BLUR] = new.blurIntensity
        prefs[Keys.CORNER] = new.cornerRadius
        prefs[Keys.WALLPAPER] = new.wallpaper.stored
        prefs[Keys.DENSITY] = new.density.name
    }

    override suspend fun resetAppearance(): AppResult<Unit> = write { prefs ->
        listOf(
            Keys.THEME_MODE, Keys.DYNAMIC_COLOR, Keys.ACCENT, Keys.CUSTOM_HUE, Keys.GLASS, Keys.BLUR, Keys.CORNER,
            Keys.WALLPAPER, Keys.DENSITY,
        ).forEach { prefs.remove(it) }
    }

    private fun Preferences.toAppearance(): AppearanceSettings {
        val d = AppearanceSettings()
        val accentName = this[Keys.ACCENT]
        return AppearanceSettings(
            themeMode = this[Keys.THEME_MODE].toThemeMode(),
            dynamicColor = this[Keys.DYNAMIC_COLOR] ?: false,
            accent = if (accentName == CUSTOM_ACCENT) null else Accent.fromName(accentName) ?: d.accent,
            customHue = this[Keys.CUSTOM_HUE]?.takeIf { it.isFinite() } ?: d.customHue,
            glassIntensity = this[Keys.GLASS] ?: d.glassIntensity,
            blurIntensity = this[Keys.BLUR] ?: d.blurIntensity,
            cornerRadius = this[Keys.CORNER] ?: d.cornerRadius,
            wallpaper = WallpaperChoice.parse(this[Keys.WALLPAPER]),
            density = UiDensity.fromName(this[Keys.DENSITY]),
        ).clamped()
    }

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
        val VOICE_LANGUAGE = stringPreferencesKey("voice_typing_language")
        val ACCENT = stringPreferencesKey("accent")
        val CUSTOM_HUE = floatPreferencesKey("accent_custom_hue")
        val GLASS = intPreferencesKey("glass_intensity")
        val BLUR = intPreferencesKey("blur_intensity")
        val CORNER = intPreferencesKey("corner_radius")
        val WALLPAPER = stringPreferencesKey("wallpaper")
        val DENSITY = stringPreferencesKey("ui_density")
        val BLANK_NOTE = stringPreferencesKey("default_blank_note")
        val PAGE_STYLE = stringPreferencesKey("default_page_style")
        val PAGE_FORMAT = stringPreferencesKey("default_page_format")
        val PAGE_MODE = stringPreferencesKey("default_page_mode")
        val SCROLL_AXIS = stringPreferencesKey("default_scroll_axis")
        val PAPER_COLOR = intPreferencesKey("default_paper_color")
        val DARK_PAPER = booleanPreferencesKey("dark_paper")
        val RECENT_BRUSHES = stringPreferencesKey("ink_recent_brushes")
        val RECENT_COLORS = stringPreferencesKey("ink_recent_colors")
        val SAVED_COLORS = stringPreferencesKey("ink_saved_colors")
        val LASER_STYLE = stringPreferencesKey("laser_style")
        val LASER_RAINBOW = booleanPreferencesKey("laser_rainbow")
        val LASER_COLOR = intPreferencesKey("laser_color")
        val LASER_WIDTH = floatPreferencesKey("laser_width")
        val SHAPE_KIND = intPreferencesKey("shape_kind")
        val SHAPE_COLOR = intPreferencesKey("shape_color")
        val SHAPE_SIZE = floatPreferencesKey("shape_size")
        val SHAPE_FILLED = booleanPreferencesKey("shape_filled")
        val SHAPE_FILL = intPreferencesKey("shape_fill")
        val SHAPE_GRADIENT = booleanPreferencesKey("shape_gradient")
    }

    private companion object {
        const val TAG = "Settings"
        const val CUSTOM_ACCENT = "CUSTOM"
    }
}

/** Unknown or missing values (e.g. written by a future app version) fall back to SYSTEM. */
internal fun String?.toThemeMode(): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == this } ?: ThemeMode.SYSTEM

internal fun String?.toNoteSort(): NoteSort = NoteSort.entries.firstOrNull { it.name == this } ?: NoteSort.MODIFIED
