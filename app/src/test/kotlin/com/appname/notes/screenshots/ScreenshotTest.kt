package com.appname.notes.screenshots

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.MainActivity
import com.appname.notes.R
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders real screens to PNG files in app/build/screenshots (CI publishes them to the `screenshots`
 * branch) so UI changes can be reviewed without a device. Also checks the adaptive navigation layout.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTestBase(private val device: String) {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    protected fun snap(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "${device}_$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    protected fun string(id: Int) = composeRule.activity.getString(id)

    protected fun openSettings() = composeRule.onNodeWithTag("nav_settings").performClick()

    protected fun chooseTheme(id: Int) {
        composeRule.onNodeWithText(string(id)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(id)) and isSelected(), timeoutMillis = 5_000)
    }

    @Test
    fun captureLightAndDark() {
        snap("home_light")
        openSettings()
        chooseTheme(R.string.settings_theme_light)
        snap("settings_light")
        chooseTheme(R.string.settings_theme_dark)
        snap("settings_dark")
        composeRule.onNodeWithTag("nav_home").performClick()
        snap("home_dark")
    }
}

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneScreenshotTest : ScreenshotTestBase("phone") {

    @Test
    fun phoneUsesBottomNavigationBar() {
        val item = composeRule.onNodeWithTag("nav_settings").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(item.top).isGreaterThan(root.height * 0.75f) // bottom of the screen
    }
}

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp-land-xhdpi")
class TabletScreenshotTest : ScreenshotTestBase("tablet") {

    @Test
    fun tabletUsesNavigationRail() {
        val item = composeRule.onNodeWithTag("nav_settings").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(item.left).isLessThan(root.width * 0.15f) // left edge, not a bottom bar
        assertThat(item.top).isLessThan(root.height * 0.75f)
    }
}
