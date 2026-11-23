package com.appname.notes.feature.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebClipperTest {
    @Test
    fun `reads title, summary and picture from link-preview tags`() {
        val html = """
            <html><head><title>Fallback</title>
            <meta property="og:title" content="Waves &amp; Optics">
            <meta name="description" content="Notes on   light.">
            <meta property="og:image" content="/img/cover.png">
            </head><body>…</body></html>
        """.trimIndent()
        val clip = WebClipper.parse(html, "https://example.com/physics/waves")!!
        assertThat(clip.title).isEqualTo("Waves & Optics")
        assertThat(clip.description).isEqualTo("Notes on light.")
        assertThat(clip.imageUrl).isEqualTo("https://example.com/img/cover.png")
        assertThat(WebClipper.parse("<html><body>no title</body></html>", "https://x.org")).isNull()
    }
}
