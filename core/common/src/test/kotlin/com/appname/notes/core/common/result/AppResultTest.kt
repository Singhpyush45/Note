package com.appname.notes.core.common.result

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppResultTest {

    private val failure: AppResult<Int> = AppResult.Failure(DomainError.NotFound("Note", "n1"))

    @Test
    fun `map transforms success and passes failure through`() {
        assertThat(AppResult.Success(2).map { it * 10 }).isEqualTo(AppResult.Success(20))
        assertThat(failure.map { it * 10 }).isEqualTo(failure)
    }

    @Test
    fun `flatMap chains results and stops at first failure`() {
        val chained = AppResult.Success(2).flatMap { AppResult.Success(it + 1) }
        assertThat(chained.getOrNull()).isEqualTo(3)
        var called = false
        failure.flatMap { called = true; AppResult.Success(it) }
        assertThat(called).isFalse()
    }

    @Test
    fun `callbacks run only for the matching case`() {
        var successSeen: Int? = null
        var errorSeen: DomainError? = null
        AppResult.Success(5).onSuccess { successSeen = it }.onFailure { errorSeen = it }
        assertThat(successSeen).isEqualTo(5)
        assertThat(errorSeen).isNull()

        failure.onSuccess { successSeen = -1 }.onFailure { errorSeen = it }
        assertThat(successSeen).isEqualTo(5)
        assertThat(errorSeen).isEqualTo(DomainError.NotFound("Note", "n1"))
    }

    @Test
    fun `accessors return null for the other case`() {
        assertThat(failure.getOrNull()).isNull()
        assertThat(AppResult.Success(1).errorOrNull()).isNull()
        assertThat(failure.errorOrNull()).isInstanceOf(DomainError.NotFound::class.java)
    }
}
