package com.appname.notes

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.Pt
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.pdf.PdfPageBox
import com.appname.notes.core.ink.pdf.PdfPageMapping
import com.appname.notes.feature.pdf.PdfAnnotationWriter
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The PDF spike (ADR-0009 Part B), judged by an independent renderer: Android's PdfRenderer (PDFium).
 *
 * For page sizes, all four rotations and an offset crop box:
 * 1. our inverse mapping finds a marker that PdfBox drew in PDF user space at the pixel PDFium shows it;
 * 2. ink exported at a point in our page units shows up at that pixel after export (PDF-02/PDF-04);
 * 3. the source file is byte-identical afterwards (PDF-05).
 */
@RunWith(AndroidJUnit4::class)
class PdfMappingDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "pdf-spike").apply { mkdirs() }

    private data class Case(val name: String, val media: PDRectangle, val crop: PDRectangle?, val rotation: Int)

    private val cases = listOf(
        Case("a4", PDRectangle(0f, 0f, 595f, 842f), null, 0),
        Case("letter-landscape", PDRectangle(0f, 0f, 792f, 612f), null, 0),
        Case("a4-rot90", PDRectangle(0f, 0f, 595f, 842f), null, 90),
        Case("a4-rot180", PDRectangle(0f, 0f, 595f, 842f), null, 180),
        Case("a4-rot270", PDRectangle(0f, 0f, 595f, 842f), null, 270),
        Case("crop-offset-rot90", PDRectangle(0f, 0f, 700f, 900f), PDRectangle(50f, 30f, 595f, 842f), 90),
    )

    /** An asymmetric spot (20 % across, 70 % up) so a wrong rotation can't land on it by symmetry. */
    private fun markerCenter(c: Case): Vec {
        val b = c.crop ?: c.media
        return Vec((b.lowerLeftX + 0.2 * b.width), (b.lowerLeftY + 0.7 * b.height))
    }

    /** One PDF with a page per case, each with a black 16 pt square at its marker spot. */
    private fun makeSource(): File {
        val file = File(dir, "source.pdf")
        PDDocument().use { doc ->
            for (c in cases) {
                val page = PDPage(c.media)
                c.crop?.let { page.cropBox = it }
                page.rotation = c.rotation
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    val m = markerCenter(c)
                    cs.setNonStrokingColor(0f, 0f, 0f)
                    cs.addRect((m.x - 8).toFloat(), (m.y - 8).toFloat(), 16f, 16f)
                    cs.fill()
                }
            }
            doc.save(file)
        }
        return file
    }

    private fun boxOf(c: Case): PdfPageBox {
        val b = c.crop ?: c.media
        return PdfPageBox(b.lowerLeftX.toDouble(), b.lowerLeftY.toDouble(), b.upperRightX.toDouble(), b.upperRightY.toDouble(), c.rotation)
    }

    /** Renders every page at 2 px per point on white, like a viewer would. */
    private fun render(file: File): List<Bitmap> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                List(r.pageCount) { i ->
                    r.openPage(i).use { page ->
                        Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }

    /** Pixel position of a point in our page units, at 2 px per point. */
    private fun pixel(units: Vec) = (units.x * PdfPageMapping.POINTS_PER_UNIT * 2).roundToInt() to (units.y * PdfPageMapping.POINTS_PER_UNIT * 2).roundToInt()

    private fun sha256(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    @Test
    fun pageSizesRotationAndMarkersMatchPdfium() {
        val writer = PdfAnnotationWriter(context) // initialises PdfBox first
        val source = makeSource()
        val boxes = writer.readPageBoxes(source)
        val pages = render(source)
        cases.forEachIndexed { i, c ->
            val box = boxes[i]
            assertWithMessage(c.name).that(box).isEqualTo(boxOf(c))
            // Displayed size (rotation swaps width and height) matches what PDFium reports, to a point.
            val (w, h) = PdfPageMapping.displayedSize(box)
            assertWithMessage("${c.name} width").that(abs(w * 0.75 - pages[i].width / 2.0)).isLessThan(1.0)
            assertWithMessage("${c.name} height").that(abs(h * 0.75 - pages[i].height / 2.0)).isLessThan(1.0)
            // The marker is where our mapping says.
            val (x, y) = pixel(PdfPageMapping.toUnits(box, markerCenter(c)))
            assertWithMessage("${c.name}: marker at $x,$y").that(pages[i].getPixel(x, y) and 0xFFFFFF).isEqualTo(0)
        }
    }

    @Test
    fun exportedInkLandsWhereItWasDrawnAndTheOriginalIsUntouched() {
        val writer = PdfAnnotationWriter(context)
        val source = makeSource()
        val before = sha256(source)
        val boxes = writer.readPageBoxes(source)
        val red = InkBrush(InkTool.MARKER, 0xFFFF0000.toInt(), 8f)
        // On each page: a short thick red line at 30 % across, 20 % down the displayed page (in page units).
        val spots = boxes.map { b -> PdfPageMapping.displayedSize(b).let { (w, h) -> Vec(0.3 * w, 0.2 * h) } }
        val ink = boxes.indices.associateWith { i ->
            val s = spots[i]
            listOf(ShapeItem("l$i", 1, red, ShapeKind.LINE, ShapeGeometry.Segment(Pt((s.x - 20).toFloat(), s.y.toFloat()), Pt((s.x + 20).toFloat(), s.y.toFloat())), null))
        }
        val out = File(dir, "annotated.pdf")
        out.outputStream().use { writer.writeAnnotatedCopy(source, ink, it) }

        assertThat(sha256(source)).isEqualTo(before) // PDF-05
        val pages = render(out)
        assertThat(pages).hasSize(cases.size)
        cases.forEachIndexed { i, c ->
            val (x, y) = pixel(spots[i])
            val p = pages[i].getPixel(x, y)
            assertWithMessage("${c.name}: ink at $x,$y is ${Integer.toHexString(p)}").that(Color.red(p)).isGreaterThan(200)
            assertWithMessage("${c.name}: ink colour").that(Color.green(p)).isLessThan(60)
            // The original marker is still there (export adds, never replaces).
            val (mx, my) = pixel(PdfPageMapping.toUnits(boxes[i], markerCenter(c)))
            assertWithMessage("${c.name}: marker kept").that(pages[i].getPixel(mx, my) and 0xFFFFFF).isEqualTo(0)
        }
    }

    @Test
    fun translucentHighlighterStaysTranslucentInTheExport() {
        val writer = PdfAnnotationWriter(context)
        val source = makeSource()
        val box = writer.readPageBoxes(source)[0]
        val marker = PdfPageMapping.toUnits(box, markerCenter(cases[0]))
        // A yellow highlighter across the black marker and the white page next to it.
        val hl = ShapeItem(
            "h", 1, InkBrush(InkTool.HIGHLIGHTER, 0x66FFEB3B, 30f), ShapeKind.LINE,
            ShapeGeometry.Segment(Pt((marker.x - 60).toFloat(), marker.y.toFloat()), Pt((marker.x + 60).toFloat(), marker.y.toFloat())), null,
        )
        val out = File(dir, "highlight.pdf")
        out.outputStream().use { writer.writeAnnotatedCopy(source, mapOf(0 to listOf(hl)), it) }
        val page = render(out)[0]
        val (mx, my) = pixel(marker)
        val (px, py) = pixel(Vec(marker.x + 40, marker.y))
        // Over the marker it stays dark (the text shows through); on paper it is light yellow, not opaque.
        assertThat(Color.red(page.getPixel(mx, my))).isLessThan(130) // 40 % yellow over black ≈ 102
        val paper = page.getPixel(px, py)
        assertThat(Color.blue(paper)).isIn(100..230) // tinted, but not solid yellow (blue 0x3B) nor white
        assertThat(Color.red(paper)).isGreaterThan(230)
    }
}
