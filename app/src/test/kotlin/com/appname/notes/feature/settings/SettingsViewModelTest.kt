package com.appname.notes.feature.settings

import app.cash.turbine.test
import com.appname.notes.core.settings.AppearanceSettings
import com.appname.notes.core.settings.ThemeMode
import com.appname.notes.core.settings.WallpaperChoice
import com.appname.notes.core.designsystem.theme.Accent
import com.appname.notes.core.designsystem.theme.UiDensity
import com.appname.notes.core.designsystem.theme.WallpaperPreset
import com.appname.notes.testing.FakeSettingsRepository
import com.appname.notes.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

/** Robolectric: the wallpaper tests pass real android.net.Uri values. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()
    private val switched = mutableListOf<Boolean>()

    private class FakePhotos : WallpaperPhotos {
        var importResult: String? = "w1.jpg"
        val kept = mutableListOf<String?>()
        override suspend fun import(uri: android.net.Uri): String? = importResult
        override suspend fun deleteAllExcept(keep: String?) { kept += keep }
        override suspend fun load(fileName: String): androidx.compose.ui.graphics.ImageBitmap? = null
    }

    private val photos = FakePhotos()

    // Created in @Before, after the rule has installed the test Main dispatcher.
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        viewModel = SettingsViewModel(
            repository = repository,
            indexRebuilder = { com.appname.notes.core.common.result.AppResult.Success(Unit) },
            documentTextSwitch = { switched += it },
            wallpaperPhotos = photos,
        )
    }

    @Test
    fun `starts loading then shows stored settings`() = runTest {
        viewModel.uiState.test {
            val first = awaitItem()
            val ready = if (first is SettingsUiState.Loading) awaitItem() else first
            assertThat(ready).isEqualTo(SettingsUiState.Ready(AppearanceSettings()))
        }
    }

    @Test
    fun `selecting a theme updates state`() = runTest {
        viewModel.uiState.test {
            skipUntilReady()
            viewModel.onThemeModeSelected(ThemeMode.DARK)
            assertThat(awaitReady().appearance.themeMode).isEqualTo(ThemeMode.DARK)
        }
    }

    @Test
    fun `toggling dynamic colour updates state`() = runTest {
        viewModel.uiState.test {
            skipUntilReady()
            viewModel.onDynamicColorChanged(true)
            assertThat(awaitReady().appearance.dynamicColor).isTrue()
        }
    }

    @Test
    fun `failed save shows an error until acknowledged and keeps old value`() = runTest {
        repository.failWrites = true
        viewModel.uiState.test {
            skipUntilReady()
            viewModel.onThemeModeSelected(ThemeMode.LIGHT)
            val failed = awaitReady()
            assertThat(failed.showSaveError).isTrue()
            assertThat(failed.appearance.themeMode).isEqualTo(ThemeMode.SYSTEM)

            viewModel.onSaveErrorShown()
            assertThat(awaitReady().showSaveError).isFalse()
        }
    }

    @Test
    fun `rebuilding the search index shows a confirmation once`() = runTest {
        viewModel.uiState.test {
            skipUntilReady()
            viewModel.onRebuildSearchIndex()
            assertThat(awaitReady().showIndexRebuilt).isTrue()
            viewModel.onIndexRebuiltShown()
            assertThat(awaitReady().showIndexRebuilt).isFalse()
        }
    }

    private suspend fun app.cash.turbine.TurbineTestContext<SettingsUiState>.skipUntilReady() {
        while (awaitItem() !is SettingsUiState.Ready) {
            // skip Loading
        }
    }

    private suspend fun app.cash.turbine.TurbineTestContext<SettingsUiState>.awaitReady(): SettingsUiState.Ready =
        awaitItem() as SettingsUiState.Ready

    @Test
    fun `turning document text off saves it and clears the index, on starts indexing`() = runTest {
        viewModel.onDocumentTextChanged(false)
        viewModel.onDocumentTextChanged(true)
        assertThat(switched).containsExactly(false, true).inOrder()
        assertThat(repository.documentTextEnabled.value).isTrue()
    }

    @Test
    fun `appearance choices are saved`() = runTest {
        viewModel.onAccentSelected(Accent.PINK)
        assertThat(repository.current().accent).isEqualTo(Accent.PINK)
        viewModel.onCustomHueSelected(123f)
        assertThat(repository.current().accent).isNull()
        assertThat(repository.current().accentHue).isEqualTo(123f)
        viewModel.onGlassIntensityChanged(20)
        viewModel.onBlurIntensityChanged(90)
        viewModel.onCornerRadiusChanged(28)
        viewModel.onDensitySelected(UiDensity.COMPACT)
        val a = repository.current()
        assertThat(listOf(a.glassIntensity, a.blurIntensity, a.cornerRadius)).containsExactly(20, 90, 28).inOrder()
        assertThat(a.density).isEqualTo(UiDensity.COMPACT)
    }

    @Test
    fun `choosing an accent turns wallpaper colours off`() = runTest {
        viewModel.onDynamicColorChanged(true)
        viewModel.onAccentSelected(Accent.MINT)
        assertThat(repository.current().dynamicColor).isFalse()
    }

    @Test
    fun `a picked photo becomes the wallpaper and choosing a preset deletes the photo`() = runTest {
        viewModel.onWallpaperPhotoPicked(android.net.Uri.parse("content://media/picker/1"))
        assertThat(repository.current().wallpaper).isEqualTo(WallpaperChoice.Photo("w1.jpg"))
        assertThat(photos.kept.last()).isEqualTo("w1.jpg") // older photos removed
        viewModel.onWallpaperSelected(WallpaperChoice.Preset(WallpaperPreset.OCEAN))
        assertThat(photos.kept.last()).isNull()
    }

    @Test
    fun `an unreadable photo is reported and the wallpaper stays`() = runTest {
        photos.importResult = null
        viewModel.uiState.test {
            skipUntilReady()
            viewModel.onWallpaperPhotoPicked(android.net.Uri.parse("content://media/picker/1"))
            assertThat(awaitReady().showPhotoFailed).isTrue()
            viewModel.onPhotoFailedShown()
            assertThat(awaitReady().showPhotoFailed).isFalse()
        }
        assertThat(repository.current().wallpaper).isEqualTo(WallpaperChoice.DEFAULT)
    }

    @Test
    fun `reset brings the default look back and removes the photo`() = runTest {
        viewModel.onAccentSelected(Accent.ORANGE)
        viewModel.onResetAppearance()
        assertThat(repository.current()).isEqualTo(AppearanceSettings())
        assertThat(photos.kept.last()).isNull()
    }

    @Test
    fun `a failed save doesn't touch the index`() = runTest {
        repository.failWrites = true
        viewModel.onDocumentTextChanged(false)
        assertThat(switched).isEmpty()
    }
}
