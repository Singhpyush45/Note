package com.appname.notes.core.common.id

import com.appname.notes.core.common.time.AppClock
import java.security.SecureRandom
import java.util.UUID

/** Creates stable entity IDs (SRS 9.2: all records require stable IDs). */
fun interface IdGenerator {
    fun newId(): String
}

/**
 * UUID version 7 (RFC 9562): the first 48 bits are the Unix time in milliseconds, the rest is random.
 *
 * Why v7 instead of random v4: IDs created later sort later, which keeps database indexes compact and
 * makes sync logs easier to read. IDs are still globally unique, so two offline devices never collide.
 *
 * Bit layout (128 bits):
 * ```
 * | unix_ts_ms (48) | ver=0111 (4) | rand_a (12) | var=10 (2) | rand_b (62) |
 * ```
 */
class UuidV7Generator(
    private val clock: AppClock,
    private val random: java.util.Random = SecureRandom(),
) : IdGenerator {

    override fun newId(): String = generate().toString()

    fun generate(): UUID {
        val millis = clock.now().toEpochMilli() and TIMESTAMP_MASK
        val randA = random.nextInt() and 0xFFF // 12 random bits
        val randB = random.nextLong() and RAND_B_MASK // 62 random bits

        val mostSignificant = (millis shl 16) or (VERSION_7 shl 12) or randA.toLong()
        val leastSignificant = VARIANT_RFC_9562 or randB
        return UUID(mostSignificant, leastSignificant)
    }

    companion object {
        private const val TIMESTAMP_MASK = 0xFFFF_FFFF_FFFFL // 48 bits
        private const val VERSION_7 = 0x7L
        private const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL // 62 bits
        private const val VARIANT_RFC_9562 = Long.MIN_VALUE // top two bits = 10

        /** Reads the millisecond timestamp back out of a v7 UUID. */
        fun timestampMillis(uuid: UUID): Long = uuid.mostSignificantBits ushr 16
    }
}
