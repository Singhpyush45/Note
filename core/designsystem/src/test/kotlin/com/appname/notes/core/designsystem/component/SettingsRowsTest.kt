package com.appname.notes.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRowsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchRow_isOneAccessibleSwitch_thatToggles() {
        composeRule.setContent {
            AppTheme {
                var checked by remember { mutableStateOf(false) }
                SettingsSwitchRow(title = "Dynamic colour", checked = checked, onCheckedChange = { checked = it })
            }
        }
        val row = composeRule.onNodeWithText("Dynamic colour")
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        row.assertIsOff()
        row.assertHeightIsAtLeast(48.dp)
        row.performClick()
        row.assertIsOn()
    }

    @Test
    fun disabledSwitchRow_doesNotToggle() {
        composeRule.setContent {
            AppTheme {
                var checked by remember { mutableStateOf(false) }
                SettingsSwitchRow(
                    title = "Disabled option",
                    checked = checked,
                    onCheckedChange = { checked = it },
                    enabled = false,
                )
            }
        }
        val row = composeRule.onNodeWithText("Disabled option")
        row.performClick()
        row.assertIsOff()
    }

    @Test
    fun radioRows_selectExactlyOne() {
        composeRule.setContent {
            AppTheme {
                var selected by remember { mutableStateOf("System") }
                SettingsRadioGroup {
                    listOf("System", "Light", "Dark").forEach { option ->
                        SettingsRadioRow(title = option, selected = selected == option, onClick = { selected = option })
                    }
                }
            }
        }
        composeRule.onNodeWithText("System").assertIsSelected()
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.onNodeWithText("Dark").assertIsSelected()
        composeRule.onNodeWithText("System").assertIsNotSelected()
        composeRule.onNodeWithText("Dark")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun sectionHeader_isAHeading() {
        composeRule.setContent { AppTheme { SettingsSectionHeader("Appearance") } }
        composeRule.onNodeWithText("Appearance").assert(isHeading())
    }

    @Test
    fun emptyState_titleIsAHeading_andBodyIsShown() {
        composeRule.setContent { AppTheme { EmptyState(title = "No notes yet", body = "Tap + to start") } }
        composeRule.onNodeWithText("No notes yet").assert(isHeading())
        composeRule.onNodeWithText("Tap + to start").assertExists()
    }
}
