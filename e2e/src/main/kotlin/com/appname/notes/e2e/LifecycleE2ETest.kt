package com.appname.notes.e2e

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
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
 * - PDF from another app → annotate → process killed → reopen: the ink is still on the PDF page (Phase 4).
 * - photo shared from another app → new note → process killed → reopen: the photo is still attached (Phase 5).
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
        killInBackground() // what Android does to background apps under memory pressure

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
        val canvas = findCanvas().visibleBounds
        val y = canvas.top + canvas.height() / 4
        // A fresh install has no stylus seen yet, so a finger draws.
        device.swipe(canvas.left + canvas.width() / 5, y, canvas.right - canvas.width() / 5, y, 40)
        device.waitForIdle()

        device.pressHome()
        Thread.sleep(SAVE_WAIT_MS)
        killInBackground()

        launch(clearTask = false)
        findCanvas()
        Thread.sleep(1_000) // let the page draw
        assertThat(darkPixelsAlong(y, canvas.left + canvas.width() / 3, canvas.right - canvas.width() / 3)).isGreaterThan(20)

        // Back to the library: the note is still there (an empty one would have been discarded).
        device.pressBack()
        assertThat(device.wait(Until.hasObject(By.res(java.util.regex.Pattern.compile("ink_thumbnail_.*"))), TIMEOUT_MS)).isTrue()
    }

    /**
     * A PDF opened from another app ("Open with") is imported, opened, annotated, and the ink survives the
     * process being killed. Reopening restores the editor without importing the file a second time.
     */
    @Test
    fun pdfOpenedFromAnotherAppKeepsItsInkAfterProcessDeath() {
        val uri = FileProvider.getUriForFile(context, "com.appname.notes.e2e.files", samplePdf())
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/pdf")
            .setPackage(PACKAGE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(view)

        val canvas = findCanvas().visibleBounds
        Thread.sleep(1_000) // let the PDF page draw
        assertThat(device.hasObject(By.res("ink_pdf_failed"))).isFalse()
        val y = canvas.top + canvas.height() / 4
        device.swipe(canvas.left + canvas.width() / 5, y, canvas.right - canvas.width() / 5, y, 40)
        device.waitForIdle()

        device.pressHome()
        Thread.sleep(SAVE_WAIT_MS)
        killInBackground()

        launch(clearTask = false)
        findCanvas()
        Thread.sleep(1_000)
        assertThat(device.hasObject(By.res("ink_pdf_failed"))).isFalse()
        assertThat(darkPixelsAlong(y, canvas.left + canvas.width() / 3, canvas.right - canvas.width() / 3)).isGreaterThan(20)

        device.pressBack()
        assertThat(device.wait(Until.hasObject(By.text("Lecture notes")), TIMEOUT_MS)).isTrue()
        assertThat(device.findObjects(By.text("Lecture notes"))).hasSize(1) // not imported twice
    }

    /**
     * A photo shared from another app (Gallery/Camera "Share") becomes a new note with it attached, and
     * survives the process being killed (FR-009, SRS 16.2 lifecycle).
     */
    @Test
    fun photoSharedFromAnotherAppBecomesANoteThatSurvivesProcessDeath() {
        val photo = File(File(context.cacheDir, "shared").apply { mkdirs() }, "Lecture board.jpg")
        val bmp = android.graphics.Bitmap.createBitmap(640, 480, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.DKGRAY) }
        photo.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        val uri = FileProvider.getUriForFile(context, "com.appname.notes.e2e.files", photo)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .setPackage(PACKAGE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        send.clipData = android.content.ClipData.newRawUri("photo", uri)
        context.startActivity(send)

        val label = "Photo: Lecture board.jpg"
        assertThat(device.wait(Until.hasObject(By.desc(label)), TIMEOUT_MS)).isTrue()

        device.pressHome()
        Thread.sleep(SAVE_WAIT_MS)
        killInBackground()

        launch(clearTask = false)
        checkNotNull(device.wait(Until.findObject(By.desc(label)), TIMEOUT_MS)) { "Photo gone after process death. ${diagnostics()}" }
        device.pressBack()
        // One note in the library, with its attachment counted (not imported twice on relaunch).
        assertThat(device.wait(Until.hasObject(By.descContains("1 attachment")), TIMEOUT_MS)).isTrue()
        assertThat(device.findObjects(By.descContains("1 attachment"))).hasSize(1)
    }

    /** A one-page A4 PDF made with Android's own PDF writer, in the folder the FileProvider shares. */
    private fun samplePdf(): File {
        val file = File(File(context.cacheDir, "shared").apply { mkdirs() }, "Lecture notes.pdf")
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        page.canvas.drawRect(40f, 40f, 160f, 80f, Paint().apply { color = android.graphics.Color.GRAY })
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** The handwriting canvas is a classic Android View: found by its accessibility description, not a test tag. */
    private fun findCanvas(): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.descStartsWith("Handwriting page")), TIMEOUT_MS)) { "No handwriting canvas. ${diagnostics()}" }

    /** What the screen shows and any crash, so a failure explains itself in the CI report. */
    private fun diagnostics(): String {
        val shown = device.findObjects(By.pkg(PACKAGE)).mapNotNull { o ->
            listOfNotNull(o.resourceName, o.text, o.contentDescription).joinToString("/").takeIf { it.isNotBlank() }
        }.distinct().take(40)
        val crash = device.executeShellCommand("logcat -d -b crash").lines().take(40).joinToString("\n")
        val pkg = device.currentPackageName
        return "Foreground: $pkg. On screen: $shown. Crash log: $crash"
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
                ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3 < 160 // paper ≈ 255, rules ≈ 220
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
        checkNotNull(device.wait(Until.findObject(By.res(resourceId)), TIMEOUT_MS)) { "No view with id $resourceId. ${diagnostics()}" }

    private fun typeInto(field: UiObject2, text: String) {
        field.click()
        field.text = text
        device.waitForIdle()
    }

    /**
     * Kills the backgrounded app the way Android does under memory pressure (`am kill`). Android only kills an
     * idle background process: while background work runs (e.g. text indexing after an import) the kill is
     * refused, as it would be on a phone. So the kill is repeated until the work is done and it succeeds.
     */
    private fun killInBackground() {
        val deadline = System.currentTimeMillis() + KILL_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            device.executeShellCommand("am kill $PACKAGE")
            Thread.sleep(500)
            if (device.executeShellCommand("pidof $PACKAGE").isBlank()) return
        }
        throw AssertionError("$PACKAGE is still running. ${diagnostics()}")
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
        const val KILL_TIMEOUT_MS = 60_000L
    }
}
