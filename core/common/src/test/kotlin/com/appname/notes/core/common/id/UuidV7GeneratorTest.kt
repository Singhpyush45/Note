package com.appname.notes.core.common.id

import com.appname.notes.core.common.time.FixedClock
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.util.UUID
import org.junit.Test

class UuidV7GeneratorTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val generator = UuidV7Generator(clock)

    @Test
    fun `id is a valid version 7 RFC variant uuid`() {
        val uuid = UUID.fromString(generator.newId())
        assertThat(uuid.version()).isEqualTo(7)
        assertThat(uuid.variant()).isEqualTo(2) // RFC 4122/9562 variant "10"
    }

    @Test
    fun `timestamp is embedded and recoverable`() {
        val uuid = generator.generate()
        assertThat(UuidV7Generator.timestampMillis(uuid)).isEqualTo(clock.now().toEpochMilli())
    }

    @Test
    fun `ids created in later milliseconds sort later as strings`() {
        val first = generator.newId()
        clock.advanceMillis(1)
        val second = generator.newId()
        assertThat(second).isGreaterThan(first)
    }

    @Test
    fun `many ids in the same millisecond are unique`() {
        val ids = HashSet<String>()
        repeat(100_000) { ids += generator.newId() }
        assertThat(ids).hasSize(100_000)
    }

    @Test
    fun `string form is canonical 36 characters`() {
        val id = generator.newId()
        assertThat(id).hasLength(36)
        assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    }
}
