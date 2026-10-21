package com.appname.notes

import android.app.Application
import android.os.StrictMode
import com.appname.notes.feature.ink.InkCrashGuard
import com.appname.notes.di.ApplicationScope
import com.appname.notes.feature.pdf.PdfImporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltAndroidApp
class NotesApplication : Application() {

    @Inject lateinit var pdfImporter: PdfImporter

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        if (BuildConfig.DEBUG) enableStrictMode()
        super.onCreate() // Hilt injects the fields above here
        InkCrashGuard.install()
        // Remove stored PDFs no note uses any more (e.g. the app was killed during a delete). Off the startup path.
        appScope.launch {
            delay(STARTUP_CLEANUP_DELAY_MS)
            pdfImporter.cleanUp()
        }
    }

    /**
     * Debug builds log any disk or network access on the main thread, leaked resources, etc. (NFR-01).
     * Check Logcat for "StrictMode" while testing; each violation is a bug to fix.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build(),
        )
    }

    private companion object {
        const val STARTUP_CLEANUP_DELAY_MS = 5_000L
    }
}
