package com.appname.notes.core.database

import android.database.SQLException
import android.database.sqlite.SQLiteFullException
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Runs a database call and turns storage failures into typed errors instead of crashes (SRS B.2, 15.1). */
internal suspend fun <T> storageCall(logger: AppLogger, what: String, block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: SQLiteFullException) {
    logger.error(TAG, "$what failed: storage full", e)
    AppResult.Failure(DomainError.Storage(lowSpace = true))
} catch (e: SQLException) {
    logger.error(TAG, "$what failed", e)
    AppResult.Failure(DomainError.Storage())
} catch (e: IOException) {
    logger.error(TAG, "$what failed", e)
    AppResult.Failure(DomainError.Storage())
} catch (e: IllegalStateException) {
    // Room throws this for e.g. a closed database.
    logger.error(TAG, "$what failed", e)
    AppResult.Failure(DomainError.Storage())
}

private const val TAG = "Storage"
