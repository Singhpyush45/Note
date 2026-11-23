package com.appname.notes.feature.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LinkUrlTest {
    @Test
    fun `domains get https and invalid input is rejected`() {
        assertThat(normalizeUrl("example.com")).isEqualTo("https://example.com")
        assertThat(normalizeUrl("http://a.b/c?d=1")).isEqualTo("http://a.b/c?d=1")
        assertThat(normalizeUrl("mailto:me@x.y")).isEqualTo("mailto:me@x.y")
        assertThat(normalizeUrl("hello world")).isNull()
        assertThat(normalizeUrl("javascript:alert(1)")).isNull()
        assertThat(normalizeUrl("localhost")).isNull()
        assertThat(normalizeUrl("")).isNull()
    }
}
