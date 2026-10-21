package com.appname.notes.core.common.logging

/**
 * The only way app code should log (ADR-0006). Implementations must pass every message through
 * [LogRedactor] before writing it anywhere.
 *
 * Policy (SRS 15.2): never log note bodies, handwriting points, filenames or OCR text.
 */
interface AppLogger {
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}

/** Logger that drops everything; handy default for tests. */
object NoOpLogger : AppLogger {
    override fun debug(tag: String, message: String) = Unit
    override fun info(tag: String, message: String) = Unit
    override fun warn(tag: String, message: String, throwable: Throwable?) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}
