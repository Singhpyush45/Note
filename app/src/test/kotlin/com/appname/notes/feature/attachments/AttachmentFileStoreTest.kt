package com.appname.notes.feature.attachments

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.domain.model.AttachmentKind
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowMimeTypeMap
import org.robolectric.annotation.Config

/**
 * FR-009 storage behaviour and SRS 15.1 "storage low": the store's checks, on the JVM. Image decoding is
 * faked on the JVM, so photo handling is tested on the emulator (AttachmentDeviceTest).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AttachmentFileStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val source = File(context.cacheDir, "source").apply { mkdirs() }

    @Suppress("DEPRECATION") // Robolectric's MIME-map shadow: still the documented way to seed the map
    @Before
    fun mimeTypes() {
        // Robolectric's MIME map starts empty; Android's has these.
        Shadow.extract<ShadowMimeTypeMap>(MimeTypeMap.getSingleton()).apply {
            addExtensionMimeTypeMapping("zip", "application/zip")
            addExtensionMimeTypeMapping("pdf", "application/pdf")
        }
    }

    private fun store(maxBytes: Long = 1_000_000, free: Long = Long.MAX_VALUE, graceMs: Long = 0) =
        AttachmentFileStore(context, NoOpLogger, maxBytes = maxBytes, freeBytes = { free }, graceMs = graceMs)

    private fun file(name: String, bytes: Int) = File(source, name).apply { writeBytes(ByteArray(bytes) { (it % 251).toByte() }) }

    private fun stored(s: AttachmentFileStore) = s.dir.listFiles().orEmpty().map { it.name }

    @Test
    fun `a file is copied byte for byte, keeps its name and extension, and is described`() {
        val s = store()
        val f = file("Lecture notes.zip", 4096)
        val r = s.import(Uri.fromFile(f)) as AttachResult.Ok
        val a = r.attachment
        assertThat(a.displayName).isEqualTo("Lecture notes.zip")
        assertThat(a.kind).isEqualTo(AttachmentKind.FILE)
        assertThat(a.mimeType).isEqualTo("application/zip")
        assertThat(a.sizeBytes).isEqualTo(4096)
        assertThat(a.fileName).endsWith(".zip") // other apps recognise it when opened from the note
        assertThat(s.file(a.fileName).readBytes()).isEqualTo(f.readBytes())
        assertThat(stored(s).none { it.endsWith(".part") }).isTrue()
    }

    @Test
    fun `a PDF is recognised as a PDF`() {
        val r = store().import(Uri.fromFile(file("Slides.pdf", 100))) as AttachResult.Ok
        assertThat(r.attachment.kind).isEqualTo(AttachmentKind.PDF)
    }

    @Test
    fun `too large is refused and leaves nothing behind`() {
        val s = store(maxBytes = 1000)
        val r = s.import(Uri.fromFile(file("big.zip", 5000)))
        assertThat((r as AttachResult.Failed).error).isEqualTo(AttachError.TOO_LARGE)
        assertThat(stored(s)).isEmpty()
    }

    @Test
    fun `low storage is refused before copying and says so`() {
        val s = store(free = 10)
        val r = s.import(Uri.fromFile(file("a.zip", 5000)))
        assertThat((r as AttachResult.Failed).error).isEqualTo(AttachError.STORAGE_FULL)
        assertThat(stored(s)).isEmpty()
    }

    @Test
    fun `a file that disappeared is reported as unreadable`() {
        val s = store()
        val r = s.import(Uri.fromFile(File(source, "gone.zip")))
        assertThat((r as AttachResult.Failed).error).isEqualTo(AttachError.UNREADABLE)
        assertThat(stored(s)).isEmpty()
    }

    @Test
    fun `cleanup deletes only unreferenced files, and keeps recent ones while an attach may be running`() {
        val s = store(graceMs = 0)
        val keep = (s.import(Uri.fromFile(file("keep.zip", 10))) as AttachResult.Ok).attachment.fileName
        val drop = (s.import(Uri.fromFile(file("drop.zip", 10))) as AttachResult.Ok).attachment.fileName
        // With a grace period, a just-written file is safe even if no note refers to it yet.
        assertThat(store(graceMs = 60_000).deleteUnreferenced(setOf(keep))).isEqualTo(0)
        s.file(drop).setLastModified(System.currentTimeMillis() - 1_000)
        s.file(keep).setLastModified(System.currentTimeMillis() - 1_000)
        assertThat(s.deleteUnreferenced(setOf(keep))).isEqualTo(1)
        assertThat(stored(s)).containsExactly(keep)
    }
}
