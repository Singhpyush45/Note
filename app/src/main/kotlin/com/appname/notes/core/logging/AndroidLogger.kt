package com.appname.notes.core.logging

import android.util.Log
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.logging.LogRedactor

/**
 * Logcat-backed [AppLogger]. Messages *and* stack traces are redacted, because exception messages can
 * contain user data too (ADR-0006).
 *
 * @param minPriority lowest level written, e.g. [Log.DEBUG] in debug builds and [Log.WARN] in release.
 */
class AndroidLogger(private val minPriority: Int) : AppLogger {

    override fun debug(tag: String, message: String) = write(Log.DEBUG, tag, message, null)

    override fun info(tag: String, message: String) = write(Log.INFO, tag, message, null)

    override fun warn(tag: String, message: String, throwable: Throwable?) =
        write(Log.WARN, tag, message, throwable)

    override fun error(tag: String, message: String, throwable: Throwable?) =
        write(Log.ERROR, tag, message, throwable)

    private fun write(priority: Int, tag: String, message: String, throwable: Throwable?) {
        if (priority < minPriority) return
        val full = if (throwable == null) message else "$message\n${Log.getStackTraceString(throwable)}"
        Log.println(priority, tag, LogRedactor.redact(full))
    }
}
