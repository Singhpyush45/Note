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
import com.appname.notes.feature.calendar.PlannerTestTags
import com.appname.notes.feature.library.LibraryTestTags
import com.appname.notes.feature.settings.SettingsTestTags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Whole-app tests on a simulated device (fast, no emulator). Runs on the minimum (26) and the newest
 * Robolectric-supported (36) API levels.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 36])
class AppNavigationRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int) = composeRule.activity.getString(id)

    @Test
    fun launch_showsHomeWithEmptyState() {
        composeRule.onNodeWithTag(LibraryTestTags.SCREEN).assertIsDisplayed()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.home_empty_title)), timeoutMillis = 5_000)
        composeRule.onNodeWithTag("nav_home").assertIsSelected()
    }

    @Test
    fun calendarTab_opensCalendarAndTasks_andBackReturnsHome() {
        composeRule.onNodeWithTag("nav_calendar").performClick()
        composeRule.onNodeWithTag(PlannerTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_calendar").assertIsSelected()

        composeRule.onNodeWithTag(PlannerTestTags.TAB_TASKS).performClick()
        composeRule.onNodeWithTag(PlannerTestTags.FILTER_PENDING).assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithTag(LibraryTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_home").assertIsSelected()
        assertThat(composeRule.activity.isFinishing).isFalse()
    }

    @Test
    fun settingsTab_opensSettings_andBackReturnsHome() {
        composeRule.onNodeWithTag(LibraryTestTags.MORE).performClick()
        composeRule.onNodeWithTag(LibraryTestTags.SETTINGS).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.BACK).assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithTag(LibraryTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_home").assertIsSelected()
        assertThat(composeRule.activity.isFinishing).isFalse()
    }

    @Test
    fun backFromHome_leavesTheApp() {
        pressBack()
        assertThat(composeRule.activity.isFinishing).isTrue()
    }

    @Test
    fun backStack_survivesActivityRecreation() {
        composeRule.onNodeWithTag(LibraryTestTags.MORE).performClick()
        composeRule.onNodeWithTag(LibraryTestTags.SETTINGS).performClick()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag(SettingsTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun themeChoice_persistsAcrossRecreation() {
        composeRule.onNodeWithTag(LibraryTestTags.MORE).performClick()
        composeRule.onNodeWithTag(LibraryTestTags.SETTINGS).performClick()
        composeRule.onNodeWithText(string(R.string.settings_theme_dark)).performClick()
        // The radio reflects what was *saved*; the DataStore write finishes on a background thread.
        waitUntilSelected(string(R.string.settings_theme_dark))

        composeRule.activityRule.scenario.recreate()

        waitUntilSelected(string(R.string.settings_theme_dark))
    }

    private fun waitUntilSelected(text: String) =
        composeRule.waitUntilExactlyOneExists(hasText(text) and isSelected(), timeoutMillis = 5_000)

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }
}
