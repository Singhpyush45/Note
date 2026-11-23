package com.appname.notes.feature.ink

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PhotoAndPreviewRegressionTest {

    @Test
    fun `fallback background cutout preserves interior subject RGB and opacity intact`() {
        val width = 80
        val height = 80
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bgColor = Color.rgb(245, 245, 245)
        val subjectOuter = Color.rgb(34, 120, 210)
        // Interior pixel with light colour similar to background (e.g. white shirt or highlight inside subject)
        val subjectInteriorLight = Color.rgb(244, 244, 244)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = when {
                    x in 28..52 && y in 28..52 -> subjectInteriorLight
                    x in 20..60 && y in 20..60 -> subjectOuter
                    else -> bgColor
                }
                bmp.setPixel(x, y, color)
            }
        }

        val cutout = BackgroundRemover.cutOutOnDeviceFallback(bmp)
        assertThat(cutout).isNotNull()
        val result = cutout!!

        // Border background is removed (transparent).
        assertThat(Color.alpha(result.getPixel(2, 2))).isEqualTo(0)
        assertThat(Color.alpha(result.getPixel(width - 3, height - 3))).isEqualTo(0)

        // Interior subject pixels (including light interior pixels matching the border colour)
        // remain 100% opaque and keep their exact original RGB values.
        val centerPixel = result.getPixel(40, 40)
        assertThat(Color.alpha(centerPixel)).isEqualTo(255)
        assertThat(centerPixel).isEqualTo(subjectInteriorLight)

        val midSubjectPixel = result.getPixel(24, 24)
        assertThat(Color.alpha(midSubjectPixel)).isEqualTo(255)
        assertThat(midSubjectPixel).isEqualTo(subjectOuter)
    }

    @Test
    fun `fallback background cutout returns null when image has no removable border background`() {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(180, 90, 40))

        // Uniform photo where center and border are identical must not erase the subject.
        val cutout = BackgroundRemover.cutOutOnDeviceFallback(bmp)
        assertThat(cutout).isNull()
    }
}
