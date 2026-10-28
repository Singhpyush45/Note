package com.appname.notes

import android.app.Application
import android.os.StrictMode
import com.appname.notes.feature.ink.InkCrashGuard
import com.appname.notes.feature.ink.InkImages
import com.appname.notes.di.ApplicationScope
import com.appname.notes.feature.attachments.StorageCleanup
import com.appname.notes.feature.audio.RecordingController
import com.appname.notes.feature.textindex.TextIndexScheduler
import com.appname.notes.feature.reminders.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltAndroidApp
class NotesApplication : Application() {

    @Inject lateinit var storageCleanup: StorageCleanup

    @Inject lateinit var textIndexScheduler: TextIndexScheduler

    @Inject lateinit var recordings: RecordingController

    @Inject lateinit var reminders: ReminderScheduler

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        // A checked backup waiting to be restored is swapped in before anything opens the database (FR-025).
        com.appname.notes.feature.backup.BackupManager.applyPending(this)
        if (BuildConfig.DEBUG) enableStrictMode()
        super.onCreate() // Hilt injects the fields above here
        InkCrashGuard.install()
        // Initialize the ink image cache directory at startup so front-page note thumbnails decode photos on first launch.
        InkImages.init(this)
        // Keeps the alarm set for the next reminder (FR-010); anything missed while the app was stopped goes off now.
        reminders.start()
        // Remove stored PDFs and attachments no note uses any more (e.g. the app was killed during a delete).
        // Off the startup path.
        appScope.launch {
            delay(STARTUP_CLEANUP_DELAY_MS)
            storageCleanup.run()
            // A recording cut off by the app being killed is still attached to its note (FR-012, FR-008).
            recordings.recoverInterrupted()
            // Read text in documents added while the app was closed or that failed earlier (FR-011).
            textIndexScheduler.requestRun()
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
