package com.appname.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.feature.home.HomeTestTags
import com.appname.notes.feature.settings.SettingsTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a real emulator/device: real rendering, real DataStore file, real back handling. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigateToSettings_recreate_andBack() {
        composeRule.onNodeWithTag(HomeTestTags.SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SCREEN).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate() // same path as rotation / theme change
        composeRule.onNodeWithTag(SettingsTestTags.SCREEN).assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag(HomeTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_home").assertIsSelected()
    }

    @Test
    fun themeSelection_appliesImmediately() {
        val dark = composeRule.activity.getString(R.string.settings_theme_dark)
        val system = composeRule.activity.getString(R.string.settings_theme_system)

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithText(dark).performClick()
        waitUntilSelected(dark) // saved on a background thread, then shown

        // Restore the default so test order doesn't matter.
        composeRule.onNodeWithText(system).performClick()
        waitUntilSelected(system)
    }

    private fun waitUntilSelected(text: String) {
        composeRule.waitUntilExactlyOneExists(hasText(text) and isSelected(), timeoutMillis = 5_000)
    }
}
