package com.appname.notes

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.AttachmentId
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.core.domain.model.ExtractionMethod
import com.appname.notes.core.domain.model.IndexStatus
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.SearchFilters
import com.appname.notes.core.domain.model.TextSource
import com.appname.notes.di.AppDataEntryPoint
import com.appname.notes.feature.attachments.AttachmentFileStore
import com.appname.notes.feature.pdf.PdfImport
import com.appname.notes.feature.textindex.DocumentTextExtractor
import com.appname.notes.feature.textindex.MlKitTextRecognizer
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FR-011 / SRS Journey 4 on a real Android: text is read from PDFs (text layer) and from pictures (on-device
 * OCR with the bundled ML Kit models, Latin and Devanagari), then found by search with its page.
 */
@RunWith(AndroidJUnit4::class)
class DocumentTextDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val data = EntryPointAccessors.fromApplication(context.applicationContext as Application, AppDataEntryPoint::class.java)
    private val dir = File(context.cacheDir, "document-text-test").apply { mkdirs() }

    private val attachmentStore = AttachmentFileStore(context, NoOpLogger)
    private val extractor = DocumentTextExtractor(attachmentStore, data.pdfFiles(), MlKitTextRecognizer(), data.pdfWriter(), NoOpLogger)

    /** Two A4 pages with real text (a text layer), the second one about cells. */
    private fun textPdf(name: String): File {
        data.pdfWriter() // initialises PdfBox
        val f = File(dir, name)
        PDDocument().use { doc ->
            listOf("Chapter one: plants and light", "Mitochondria are the powerhouse of the cell").forEach { line ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 18f)
                    cs.newLineAtOffset(60f, 700f)
                    cs.showText(line)
                    cs.endText()
                }
            }
            doc.save(f)
        }
        return f
    }

    /** A picture of [text] in large print on white, like a photo of a whiteboard or a printed page. */
    private fun textPicture(text: String, typeface: Typeface = Typeface.DEFAULT_BOLD): Bitmap {
        val bmp = Bitmap.createBitmap(1400, 500, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 96f; this.typeface = typeface }
        canvas.drawText(text, 60f, 280f, paint)
        return bmp
    }

    /** A one-page PDF whose page is only a picture (no text layer), like a scan. */
    private fun scannedPdf(name: String, text: String): File {
        val f = File(dir, name)
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawBitmap(textPicture(text), null, android.graphics.RectF(20f, 100f, 575f, 300f), null)
        doc.finishPage(page)
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    private fun jpeg(name: String, bmp: Bitmap): File = File(dir, name).also { f ->
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    }

    private fun importAttachment(file: File) =
        (attachmentStore.import(Uri.fromFile(file)) as com.appname.notes.feature.attachments.AttachResult.Ok).attachment

    private fun source(fileName: String, kind: AttachmentKind) = TextSource.AttachmentSource(AttachmentId("x"), NoteId("n"), kind, fileName)

    @Test
    fun pdfTextLayerIsReadExactlyPerPage() {
        val a = importAttachment(textPdf("Biology.pdf"))
        val r = extractor.extract(source(a.fileName, AttachmentKind.PDF))
        assertThat(r.status).isEqualTo(IndexStatus.DONE)
        assertThat(r.pages.map { it.page }).containsExactly(0, 1).inOrder()
        assertThat(r.pages.all { it.method == ExtractionMethod.PDF_TEXT }).isTrue()
        assertThat(r.pages[1].text).contains("Mitochondria are the powerhouse of the cell")
    }

    @Test
    fun scannedPdfPageWithoutTextLayerIsReadWithOcr() {
        val a = importAttachment(scannedPdf("Scan.pdf", "PHOTOSYNTHESIS"))
        val r = extractor.extract(source(a.fileName, AttachmentKind.PDF))
        val page = r.pages.single()
        assertThat(page.method).isEqualTo(ExtractionMethod.OCR)
        assertWithMessage("OCR read: ${page.text}").that(page.text.uppercase()).contains("PHOTOSYNTHESIS")
    }

    @Test
    fun photoOfEnglishTextIsRead() {
        val a = importAttachment(jpeg("board.jpg", textPicture("Exam on Friday")))
        val r = extractor.extract(source(a.fileName, AttachmentKind.IMAGE))
        assertThat(r.status).isEqualTo(IndexStatus.DONE)
        assertWithMessage("OCR read: ${r.pages.single().text}").that(r.pages.single().text.lowercase()).contains("friday")
    }

    @Test
    fun photoOfHindiTextIsRead() {
        val a = importAttachment(jpeg("hindi.jpg", textPicture("नमस्ते भारत", Typeface.DEFAULT)))
        val r = extractor.extract(source(a.fileName, AttachmentKind.IMAGE))
        val text = r.pages.singleOrNull()?.text.orEmpty()
        assertWithMessage("OCR read: '$text'").that(text).contains("भारत")
    }

    @Test
    fun importedPdfBecomesSearchableWithItsPage() = runBlocking {
        val id = (data.pdfImporter().import(Uri.fromFile(textPdf("Cell biology.pdf")), null) as PdfImport.Created).noteId
        data.textIndexer().run()
        val hits = data.notes().search("mitochondria", SearchFilters()).first().filter { it.note.id == id }
        assertThat(hits).hasSize(1)
        assertThat(hits.single().location!!.page).isEqualTo(1)
        assertThat((data.textIndex().progress() as AppResult.Success).value.first).isAtLeast(1)
    }
}
