package com.appname.notes.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Uses a real DataStore file on disk, so this also proves values survive an app restart. */
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val file get() = File(tmp.root, "settings.preferences_pb")

    /** Each "process" gets its own DataStore scope; cancel it before opening the file again. */
    private fun openStore(job: Job) = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatcher + job),
        produceFile = { file },
    )

    @Test
    fun `defaults when nothing was saved`() = testScope.runTest {
        val job = Job()
        val repo = DataStoreSettingsRepository(openStore(job), NoOpLogger)
        assertThat(repo.appearance.first()).isEqualTo(AppearanceSettings())
        job.cancelAndJoin()
    }

    @Test
    fun `saved values are read back and survive a restart`() = testScope.runTest {
        val firstRun = Job()
        val repo = DataStoreSettingsRepository(openStore(firstRun), NoOpLogger)
        assertThat(repo.setThemeMode(ThemeMode.DARK)).isEqualTo(AppResult.Success(Unit))
        assertThat(repo.setDynamicColor(true)).isEqualTo(AppResult.Success(Unit))
        assertThat(repo.appearance.first()).isEqualTo(AppearanceSettings(ThemeMode.DARK, dynamicColor = true))
        firstRun.cancelAndJoin()

        val secondRun = Job()
        val reopened = DataStoreSettingsRepository(openStore(secondRun), NoOpLogger)
        assertThat(reopened.appearance.first()).isEqualTo(AppearanceSettings(ThemeMode.DARK, dynamicColor = true))
        secondRun.cancelAndJoin()
    }

    @Test
    fun `unknown stored theme falls back to system`() = testScope.runTest {
        val job = Job()
        val store = openStore(job)
        store.edit { it[stringPreferencesKey("theme_mode")] = "PURPLE" }
        val repo = DataStoreSettingsRepository(store, NoOpLogger)
        assertThat(repo.appearance.first().themeMode).isEqualTo(ThemeMode.SYSTEM)
        job.cancelAndJoin()
    }

    @Test
    fun `the whole look is saved, survives a restart, and resets to defaults`() = testScope.runTest {
        val firstRun = Job()
        val repo = DataStoreSettingsRepository(openStore(firstRun), NoOpLogger)
        val custom = AppearanceSettings(
            themeMode = ThemeMode.LIGHT,
            accent = null,
            customHue = 75f,
            glassIntensity = 35,
            blurIntensity = 80,
            cornerRadius = 12,
            wallpaper = WallpaperChoice.Photo("abc.jpg"),
            density = com.appname.notes.core.designsystem.theme.UiDensity.COMPACT,
        )
        assertThat(repo.updateAppearance { custom }).isEqualTo(AppResult.Success(Unit))
        firstRun.cancelAndJoin()

        val secondRun = Job()
        val reopened = DataStoreSettingsRepository(openStore(secondRun), NoOpLogger)
        assertThat(reopened.appearance.first()).isEqualTo(custom)
        assertThat(reopened.appearance.first().accentHue).isEqualTo(75f)
        reopened.updateAppearance { it.copy(accent = com.appname.notes.core.designsystem.theme.Accent.MINT) }
        assertThat(reopened.appearance.first().accentHue).isEqualTo(com.appname.notes.core.designsystem.theme.Accent.MINT.hue)
        reopened.resetAppearance()
        assertThat(reopened.appearance.first()).isEqualTo(AppearanceSettings())
        secondRun.cancelAndJoin()
    }

    @Test
    fun `out-of-range and unknown appearance values are corrected`() = testScope.runTest {
        val job = Job()
        val store = openStore(job)
        store.edit {
            it[androidx.datastore.preferences.core.intPreferencesKey("glass_intensity")] = 250
            it[androidx.datastore.preferences.core.intPreferencesKey("corner_radius")] = 2
            it[stringPreferencesKey("accent")] = "NEON"
            it[stringPreferencesKey("wallpaper")] = "preset:GALAXY"
            it[stringPreferencesKey("ui_density")] = "TINY"
        }
        val a = DataStoreSettingsRepository(store, NoOpLogger).appearance.first()
        assertThat(a.glassIntensity).isEqualTo(100)
        assertThat(a.cornerRadius).isEqualTo(8)
        assertThat(a.accent).isEqualTo(com.appname.notes.core.designsystem.theme.Accent.DEFAULT)
        assertThat(a.wallpaper).isEqualTo(WallpaperChoice.DEFAULT)
        assertThat(a.density).isEqualTo(com.appname.notes.core.designsystem.theme.UiDensity.COMFORTABLE)
        job.cancelAndJoin()
    }

    @Test
    fun `wallpaper choices round-trip through their stored form`() {
        val all = listOf(WallpaperChoice.None, WallpaperChoice.Photo("x.jpg")) +
            com.appname.notes.core.designsystem.theme.WallpaperPreset.entries.map { WallpaperChoice.Preset(it) }
        all.forEach { assertThat(WallpaperChoice.parse(it.stored)).isEqualTo(it) }
        assertThat(WallpaperChoice.parse(null)).isEqualTo(WallpaperChoice.DEFAULT)
        assertThat(WallpaperChoice.parse("photo:")).isEqualTo(WallpaperChoice.DEFAULT)
    }

    @Test
    fun `theme mode parsing handles null and every enum name`() {
        assertThat((null as String?).toThemeMode()).isEqualTo(ThemeMode.SYSTEM)
        ThemeMode.entries.forEach { assertThat(it.name.toThemeMode()).isEqualTo(it) }
    }

    @Test
    fun `note defaults are saved, read back, and unknown values fall back`() = testScope.runTest {
        val job = Job()
        val store = openStore(job)
        val repo = DataStoreSettingsRepository(store, NoOpLogger)
        assertThat(repo.noteDefaults.first()).isEqualTo(NoteDefaults())
        val mine = NoteDefaults(
            blankNote = BlankNoteKind.HANDWRITING,
            pageStyle = "basic.grid_narrow",
            pageFormat = com.appname.notes.core.ink.template.PageFormat.LONG_20_9,
            pageMode = com.appname.notes.core.ink.layout.PageMode.INFINITE,
            scrollAxis = com.appname.notes.core.ink.layout.ScrollAxis.HORIZONTAL,
            paperColor = 0xFFFFF8E7.toInt(),
            darkPaper = false,
        )
        assertThat(repo.updateNoteDefaults { mine }).isEqualTo(AppResult.Success(Unit))
        assertThat(repo.noteDefaults.first()).isEqualTo(mine)
        // A style that isn't a Basic template (or a value from a newer version) falls back to the default.
        store.edit {
            it[stringPreferencesKey("default_page_style")] = "planners.weekly"
            it[stringPreferencesKey("default_page_mode")] = "SPIRAL"
        }
        val read = repo.noteDefaults.first()
        assertThat(read.pageStyle).isEqualTo(NoteDefaults.DEFAULT_STYLE)
        assertThat(read.pageMode).isEqualTo(com.appname.notes.core.ink.layout.PageMode.SPLIT)
        // The first page of a new blank note uses the style, size and paper.
        val page = mine.newPageLayout()
        assertThat(page.width).isEqualTo(794f)
        assertThat(page.height).isEqualTo(com.appname.notes.core.ink.template.PageFormat.LONG_20_9.height)
        assertThat(page.paperColor).isEqualTo(0xFFFFF8E7.toInt())
        assertThat(page.paper.spacing).isEqualTo(16f)
        job.cancelAndJoin()
    }
}
