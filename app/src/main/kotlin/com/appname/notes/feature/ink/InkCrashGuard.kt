package com.appname.notes.feature.ink

import android.util.Log

/**
 * Works around a crash in Jetpack Ink 1.0.0 found by our process-death e2e test: when the app goes to
 * the background right after a stroke, Ink closes its front-buffer renderer but a draw already queued
 * on its render thread still runs and throws
 * `IllegalStateException: Attempt to draw after renderer has been closed`, killing the whole app.
 *
 * Only that exact exception on Ink's own render thread is absorbed (the thread ends; nothing is drawn
 * that frame). Every other uncaught exception goes to the normal handler and crashes as usual, so real
 * bugs stay visible. [InkCanvasView] replaces its Ink layer when the screen is shown again, which brings
 * a fresh render thread. Saved ink is never affected: strokes are stored on pen-up.
 *
 * Remove when an Ink release fixes it (re-run `LifecycleE2ETest.handwritingSurvivesProcessDeath`
 * without this guard to check).
 */
object InkCrashGuard {

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (isKnownInkShutdownRace(thread.name, error)) {
                Log.w(TAG, "Ignored Ink render-thread shutdown race on ${thread.name}")
            } else {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    internal fun isKnownInkShutdownRace(threadName: String?, error: Throwable): Boolean =
        threadName?.startsWith("CanvasInProgressStrokesRenderHelper") == true &&
            error is IllegalStateException &&
            error.message?.contains("renderer has been closed") == true

    private const val TAG = "InkCrashGuard"
}
