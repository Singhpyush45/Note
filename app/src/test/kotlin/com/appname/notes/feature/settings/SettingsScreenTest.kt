package com.appname.notes.feature.settings

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.R
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.settings.AppearanceSettings
import com.appname.notes.core.settings.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Screen-only tests: the ViewModel is replaced by plain state + callbacks. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun show(
        state: SettingsUiState,
        dynamicSupported: Boolean = true,
        onTheme: (ThemeMode) -> Unit = {},
        onDynamic: (Boolean) -> Unit = {},
        onErrorShown: () -> Unit = {},
    ) = composeRule.setContent {
        AppTheme {
            SettingsScreen(
                uiState = state,
                onThemeModeSelected = onTheme,
                onDynamicColorChanged = onDynamic,
                onSaveErrorShown = onErrorShown,
                dynamicColorSupported = dynamicSupported,
            )
        }
    }

    @Test
    fun showsCurrentTheme_andReportsNewChoice() {
        var chosen: ThemeMode? = null
        show(SettingsUiState.Ready(AppearanceSettings(themeMode = ThemeMode.LIGHT)), onTheme = { chosen = it })

        composeRule.onNodeWithText(string(R.string.settings_theme_light)).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.settings_theme_dark)).performClick()
        assertThat(chosen).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun dynamicColour_toggleReportsChange() {
        var dynamic: Boolean? = null
        show(SettingsUiState.Ready(AppearanceSettings()), onDynamic = { dynamic = it })
        composeRule.onNodeWithText(string(R.string.settings_dynamic_color)).performClick()
        assertThat(dynamic).isTrue()
    }

    @Test
    fun dynamicColour_disabledOnOldAndroid_withExplanation() {
        show(SettingsUiState.Ready(AppearanceSettings(dynamicColor = true)), dynamicSupported = false)
        composeRule.onNodeWithText(string(R.string.settings_dynamic_color))
            .assertIsNotEnabled()
            .assertIsOff()
        composeRule.onNodeWithText(string(R.string.settings_dynamic_color_unsupported)).assertExists()
    }

    @Test
    fun saveError_showsSnackbar_andIsAcknowledged() {
        var acknowledged = false
        composeRule.mainClock.autoAdvance = false // control time so the snackbar can't time out early
        show(
            SettingsUiState.Ready(AppearanceSettings(), showSaveError = true),
            onErrorShown = { acknowledged = true },
        )
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText(string(R.string.settings_save_failed)).assertExists()
        assertThat(acknowledged).isFalse()

        composeRule.mainClock.advanceTimeBy(15_000) // past the short snackbar duration
        assertThat(acknowledged).isTrue()
    }

    @Test
    fun privacyAndVersionInfo_areVisible() {
        show(SettingsUiState.Ready(AppearanceSettings()))
        composeRule.onNodeWithText(string(R.string.settings_storage_value)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_version)).assertExists()
    }
}
