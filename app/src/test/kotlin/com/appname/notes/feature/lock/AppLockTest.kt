package com.appname.notes.feature.lock

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLockTest {
    @Test
    fun `locks again only when on and away long enough`() {
        val on = LockSettings(enabled = true, after = LockAfter.ONE_MINUTE)
        assertThat(AppLock.shouldLock(on, awaySince = 0, now = 59_000)).isFalse()
        assertThat(AppLock.shouldLock(on, awaySince = 0, now = 60_000)).isTrue()
        assertThat(AppLock.shouldLock(on.copy(after = LockAfter.IMMEDIATELY), 0, 1)).isTrue()
        assertThat(AppLock.shouldLock(on.copy(enabled = false), 0, 999_999)).isFalse()
    }
}
