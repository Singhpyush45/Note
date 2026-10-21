package com.appname.notes.feature.settings

import app.cash.turbine.test
import com.appname.notes.core.settings.AppearanceSettings
import com.appname.notes.core.settings.ThemeMode
import com.appname.notes.testing.FakeSettingsRepository
import com.appname.notes.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()

    // Created in @Before, after the rule has installed the test Main dispatcher.
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        viewModel = SettingsViewModel(repository) { com.appname.notes.core.common.result.AppResult.Success(Unit) }
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
}
