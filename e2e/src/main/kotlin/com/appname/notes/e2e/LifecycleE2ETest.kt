package com.appname.notes.e2e

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import java.io.File
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SRS 16.2 mandatory regression scenarios that need the app process to really die:
 * - write → background → system kills the process → reopen: same screen, same text (FR-008).
 * - write → background → force-stop → relaunch: note is in the library (FR-002, NFR-04).
 * - handwrite → background → process killed → reopen: the ink is still on the page (Phase 3).
 */
@RunWith(AndroidJUnit4::class)
class LifecycleE2ETest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun freshApp() {
        device.executeShellCommand("pm clear $PACKAGE") // start from an empty database every time
        launch(clearTask = true)
    }

    @Test
    fun textAndScreenSurviveProcessDeath() {
        find("fab_new_note").click()
        typeInto(find("editor_title"), "Survivor")
        typeInto(find("editor_body"), "This text must survive process death")

        device.pressHome()
        Thread.sleep(SAVE_WAIT_MS) // the app saves when it goes to the background
        device.executeShellCommand("am kill $PACKAGE") // what Android does to background apps under memory pressure
        waitUntilProcessGone()

        launch(clearTask = false) // like returning from recent apps: the task and its saved state are restored
        val body = find("editor_body")
        assertThat(body.text).contains("This text must survive process death")
        assertThat(find("editor_title").text).contains("Survivor")
    }

    @Test
    fun noteSurvivesForceStop() {
        find("fab_new_note").click()
        typeInto(find("editor_body"), "Force stop safe")

        device.pressHome()
        Thread.sleep(SAVE_WAIT_MS)
        device.executeShellCommand("am force-stop $PACKAGE")
        waitUntilProcessGone()

        launch(clearTask = true) // a cold start from the launcher
        assertThat(device.wait(Until.hasObject(By.textContains("Force stop safe")), TIMEOUT_MS)).isTrue()
    }

    @Test
    fun handwritingSurvivesProcessDeath() {
        find("fab_new_ink_note").click()
        val canvas = find("ink_canvas").visibleBounds
        val y = canvas.top + canvas.height() / 4
        // A fresh install has no stylus seen yet, so a finger draws.
        device.swipe(canvas.left + canvas.width() / 5, y, canvas.right - canvas.width() / 5, y, 40)
        device.waitForIdle()

        device.pressHome()
        Thread.sleep(SAVE_WAIT_MS)
        device.executeShellCommand("am kill $PACKAGE")
        waitUntilProcessGone()

        launch(clearTask = false)
        find("ink_canvas")
        Thread.sleep(1_000) // let the page draw
        assertThat(darkPixelsAlong(y, canvas.left + canvas.width() / 3, canvas.right - canvas.width() / 3)).isGreaterThan(20)

        // Back to the library: the note is still there (an empty one would have been discarded).
        device.pressBack()
        assertThat(device.wait(Until.hasObject(By.res(java.util.regex.Pattern.compile("ink_thumbnail_.*"))), TIMEOUT_MS)).isTrue()
    }

    /** Counts dark pixels in a narrow band around screen row [y] between [x0] and [x1]. */
    private fun darkPixelsAlong(y: Int, x0: Int, x1: Int): Int {
        val file = File(context.cacheDir, "ink_e2e.png")
        check(device.takeScreenshot(file)) { "screenshot failed" }
        val bitmap = BitmapFactory.decodeFile(file.path)
        var dark = 0
        for (x in x0 until x1 step 2) {
            val hit = (y - 6..y + 6).any { yy ->
                val c = bitmap.getPixel(x, yy)
                ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3 < 110
            }
            if (hit) dark++
        }
        return dark
    }

    private fun launch(clearTask: Boolean) {
        val intent = checkNotNull(context.packageManager.getLaunchIntentForPackage(PACKAGE)) { "App under test not installed" }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (clearTask) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        assertThat(device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), TIMEOUT_MS)).isTrue()
    }

    private fun find(resourceId: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.res(resourceId)), TIMEOUT_MS)) { "No view with id $resourceId" }

    private fun typeInto(field: UiObject2, text: String) {
        field.click()
        field.text = text
        device.waitForIdle()
    }

    private fun waitUntilProcessGone() {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (device.executeShellCommand("pidof $PACKAGE").isBlank()) return
            Thread.sleep(200)
        }
        throw AssertionError("$PACKAGE is still running")
    }

    private companion object {
        const val PACKAGE = "com.appname.notes.debug"
        const val TIMEOUT_MS = 15_000L
        const val SAVE_WAIT_MS = 2_000L
    }
}
