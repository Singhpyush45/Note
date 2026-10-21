package com.appname.notes.core.domain.search

import com.appname.notes.core.domain.model.SearchHit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchQueryTest {

    @Test
    fun `words become lower-case prefix terms`() {
        assertThat(SearchQuery.toMatchExpression("Chain Rule")).isEqualTo("chain* rule*")
    }

    @Test
    fun `fts syntax typed by the user is neutralised`() {
        assertThat(SearchQuery.toMatchExpression("\"cats\" OR -dogs NEAR(x) *")).isEqualTo("cats* or* dogs* near* x*")
    }

    @Test
    fun `nothing searchable gives null`() {
        assertThat(SearchQuery.toMatchExpression("  ?!  ")).isNull()
    }

    @Test
    fun `hindi words keep their vowel signs`() {
        assertThat(SearchQuery.toMatchExpression("नमस्ते दुनिया")).isEqualTo("नमस्ते* दुनिया*")
    }

    @Test
    fun `snippet highlights are parsed`() {
        val s = "…the ${SearchHit.HIGHLIGHT_START}chain${SearchHit.HIGHLIGHT_END} rule…"
        val h = Highlighted.parse(s)
        assertThat(h.text).isEqualTo("…the chain rule…")
        assertThat(h.ranges).containsExactly(5 until 10)
    }
}
