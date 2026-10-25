package com.appname.notes.core.ink.model

import com.appname.notes.core.ink.codec.DecodeResult
import com.appname.notes.core.ink.codec.InkCodec
import com.appname.notes.core.ink.edit.Eraser
import com.appname.notes.core.ink.edit.Lasso
import com.appname.notes.core.ink.edit.Similarity
import com.appname.notes.core.ink.edit.flipped
import com.appname.notes.core.ink.edit.transformed
import com.appname.notes.core.ink.geometry.Vec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Photos and text boxes on handwritten pages (owner request 8a). */
class PageObjectsTest {

    private val photo = ImageItem("i", 3, "abc.jpg", Placement(200f, 150f, 160f, 100f, 15f))
    private val text = TextItem("t", 4, InkBrush(InkTool.PEN, 0xFF1A73E8.toInt(), 22f), "Hello\nशब्द ✓", Placement(100f, 80f, 180f, 60f), TextFont.CURSIVE, bold = true, align = TextAlign.CENTER)

    @Test
    fun `photos and text are stored in format 3 and read back exactly`() {
        val audio = AudioItem("a", 5, "att-1", "rec.aac", "Lecture recording", 754_000, Placement(150f, 60f, 300f, 64f))
        for (item in listOf(photo, photo.copy(flipped = true), text, audio)) {
            val bytes = InkCodec.encode(item)
            assertEquals(3, bytes[0].toInt())
            assertEquals(item, (InkCodec.decode(item.id, item.z, bytes) as DecodeResult.Ok).item)
        }
        // Ink without them stays in the older formats.
        val pen = StrokeItem("s", 1, InkBrush(InkTool.PEN, -16777216, 2f), StrokePoints(floatArrayOf(0f, 5f), floatArrayOf(0f, 5f), intArrayOf(0, 8)))
        assertEquals(1, InkCodec.encode(pen)[0].toInt())
    }

    @Test
    fun `a font from a newer version opens read-only, damaged data is caught`() {
        val bytes = InkCodec.encode(text)
        // version, type, tool, colour (4), size (4), style, flags, text length (1) + text, placement (20), then the font.
        val textLen = "Hello\nशब्द ✓".toByteArray().size
        val fontAt = 13 + 1 + textLen + 20
        bytes[fontAt] = 77
        assertTrue(InkCodec.decode("t", 4, bytes) is DecodeResult.NewerVersion)
        assertTrue(InkCodec.decode("i", 3, InkCodec.encode(photo).copyOf(20)) is DecodeResult.Corrupt)
    }

    @Test
    fun `boxes move, scale and turn like ink, and text size scales`() {
        val t = Similarity(dx = 10.0, scale = 2.0, rotationDeg = 90.0, pivot = Vec(200.0, 150.0))
        val moved = photo.transformed(t) as ImageItem
        assertEquals(210f, moved.place.cx, 1e-3f)
        assertEquals(320f, moved.place.width, 1e-3f)
        assertEquals(105f, moved.place.rotationDeg, 1e-3f)
        val big = text.transformed(Similarity(scale = 1.5, pivot = Vec(0.0, 0.0))) as TextItem
        assertEquals(33f, big.brush.size, 1e-3f)
        assertEquals(270f, big.place.width, 1e-3f)
        // Flip: the photo mirrors, text stays readable but moves.
        val f = photo.flipped(horizontal = true, axis = 300.0) as ImageItem
        assertTrue(f.flipped)
        assertEquals(400f, f.place.cx, 1e-3f)
        assertEquals(345f, f.place.rotationDeg, 1e-3f)
        val ft = text.flipped(horizontal = true, axis = 300.0) as TextItem
        assertEquals(500f, ft.place.cx, 1e-3f)
    }

    @Test
    fun `the eraser leaves photos and text, the lasso picks them up`() {
        val path = listOf(Vec(100.0, 80.0), Vec(200.0, 150.0))
        assertFalse(Eraser.touches(photo, path, 20.0))
        assertFalse(Eraser.touches(text, path, 20.0))
        val lasso = listOf(Vec(0.0, 0.0), Vec(400.0, 0.0), Vec(400.0, 300.0), Vec(0.0, 300.0), Vec(0.0, 1.0))
        assertEquals(setOf("i", "t"), Lasso.select(listOf(photo, text), lasso).map { it.id }.toSet())
        assertTrue(photo.place.contains(Vec(200.0, 150.0)))
        assertFalse(photo.place.contains(Vec(400.0, 400.0)))
    }
}
