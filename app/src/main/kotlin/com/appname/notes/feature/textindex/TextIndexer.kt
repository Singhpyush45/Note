package com.appname.notes.feature.textindex

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.domain.model.IndexStatus
import com.appname.notes.core.domain.model.TextSource
import com.appname.notes.core.domain.repository.TextIndexRepository
import com.appname.notes.core.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Makes documents searchable (FR-011): takes sources nobody has read yet, extracts their text and stores it.
 * Small batches, so stopping (WorkManager limits, the user turning it off) loses at most one document's work.
 */
class TextIndexer(
    private val repository: TextIndexRepository,
    /** DocumentTextExtractor::extract in the app; a fake in tests. Blocking. */
    private val extract: (TextSource) -> Extraction,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger,
    /** Frees OCR models after a run, so they don't hold native memory while the app is used (PDF-06). */
    private val release: () -> Unit = {},
) {
    private val running = Mutex()

    /** Processes until nothing is left or [shouldStop]. Returns how many sources were processed. One run at a time. */
    suspend fun run(shouldStop: () -> Boolean = { false }): Int = running.withLock {
        try {
            runBatches(shouldStop)
        } finally {
            release()
        }
    }

    private suspend fun runBatches(shouldStop: () -> Boolean): Int = withContext(dispatchers.io) {
        var processed = 0
        while (!shouldStop() && settings.documentText.first()) {
            val retryBefore = clock.now().minus(RETRY_AFTER)
            val batch = (repository.pending(BATCH, retryBefore) as? AppResult.Success)?.value.orEmpty()
            if (batch.isEmpty()) break
            for (source in batch) {
                if (shouldStop()) break
                val extraction = try {
                    extract(source)
                } catch (e: OcrUnavailableException) {
                    Extraction(emptyList(), IndexStatus.UNAVAILABLE)
                } catch (e: OutOfMemoryError) {
                    Extraction(emptyList(), IndexStatus.FAILED)
                } catch (e: Exception) { // a damaged file must never stop the others
                    logger.warn(TAG, "Text extraction failed", e)
                    Extraction(emptyList(), IndexStatus.FAILED)
                }
                repository.save(source, extraction.pages, extraction.status)
                processed++
            }
        }
        processed
    }

    private companion object {
        const val TAG = "TextIndexer"
        const val BATCH = 5
        val RETRY_AFTER: Duration = Duration.ofHours(6)
    }
}

/** Runs [TextIndexer] in the background (survives the app closing; deferred when the battery is low, NFR-08). */
class TextIndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun textIndexer(): TextIndexer
    }

    override suspend fun doWork(): Result {
        val indexer = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).textIndexer()
        indexer.run { isStopped }
        return Result.success()
    }
}

/** Asks for indexing to run soon. Safe to call often: runs are queued one after another, never in parallel. */
class TextIndexScheduler(private val context: Context, private val logger: AppLogger) {
    fun requestRun() {
        val request = OneTimeWorkRequestBuilder<TextIndexWorker>()
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        try {
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        } catch (e: IllegalStateException) { // WorkManager not initialised (some test environments)
            logger.warn("TextIndexScheduler", "Couldn't schedule indexing", e)
        }
    }

    companion object {
        const val WORK_NAME = "document-text-index"
    }
}

/** What happens when "Find text in photos and PDFs" is turned on or off. */
fun interface DocumentTextSwitch {
    suspend fun onChanged(enabled: Boolean)
}

/** On: index in the background. Off: forget all extracted text. */
class IndexingDocumentTextSwitch(private val repository: TextIndexRepository, private val scheduler: TextIndexScheduler) : DocumentTextSwitch {
    override suspend fun onChanged(enabled: Boolean) {
        if (enabled) scheduler.requestRun() else repository.clear()
    }
}
