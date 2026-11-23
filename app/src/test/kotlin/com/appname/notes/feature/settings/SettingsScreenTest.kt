package com.appname.notes.feature.settings

import androidx.compose.ui.test.hasTestTag
import com.appname.notes.core.designsystem.theme.Accent
import com.appname.notes.core.designsystem.theme.UiDensity
import com.appname.notes.core.designsystem.theme.WallpaperPreset
import com.appname.notes.core.settings.WallpaperChoice
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
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
        actions: AppearanceActions = AppearanceActions(),
        blurSupported: Boolean = true,
    ) = composeRule.setContent {
        AppTheme {
            SettingsScreen(
                uiState = state,
                onThemeModeSelected = onTheme,
                onDynamicColorChanged = onDynamic,
                onSaveErrorShown = onErrorShown,
                dynamicColorSupported = dynamicSupported,
                appearanceActions = actions,
                blurSupported = blurSupported,
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
        // Lazy lists only compose what's on screen, so scroll each item into view first (also proves it's reachable).
        val list = composeRule.onNodeWithTag(SettingsTestTags.LIST)
        list.performScrollToNode(hasText(string(R.string.settings_storage_value)))
        composeRule.onNodeWithText(string(R.string.settings_storage_value)).assertIsDisplayed()
        list.performScrollToNode(hasText(string(R.string.settings_version)))
        composeRule.onNodeWithText(string(R.string.settings_version)).assertIsDisplayed()
    }

    @Test
    fun accentColours_reportTheChoice_andCustomOpensAHuePicker() {
        var accent: Accent? = null
        var hue: Float? = null
        show(SettingsUiState.Ready(AppearanceSettings()), actions = AppearanceActions(onAccent = { accent = it }, onCustomHue = { hue = it }))
        composeRule.onNodeWithTag(SettingsTestTags.accent(Accent.BLUE)).assertIsSelected()
        composeRule.onNodeWithTag(SettingsTestTags.accent(Accent.PINK)).performClick()
        assertThat(accent).isEqualTo(Accent.PINK)
        composeRule.onNodeWithTag(SettingsTestTags.ACCENT_CUSTOM).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.HUE_SLIDER).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.HUE_SAVE).performClick()
        assertThat(hue).isEqualTo(AppearanceSettings.DEFAULT_CUSTOM_HUE)
    }

    @Test
    fun glassBlurAndCornerSliders_showTheirValues() {
        show(SettingsUiState.Ready(AppearanceSettings(glassIntensity = 60, blurIntensity = 40, cornerRadius = 20)))
        composeRule.onNodeWithText("60%").assertExists()
        composeRule.onNodeWithText("40%").assertExists()
        composeRule.onNodeWithText("20 dp").assertExists()
    }

    @Test
    fun blurIsExplainedOnPhonesWithoutIt() {
        show(SettingsUiState.Ready(AppearanceSettings()), blurSupported = false)
        composeRule.onNodeWithText(string(R.string.settings_blur_unsupported)).assertExists()
    }

    @Test
    fun densityAndWallpaper_reportTheChoice() {
        var density: UiDensity? = null
        var wallpaper: WallpaperChoice? = null
        show(
            SettingsUiState.Ready(AppearanceSettings()),
            actions = AppearanceActions(onDensity = { density = it }, onWallpaper = { wallpaper = it }),
        )
        val list = composeRule.onNodeWithTag(SettingsTestTags.LIST)
        list.performScrollToNode(hasTestTag(SettingsTestTags.DENSITY_COMPACT))
        composeRule.onNodeWithTag(SettingsTestTags.DENSITY_COMFORTABLE).assertIsSelected()
        composeRule.onNodeWithTag(SettingsTestTags.DENSITY_COMPACT).performClick()
        assertThat(density).isEqualTo(UiDensity.COMPACT)

        list.performScrollToNode(hasTestTag(SettingsTestTags.WALLPAPER))
        composeRule.onNodeWithTag(SettingsTestTags.WALLPAPER).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.WALLPAPER_DIALOG).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.wallpaper(WallpaperChoice.DEFAULT)).assertIsSelected()
        val ocean = WallpaperChoice.Preset(WallpaperPreset.OCEAN)
        composeRule.onNodeWithTag(SettingsTestTags.wallpaper(ocean)).performClick()
        assertThat(wallpaper).isEqualTo(ocean)
    }

    @Test
    fun resetAsksFirst() {
        var reset = 0
        show(SettingsUiState.Ready(AppearanceSettings()), actions = AppearanceActions(onReset = { reset++ }))
        composeRule.onNodeWithTag(SettingsTestTags.LIST).performScrollToNode(hasTestTag(SettingsTestTags.RESET))
        composeRule.onNodeWithTag(SettingsTestTags.RESET).performClick()
        assertThat(reset).isEqualTo(0)
        composeRule.onNodeWithTag(SettingsTestTags.RESET_CONFIRM).performClick()
        assertThat(reset).isEqualTo(1)
    }
}
