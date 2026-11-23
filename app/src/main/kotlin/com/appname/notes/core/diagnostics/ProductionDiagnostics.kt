package com.appname.notes.core.diagnostics

import android.content.Context
import android.os.Build
import android.os.StrictMode
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Step 5 — Debug StrictMode Guard & Lightweight Local Crash Breadcrumb Handler.
 * - Flags accidental main-thread disk I/O, leaked SQLite cursors, and Activity leaks in debug builds.
 * - Persists the latest uncaught exception summary to `filesDir/crash_reports/last_crash.txt`
 *   before forwarding to Android's default crash handler.
 */
object ProductionDiagnostics {

    fun enableDebugStrictMode(isDebugBuild: Boolean) {
        if (!isDebugBuild) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }

    fun installCrashBreadcrumbHandler(context: Context) {
        val appCtx = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = File(appCtx.filesDir, "crash_reports").apply { mkdirs() }
                val outFile = File(dir, "last_crash.txt")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
                val report = buildString {
                    appendLine("Timestamp: $stamp")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${throwable::class.java.name}: ${throwable.message}")
                    appendLine()
                    appendLine(sw.toString())
                }
                outFile.writeText(report, Charsets.UTF_8)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun readLastCrashReport(context: Context): String? {
        val file = File(context.applicationContext.filesDir, "crash_reports/last_crash.txt")
        return if (file.exists()) runCatching { file.readText(Charsets.UTF_8) }.getOrNull() else null
    }
}
