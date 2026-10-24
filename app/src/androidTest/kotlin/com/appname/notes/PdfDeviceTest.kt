package com.appname.notes

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Debug
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.repository.InkPageContent
import com.appname.notes.core.ink.edit.InkEdit
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.Pt
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.pdf.PdfPageMapping
import com.appname.notes.di.AppDataEntryPoint
import com.appname.notes.feature.pdf.PdfFileStore
import com.appname.notes.feature.pdf.PdfImport
import com.appname.notes.feature.pdf.PdfImportError
import com.appname.notes.feature.pdf.PdfPageRenderer
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 4 on a real Android (emulator): import → store → render → annotate → export, the failure cases,
 * cleanup of unused files, and memory with a large PDF. Uses the app's real database and file store.
 * Traceability: PDF-01 (import), PDF-02/04 (export alignment), PDF-03 (pages scroll and render lazily),
 * PDF-05 (original never modified), PDF-06 (large PDF within memory budget).
 */
@RunWith(AndroidJUnit4::class)
class PdfDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val data = EntryPointAccessors.fromApplication(context.applicationContext as Application, AppDataEntryPoint::class.java)
    private val dir = File(context.cacheDir, "pdf-device-test").apply { mkdirs() }

    // ------------------------------------------------------------------ helpers

    /** A PDF with [pages] pages (A4 portrait, every third one landscape), each with a black square near its top-left. */
    private fun makePdf(name: String, pages: Int, protect: Boolean = false): File {
        data.pdfWriter() // initialises PdfBox
        val file = File(dir, name)
        PDDocument().use { doc ->
            repeat(pages) { i ->
                val page = PDPage(if (i % 3 == 2) PDRectangle(0f, 0f, 842f, 595f) else PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.setNonStrokingColor(0f, 0f, 0f)
                    cs.addRect(40f, page.mediaBox.height - 80f, 40f, 40f) // PDF y goes up: this is near the top
                    cs.fill()
                }
            }
            if (protect) doc.protect(StandardProtectionPolicy("owner-pw", "user-pw", AccessPermission()).apply { encryptionKeyLength = 128 })
            doc.save(file)
        }
        return file
    }

    private fun sha256(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    private fun import(file: File): PdfImport = runBlocking { data.pdfImporter().import(Uri.fromFile(file), null) }

    private fun created(file: File): NoteId = (import(file) as PdfImport.Created).noteId

    private fun pages(id: NoteId): List<InkPageContent> = runBlocking { (data.ink().loadPages(id) as AppResult.Success).value }

    private fun storedFile(id: NoteId): File {
        val doc = runBlocking { (data.pdf().document(id) as AppResult.Success).value!! }
        return data.pdfFiles().file(doc.fileName)
    }

    private fun render(file: File, page: Int, pxPerPoint: Int = 2): Bitmap =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(page).use { p ->
                    Bitmap.createBitmap(p.width * pxPerPoint, p.height * pxPerPoint, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }

    private fun partFiles() = data.pdfFiles().dir.listFiles().orEmpty().filter { it.name.endsWith(".part") }

    // ------------------------------------------------------------------ PDF-01, PDF-05

    @Test
    fun importMakesOnePageNoteSizedLikeThePdfAndStoresAnExactCopy() {
        val source = makePdf("three.pdf", pages = 3)
        val sha = sha256(source)
        val id = created(source)

        val doc = runBlocking { (data.pdf().document(id) as AppResult.Success).value!! }
        assertThat(doc.pageCount).isEqualTo(3)
        assertThat(doc.displayName).isEqualTo("three.pdf")
        assertThat(doc.sha256).isEqualTo(sha)
        assertThat(sha256(storedFile(id))).isEqualTo(sha) // byte-identical copy
        assertThat(sha256(source)).isEqualTo(sha) // the user's file is untouched (PDF-05)

        val p = pages(id)
        assertThat(p.map { it.info.pdfPageIndex }).containsExactly(0, 1, 2).inOrder()
        // A4 = 595 × 842 pt = 793.3 × 1122.7 units (1 unit = 0.75 pt); page 3 is landscape.
        assertThat(p[0].info.width.toDouble()).isWithin(0.5).of(595 / 0.75)
        assertThat(p[0].info.height.toDouble()).isWithin(0.5).of(842 / 0.75)
        assertThat(p[2].info.width).isGreaterThan(p[2].info.height)
        val note = runBlocking { data.notes().getNote(id)!! }
        assertThat(note.title).isEqualTo("three")
    }

    @Test
    fun badFilesAreRejectedWithAReasonAndLeaveNothingBehind() {
        val text = File(dir, "notes.pdf").apply { writeText("not really a PDF") }
        val empty = File(dir, "empty.pdf").apply { writeBytes(ByteArray(0)) }
        val locked = makePdf("locked.pdf", pages = 1, protect = true)
        assertThat((import(text) as PdfImport.Failed).error).isEqualTo(PdfImportError.NOT_A_PDF)
        assertThat((import(empty) as PdfImport.Failed).error).isEqualTo(PdfImportError.EMPTY)
        assertThat((import(locked) as PdfImport.Failed).error).isEqualTo(PdfImportError.PASSWORD_PROTECTED)
        assertThat(partFiles()).isEmpty()
    }

    // ------------------------------------------------------------------ PDF-02, PDF-04, PDF-05: annotate and export

    @Test
    fun inkSavedOnAPdfPageIsExportedAtTheSameSpotAndTheStoredOriginalStaysUnchanged() {
        val id = created(makePdf("annotate.pdf", pages = 3))
        val stored = storedFile(id)
        val before = sha256(stored)
        val page = pages(id)[2] // the landscape page
        val spot = Pt(page.info.width * 0.5f, page.info.height * 0.5f)
        val red = InkBrush(InkTool.MARKER, 0xFFFF0000.toInt(), 8f)
        val line = ShapeItem("line", 1, red, ShapeKind.LINE, ShapeGeometry.Segment(Pt(spot.x - 30, spot.y), Pt(spot.x + 30, spot.y)), null)
        runBlocking { assertThat(data.ink().commit(page.info.id, InkEdit(emptyList(), listOf(line)))).isInstanceOf(AppResult.Success::class.java) }

        // What the editor does on export: ink grouped by PDF page.
        val ink = pages(id).associate { it.info.pdfPageIndex!! to it.items }
        val out = File(dir, "annotated.pdf")
        out.outputStream().use { data.pdfWriter().writeAnnotatedCopy(stored, ink, it) }

        assertThat(sha256(stored)).isEqualTo(before)
        val rendered = render(out, 2)
        val x = (spot.x * PdfPageMapping.POINTS_PER_UNIT * 2).roundToInt()
        val y = (spot.y * PdfPageMapping.POINTS_PER_UNIT * 2).roundToInt()
        val p = rendered.getPixel(x, y)
        assertWithMessage("ink at $x,$y is ${Integer.toHexString(p)}").that(Color.red(p)).isGreaterThan(200)
        assertThat(Color.green(p)).isLessThan(60)
        // The other pages have no ink and still show the PDF's own content.
        val first = render(out, 0)
        assertThat(first.getPixel(120, 120) and 0xFFFFFF).isEqualTo(0) // the black square (40..80 pt from the top-left, ×2)
    }

    // ------------------------------------------------------------------ storage cleanup

    @Test
    fun unusedFilesAreDeletedButSharedAndRecentOnesAreKept() {
        val a = created(makePdf("a.pdf", pages = 1))
        val copy = runBlocking { (data.notes().duplicate(a) as AppResult.Success).value }
        assertThat(storedFile(copy)).isEqualTo(storedFile(a)) // the copy shares the file
        val b = created(makePdf("b.pdf", pages = 1))
        val fileA = storedFile(a)
        val fileB = storedFile(b)

        runBlocking {
            data.notes().deletePermanently(setOf(a))
            data.notes().deletePermanently(setOf(b))
        }
        // The app's cleanup right after: both files are recent, so both stay (they might belong to an import in progress).
        runBlocking { data.pdfImporter().cleanUp() }
        assertThat(fileA.exists()).isTrue()
        assertThat(fileB.exists()).isTrue()

        // A later cleanup (here: no grace period): b's file goes, a's stays because the copy still uses it.
        val store = PdfFileStore(context, data.pdfWriter(), NoOpLogger, importGraceMs = 0)
        val referenced = runBlocking { (data.pdf().referencedFiles() as AppResult.Success).value }
        store.deleteUnreferenced(referenced)
        assertThat(fileA.exists()).isTrue()
        assertThat(fileB.exists()).isFalse()
    }

    // ------------------------------------------------------------------ PDF-03, PDF-06: rendering and memory

    /**
     * Measures the renderer alone: background text indexing (which loads OCR models and renders pages of its own)
     * is turned off for this test and any run already going is waited for.
     */
    private fun withoutBackgroundIndexing(block: () -> Unit) {
        runBlocking { data.settings().setDocumentText(false) }
        val work = androidx.work.WorkManager.getInstance(context)
        val deadline = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < deadline &&
            work.getWorkInfosForUniqueWork(com.appname.notes.feature.textindex.TextIndexScheduler.WORK_NAME).get().any { !it.state.isFinished }
        ) {
            Thread.sleep(250)
        }
        try {
            block()
        } finally {
            runBlocking { data.settings().setDocumentText(true) }
        }
    }

    @Test
    fun largePdfRendersPageByPageWithinTheMemoryBudget() = withoutBackgroundIndexing {
        val source = makePdf("large.pdf", pages = 150)
        val id = created(source)
        val file = storedFile(id)
        lateinit var renderer: PdfPageRenderer
        instrumentation.runOnMainSync { renderer = PdfPageRenderer(context, file, NoOpLogger) } // it's used from the main thread
        val budget = (Runtime.getRuntime().maxMemory() / 6).coerceAtMost(128L * 1024 * 1024)
        val nativeBefore = Debug.getNativeHeapAllocatedSize()
        var maxCached = 0L
        try {
            // Like a user scrolling through every page on a large tablet: each page wanted at 2560 px.
            for (page in 0 until 150) {
                val aspect = if (page % 3 == 2) 595f / 842f else 842f / 595f
                val latch = CountDownLatch(1)
                var bitmap: Bitmap? = null
                instrumentation.runOnMainSync {
                    renderer.onUpdate = { renderer.page(page, 2560, aspect)?.takeIf { it.width > 1000 || it.height > 1000 }?.let { bitmap = it; latch.countDown() } }
                    bitmap = renderer.page(page, 2560, aspect)
                    if (bitmap != null) latch.countDown()
                }
                assertWithMessage("page $page rendered").that(latch.await(20, TimeUnit.SECONDS)).isTrue()
                instrumentation.runOnMainSync { maxCached = maxOf(maxCached, renderer.cachedBytes.toLong()) }
            }
            assertThat(renderer.failed).isFalse()
            assertWithMessage("cache stays within its budget").that(maxCached).isAtMost(budget)
            // Bitmap pixels live in the native heap: growth stays near the cache budget, not 150 pages' worth.
            // (Evicted bitmaps are freed when collected, so collect first.)
            System.gc()
            Runtime.getRuntime().runFinalization()
            System.gc()
            val grown = Debug.getNativeHeapAllocatedSize() - nativeBefore
            assertWithMessage("native heap grew ${grown / 1_048_576} MB").that(grown).isLessThan(budget + 64L * 1024 * 1024)

            // Zoomed far in: a sharp tile of the visible part is rendered at screen resolution.
            val tileLatch = CountDownLatch(1)
            instrumentation.runOnMainSync {
                assertThat(renderer.needsDetail(8000)).isTrue()
                renderer.onUpdate = { if (renderer.tile(0) != null) tileLatch.countDown() }
                renderer.requestTile(0, android.graphics.RectF(0f, 0f, 200f, 300f), 4f, 800, 1200)
            }
            assertThat(tileLatch.await(20, TimeUnit.SECONDS)).isTrue()
            instrumentation.runOnMainSync {
                val t = renderer.tile(0)!!
                assertThat(t.bitmap.width).isEqualTo(800)
                // The black square (40..80 pt = 53..107 units from the top-left) shows at 4 px per unit.
                assertThat(t.bitmap.getPixel(320, 320) and 0xFFFFFF).isEqualTo(0)
            }
        } finally {
            instrumentation.runOnMainSync { renderer.close() }
        }
    }

    @Test
    fun missingFileIsReportedNotCrashed() {
        val latch = CountDownLatch(1)
        lateinit var renderer: PdfPageRenderer
        // Created and listened to in one main-thread step, so the failure can't be reported before we listen.
        instrumentation.runOnMainSync {
            renderer = PdfPageRenderer(context, File(dir, "does-not-exist.pdf"), NoOpLogger)
            renderer.onFailed = { latch.countDown() }
        }
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
            instrumentation.runOnMainSync {
                assertThat(renderer.failed).isTrue()
                assertThat(renderer.page(0, 1000, 1.4f)).isNull()
            }
        } finally {
            instrumentation.runOnMainSync { renderer.close() }
        }
    }
}
