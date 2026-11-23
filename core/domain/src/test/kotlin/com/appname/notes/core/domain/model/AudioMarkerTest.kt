package com.appname.notes.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioMarkerTest {

    @Test
    fun `round trips through its link`() {
        val m = AudioMarker(AttachmentId("019a-xyz"), 760_400)
        assertThat(m.url).isEqualTo("audio:019a-xyz?t=760400")
        assertThat(AudioMarker.parse(m.url)).isEqualTo(m)
    }

    @Test
    fun `labels are minutes and seconds, hours when needed`() {
        assertThat(AudioMarker.formatDuration(0)).isEqualTo("0:00")
        assertThat(AudioMarker.formatDuration(760_999)).isEqualTo("12:40")
        assertThat(AudioMarker.formatDuration(3_723_000)).isEqualTo("1:02:03")
        assertThat(AudioMarker.formatDuration(-5)).isEqualTo("0:00")
    }

    @Test
    fun `other links and broken markers are not markers`() {
        assertThat(AudioMarker.parse("https://example.com")).isNull()
        assertThat(AudioMarker.parse("audio:?t=5")).isNull()
        assertThat(AudioMarker.parse("audio:abc")).isNull()
        assertThat(AudioMarker.parse("audio:abc?t=-1")).isNull()
        assertThat(AudioMarker.parse("audio:abc?t=x")).isNull()
    }
}
