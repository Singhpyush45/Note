package com.appname.notes

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.di.AppDataEntryPoint
import com.appname.notes.feature.attachments.AttachError
import com.appname.notes.feature.attachments.AttachResult
import com.appname.notes.feature.attachments.Attacher
import com.appname.notes.feature.attachments.AttachmentFileStore
import com.appname.notes.feature.attachments.AttachmentIntents
import com.appname.notes.feature.attachments.AttachmentPreviews
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Attachments on a real Android (FR-009): real photos and PDFs, EXIF orientation, previews, opening through
 * the FileProvider, storage errors and cleanup. Uses the app's real database.
 */
@RunWith(AndroidJUnit4::class)
class AttachmentDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val data = EntryPointAccessors.fromApplication(context.applicationContext as Application, AppDataEntryPoint::class.java)
    private val dir = File(context.cacheDir, "attachment-device-test").apply { mkdirs() }
    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Main
        override val io = Dispatchers.IO
        override val default = Dispatchers.Default
    }

    private fun store(free: Long = Long.MAX_VALUE, graceMs: Long = 10 * 60_000L) =
        AttachmentFileStore(context, NoOpLogger, freeBytes = { free }, graceMs = graceMs)

    /** A 400 × 200 JPEG (wide), left half red, saved by the "camera" with an EXIF tag saying: rotate 90°. */
    private fun sidewaysPhoto(): File {
        val f = File(dir, "IMG_0001.jpg")
        val bmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
            for (x in 0 until 200) for (y in 0 until 200) setPixel(x, y, Color.RED)
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(f.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        return f
    }

    private fun onePagePdf(): File {
        val f = File(dir, "Handout.pdf")
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        page.canvas.drawColor(Color.WHITE)
        doc.finishPage(page)
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    @Test
    fun sidewaysCameraPhotoIsStoredWithItsUprightSizeAndPreviewedUpright() = runBlocking {
        val s = store()
        val a = (s.import(Uri.fromFile(sidewaysPhoto())) as AttachResult.Ok).attachment
        assertThat(a.kind).isEqualTo(AttachmentKind.IMAGE)
        assertThat(a.mimeType).isEqualTo("image/jpeg")
        assertThat(a.width to a.height).isEqualTo(200 to 400) // as the person sees it: tall
        val preview = AttachmentPreviews(s, dispatchers).load(a.fileName, a.kind, 100)!!
        assertThat(preview.height).isGreaterThan(preview.width)
        // Rotated 90° clockwise, the red (left) half ends up on top.
        assertThat(Color.red(preview.getPixel(preview.width / 2, preview.height / 4))).isGreaterThan(200)
        assertThat(Color.blue(preview.getPixel(preview.width / 2, preview.height * 3 / 4))).isGreaterThan(200)
    }

    @Test
    fun pdfAttachmentGetsAFirstPagePreview() = runBlocking {
        val s = store()
        val a = (s.import(Uri.fromFile(onePagePdf())) as AttachResult.Ok).attachment
        assertThat(a.kind).isEqualTo(AttachmentKind.PDF)
        val preview = AttachmentPreviews(s, dispatchers).load(a.fileName, a.kind, 120)
        assertThat(preview).isNotNull()
        assertThat(preview!!.height).isGreaterThan(preview.width) // A4 portrait
    }

    @Test
    fun openingGivesAnotherAppReadAccessToJustThatFile() = runBlocking {
        val id = (data.notes().createNote(null) as AppResult.Success).value
        val attacher = Attacher(store(), data.attachments(), dispatchers)
        val added = attacher.attach(id, listOf(Uri.fromFile(onePagePdf()))).added.single()
        val intents = AttachmentIntents(context, store())
        val view = intents.viewIntent(added)
        assertThat(view.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(view.data!!.scheme).isEqualTo("content")
        assertThat(view.data!!.authority).isEqualTo("${context.packageName}.files")
        assertThat(view.type).isEqualTo("application/pdf")
        assertThat(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        // The link really serves the stored bytes, under the name the user knows.
        val bytes = context.contentResolver.openInputStream(view.data!!)!!.use { it.readBytes() }
        assertThat(bytes).isEqualTo(store().file(added.fileName).readBytes())
        assertThat(context.packageManager.queryIntentActivities(view, 0)).isNotNull() // resolvable without crashing
    }

    @Test
    fun aDamagedPhotoIsKeptAsAPlainFileInsteadOfFailing() {
        val broken = File(dir, "broken.jpg").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        val a = (store().import(Uri.fromFile(broken)) as AttachResult.Ok).attachment
        assertThat(a.kind).isEqualTo(AttachmentKind.FILE)
        assertThat(a.width).isNull()
        assertThat(a.displayName).isEqualTo("broken.jpg")
    }

    /** What happens after the scanner returns its PDF (the scanner screen itself is Google's, tested on a device). */
    @Test
    fun aScannedPdfIsAttachedUnderADatedNameAndIsAPdf() = runBlocking {
        val when_ = java.util.GregorianCalendar(2026, java.util.Calendar.OCTOBER, 24, 18, 30, 5).time
        val staged = com.appname.notes.feature.attachments.stageScan(context, Uri.fromFile(onePagePdf()), when_)!!
        assertThat(staged.name).isEqualTo("Scan 2026-10-24 18.30.05.pdf")
        val id = (data.notes().createNote(null) as AppResult.Success).value
        val intents = AttachmentIntents(context, store())
        val a = Attacher(store(), data.attachments(), dispatchers).attach(id, listOf(intents.uriFor(staged))).added.single()
        assertThat(a.displayName).isEqualTo("Scan 2026-10-24 18.30.05.pdf")
        assertThat(a.kind).isEqualTo(AttachmentKind.PDF)
        assertThat(a.mimeType).isEqualTo("application/pdf")
    }

    @Test
    fun fullStorageIsReportedAndNothingIsAttached() = runBlocking {
        val id = (data.notes().createNote(null) as AppResult.Success).value
        val full = store(free = 1_000)
        assertThat((full.import(Uri.fromFile(onePagePdf())) as AttachResult.Failed).error).isEqualTo(AttachError.STORAGE_FULL)
        val outcome = Attacher(full, data.attachments(), dispatchers).attach(id, listOf(Uri.fromFile(onePagePdf())))
        assertThat(outcome.added).isEmpty()
        assertThat(outcome.errors).containsExactly(AttachError.STORAGE_FULL)
        assertThat((data.attachments().list(id) as AppResult.Success).value).isEmpty()
    }

    @Test
    fun deletingTheNoteFreesItsFilesButNotThoseOfACopy() = runBlocking {
        val noGrace = store(graceMs = 0)
        val attacher = Attacher(noGrace, data.attachments(), dispatchers)
        val a = (data.notes().createNote(null) as AppResult.Success).value
        val b = (data.notes().createNote(null) as AppResult.Success).value
        val fileA = attacher.attach(a, listOf(Uri.fromFile(sidewaysPhoto()))).added.single().fileName
        val fileB = attacher.attach(b, listOf(Uri.fromFile(onePagePdf()))).added.single().fileName
        val copyOfA: NoteId = (data.notes().duplicate(a) as AppResult.Success).value

        data.notes().deletePermanently(setOf(a, b))
        // Files are older than "now" by the time cleanup runs only with no grace period (the app keeps 10 minutes).
        listOf(fileA, fileB).forEach { noGrace.file(it).setLastModified(System.currentTimeMillis() - 1_000) }
        attacher.cleanUp()
        assertThat(noGrace.file(fileA).exists()).isTrue() // the copy still shows this photo
        assertThat(noGrace.file(fileB).exists()).isFalse()
        assertThat((data.attachments().list(copyOfA) as AppResult.Success).value.single().fileName).isEqualTo(fileA)
    }
}
