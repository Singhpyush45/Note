package com.appname.notes.core.common.time

import java.time.Instant

/** Injectable clock so timestamps are deterministic in tests. */
fun interface AppClock {
    fun now(): Instant
}

object SystemClock : AppClock {
    override fun now(): Instant = Instant.now()
}

/** Test clock you can move forward manually. */
class FixedClock(private var current: Instant) : AppClock {
    override fun now(): Instant = current

    fun advanceMillis(millis: Long) {
        current = current.plusMillis(millis)
    }
}
