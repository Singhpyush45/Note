package com.appname.notes

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import com.appname.notes.core.ink.template.BuiltInTemplates
import com.appname.notes.core.ink.template.PageDesign
import com.appname.notes.core.ink.template.PageFormat
import com.appname.notes.feature.ink.ExportPage
import com.appname.notes.feature.ink.InkPdfWriter
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/** A handwritten note exported as PDF keeps each page's size, paper colour, design and ink (owner brief §5). */
@RunWith(AndroidJUnit4::class)
class InkPdfExportDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun pagesKeepTheirSizePaperAndInk() {
        val black = InkBrush(InkTool.PEN, 0xFF000000.toInt(), 6f)
        val line = StrokePoints(floatArrayOf(100f, 600f), floatArrayOf(500f, 500f), intArrayOf(0, 8))
        val cream = 0xFFFFF4E0.toInt()
        val landscape = PageFormat.A4_LANDSCAPE
        val pages = listOf(
            ExportPage(794f, 1123f, PageTemplate.BLANK, BuiltInTemplates.byId("planners.weekly")!!.layout, listOf(StrokeItem("s1", 1, black, line))),
            ExportPage(
                landscape.width, landscape.height, PageTemplate.GRID,
                PageDesign.withPaperColor(PageDesign.current(null, PageTemplate.GRID, landscape.width, landscape.height), cream),
                emptyList(),
            ),
        )
        val file = File(context.cacheDir, "export-test.pdf")
        file.outputStream().use { InkPdfWriter.write(pages, it) }

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                assertThat(pdf.pageCount).isEqualTo(2)
                pdf.openPage(0).use { p ->
                    assertThat(p.width).isEqualTo(595) // A4 portrait in points
                    assertThat(p.height).isEqualTo(842)
                    val bmp = Bitmap.createBitmap(794, 1123, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    assertThat(Color.red(bmp.getPixel(350, 500))).isLessThan(80) // the stroke
                    assertThat(Color.red(bmp.getPixel(780, 1110))).isGreaterThan(200) // paper
                }
                pdf.openPage(1).use { p ->
                    assertThat(p.width).isEqualTo(842) // landscape
                    assertThat(p.height).isEqualTo(595)
                    val bmp = Bitmap.createBitmap(1123, 794, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val px = bmp.getPixel(5, 5) // cream paper, between grid lines
                    assertThat(Color.blue(px)).isLessThan(Color.red(px) - 10)
                }
            }
        }
        file.delete()
    }
}
