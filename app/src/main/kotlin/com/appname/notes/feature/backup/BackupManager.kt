package com.appname.notes.feature.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.StatFs
import com.appname.notes.BuildConfig
import com.appname.notes.core.database.NotesDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backup and restore (FR-025).
 *
 * - [export] writes a consistent copy: the database is checkpointed and copied while other writes wait, then the
 *   note files and settings are added (see [BackupArchive]).
 * - [prepareRestore] unpacks a backup into a staging folder and checks it completely (checksums, database opens and
 *   passes SQLite's own check, not from a newer app version). Nothing on the phone changes if any check fails.
 * - The swap itself happens at the next start, before anything opens the database ([applyPending], called first
 *   thing in Application.onCreate). The old data is moved aside first and moved back if the swap fails.
 */
@Singleton
class BackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: NotesDatabase,
) {
    /** What a restore check found. */
    sealed interface Prepared {
        data class Ready(val manifest: BackupArchive.Manifest) : Prepared
        data object Damaged : Prepared
        data object TooNew : Prepared
        data object NoSpace : Prepared
    }

    /** Writes a full backup to [out]. Runs on IO; [onProgress] gets bytes done / total. */
    suspend fun export(out: OutputStream, onProgress: (Long, Long) -> Unit = { _, _ -> }): BackupArchive.Manifest =
        withContext(Dispatchers.IO) {
            val snap = File(context.cacheDir, "backup-snapshot").apply { deleteRecursively(); mkdirs() }
            try {
                snapshotDatabase(snap)
                val files = ArrayList<Pair<String, File>>()
                snap.listFiles()?.sortedBy { it.name }?.forEach { files += "db/${it.name}" to it }
                for (dir in FILE_DIRS) {
                    val root = File(context.filesDir, dir)
                    root.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { f ->
                        files += "files/$dir/${f.relativeTo(root).invariantSeparatorsPath}" to f
                    }
                }
                BackupArchive.write(out, files, BuildConfig.VERSION_NAME, DB_VERSION, System.currentTimeMillis(), onProgress)
            } finally {
                snap.deleteRecursively()
            }
        }

    /** Writes a backup into a file in the cache (for uploading); the caller deletes it. */
    suspend fun exportToCache(onProgress: (Long, Long) -> Unit = { _, _ -> }): Pair<File, BackupArchive.Manifest> {
        val f = File(context.cacheDir, "upload-backup.zip")
        val m = f.outputStream().use { export(it, onProgress) }
        return f to m
    }

    private fun snapshotDatabase(into: File) {
        val sdb = db.openHelper.writableDatabase
        sdb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val main = context.getDatabasePath(NotesDatabase.FILE_NAME)
        val wal = File(main.path + "-wal")
        // Holding a write transaction keeps other writers (and their checkpoints) out while the files are copied,
        // so the database file and its write-ahead log are copied as one consistent state.
        sdb.beginTransaction()
        try {
            main.copyTo(File(into, NotesDatabase.FILE_NAME), overwrite = true)
            if (wal.exists() && wal.length() > 0) wal.copyTo(File(into, NotesDatabase.FILE_NAME + "-wal"), overwrite = true)
        } finally {
            sdb.endTransaction()
        }
    }

    /** Unpacks and checks a backup; on [Prepared.Ready] the restore happens when the app restarts ([restartApp]). */
    suspend fun prepareRestore(input: InputStream, sizeHint: Long = -1): Prepared = withContext(Dispatchers.IO) {
        val root = dataDir()
        val pending = File(root, PENDING).apply { deleteRecursively() }
        val staging = File(root, STAGING).apply { deleteRecursively(); mkdirs() }
        val free = StatFs(root.path).availableBytes
        if (sizeHint > 0 && free < sizeHint * 2 + MIN_FREE) return@withContext Prepared.NoSpace
        val result = BackupArchive.extract(input, staging, DB_VERSION, maxBytes = (free - MIN_FREE).coerceAtLeast(0))
        val outcome = when (result) {
            is BackupArchive.ReadResult.TooNew -> Prepared.TooNew
            is BackupArchive.ReadResult.Damaged -> Prepared.Damaged
            is BackupArchive.ReadResult.Ok -> if (checkDatabase(File(staging, BackupArchive.DB_PATH))) Prepared.Ready(result.manifest) else Prepared.Damaged
        }
        if (outcome is Prepared.Ready) {
            if (!staging.renameTo(pending)) return@withContext Prepared.Damaged.also { staging.deleteRecursively() }
            File(pending, READY).writeText(outcome.manifest.createdAt.toString())
        } else {
            staging.deleteRecursively()
        }
        outcome
    }

    /** Folds the write-ahead log into the staged database and runs SQLite's integrity check on it. */
    private fun checkDatabase(file: File): Boolean = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { d ->
            d.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            val version = d.version
            val ok = d.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
            val hasNotes = d.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='notes'", null).use { it.moveToFirst() }
            ok && hasNotes && version in 1..DB_VERSION
        }.also {
            File(file.path + "-wal").delete()
            File(file.path + "-shm").delete()
        }
    } catch (e: Exception) {
        false
    }

    fun cancelPending() {
        File(dataDir(), PENDING).deleteRecursively()
    }

    /** Starts the app again so [applyPending] runs before the database opens. */
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(intent.component))
        Runtime.getRuntime().exit(0)
    }

    private fun dataDir(): File = context.filesDir.parentFile ?: context.filesDir

    companion object {
        /** Kept in step with @Database(version) of [NotesDatabase]. */
        const val DB_VERSION = 10
        val FILE_DIRS = listOf("attachments", "pdfs", "recordings", "wallpapers", "datastore") // the folders of AttachmentFileStore, PdfFileStore, RecordingController, WallpaperStore, settings
        private const val PENDING = "restore-pending"
        private const val STAGING = "restore-staging"
        private const val OLD = "restore-old"
        private const val READY = "READY"
        private const val DONE = "restore-done"
        private const val MIN_FREE = 50L * 1024 * 1024

        /**
         * Called at app start before Hilt (so before the database or settings are opened): swaps in a checked
         * backup if one is waiting. Returns true if a restore was applied. On any failure the old data is put back.
         */
        fun applyPending(context: Context): Boolean {
            val root = context.filesDir.parentFile ?: return false
            val old = File(root, OLD)
            val pending = File(root, PENDING)
            File(root, STAGING).deleteRecursively()
            if (!File(pending, READY).exists()) {
                old.deleteRecursively() // from the previous, successful restore
                pending.deleteRecursively()
                return false
            }
            old.deleteRecursively()
            val dbFile = context.getDatabasePath(NotesDatabase.FILE_NAME)
            val dbParts = listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm"), File(dbFile.path + "-journal"))
            val moved = ArrayList<Pair<File, File>>() // original place → where it was moved aside
            return try {
                File(old, "db").mkdirs()
                File(old, "files").mkdirs()
                for (f in dbParts) if (f.exists()) moveOrThrow(f, File(old, "db/${f.name}"), moved)
                for (dir in FILE_DIRS) {
                    val f = File(context.filesDir, dir)
                    if (f.exists()) moveOrThrow(f, File(old, "files/$dir"), moved)
                }
                dbFile.parentFile?.mkdirs()
                if (!File(pending, BackupArchive.DB_PATH).renameTo(dbFile)) error("db")
                for (dir in FILE_DIRS) {
                    val src = File(pending, "files/$dir")
                    if (src.exists() && !src.renameTo(File(context.filesDir, dir))) error(dir)
                }
                pending.deleteRecursively()
                File(root, DONE).writeText("1")
                true
            } catch (e: Exception) {
                // Put everything back as it was.
                // Remove what was put in place, then move the originals back.
                for (f in dbParts) if (moved.none { it.first == f }) f.delete()
                for (dir in FILE_DIRS) {
                    val f = File(context.filesDir, dir)
                    if (moved.none { it.first == f }) File(pending, "files/$dir").let { if (!it.exists()) f.deleteRecursively() }
                }
                moved.asReversed().forEach { (orig, aside) -> orig.deleteRecursively(); aside.renameTo(orig) }
                pending.deleteRecursively()
                false
            }
        }

        private fun moveOrThrow(from: File, to: File, moved: MutableList<Pair<File, File>>) {
            if (!from.renameTo(to)) error("move ${from.name}")
            moved += from to to
        }

        /** True once after a restore was applied (for the "Restore finished" message). */
        fun consumeRestoreDone(context: Context): Boolean {
            val f = File(context.filesDir.parentFile ?: return false, DONE)
            return f.exists() && f.delete()
        }
    }
}
