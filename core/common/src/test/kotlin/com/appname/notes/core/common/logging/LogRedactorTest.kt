package com.appname.notes.core.common.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LogRedactorTest {

    private fun redact(text: String) = LogRedactor.redact(text)

    @Test
    fun `plain technical text is unchanged`() {
        val text = "sync finished in 120 ms, 3 operations, result=OK"
        assertThat(redact(text)).isEqualTo(text)
    }

    @Test
    fun `email addresses are masked`() {
        val out = redact("signed in as rahul.singh+notes@example.co.in today")
        assertThat(out).doesNotContain("rahul")
        assertThat(out).doesNotContain("example")
        assertThat(out).contains(LogRedactor.MASK)
        assertThat(out).endsWith(" today")
    }

    @Test
    fun `bearer tokens are masked`() {
        val out = redact("header Bearer abcDEF123.xyz-_~+/=")
        assertThat(out).isEqualTo("header Bearer ${LogRedactor.MASK}")
    }

    @Test
    fun `jwt is masked`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        val out = redact("token payload $jwt end")
        assertThat(out).doesNotContain("eyJ")
        assertThat(out).endsWith(" end")
    }

    @Test
    fun `sensitive key value pairs are masked in several syntaxes`() {
        val inputs = listOf(
            "password=hunter2",
            "api_key: sk-live-123abc",
            "apiKey=sk-live-123abc",
            "\"refresh_token\":\"r1-secret\"",
            "SECRET = topsecret",
        )
        for (input in inputs) {
            val out = redact(input)
            assertThat(out).contains(LogRedactor.MASK)
            assertThat(out).doesNotContain("hunter2")
            assertThat(out).doesNotContain("sk-live")
            assertThat(out).doesNotContain("r1-secret")
            assertThat(out).doesNotContain("topsecret")
        }
    }

    @Test
    fun `authorization header leaves no token behind`() {
        val out = redact("Authorization: Bearer abc.def.ghi")
        assertThat(out).doesNotContain("abc")
    }

    @Test
    fun `long digit runs are masked even with separators`() {
        assertThat(redact("card 4111 1111 1111 1111 used")).isEqualTo("card ${LogRedactor.MASK} used")
        assertThat(redact("acct 123456789012")).isEqualTo("acct ${LogRedactor.MASK}")
    }

    @Test
    fun `short numbers are kept`() {
        assertThat(redact("page 12 of 340, retry 3")).isEqualTo("page 12 of 340, retry 3")
    }
}
