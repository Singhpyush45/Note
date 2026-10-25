package com.appname.notes.feature.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Backup to the user's Google Drive (Phase 9, owner choice: "Google Drive app folder"). Whole-library backups as in
 * [BackupManager], uploaded to the app's hidden Drive folder; the newest [KEEP] are kept. Restore downloads one,
 * checks it like a file restore and swaps it in at restart. Optional daily automatic backup on unmetered network
 * while charging (WorkManager). The connection state lives in this app's own preferences (not in the backup).
 */
@Singleton
class DriveBackupService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backups: BackupManager,
) {
    data class State(
        val connected: Boolean = false,
        val email: String? = null,
        val auto: Boolean = false,
        val lastBackupAt: Long = 0L,
        /** The last automatic backup failed because Google needs the user to sign in again. */
        val needsSignIn: Boolean = false,
    )

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val p = prefs
        _state.value = State(
            connected = p.getBoolean(K_CONNECTED, false),
            email = p.getString(K_EMAIL, null),
            auto = p.getBoolean(K_AUTO, false),
            lastBackupAt = p.getLong(K_LAST, 0L),
            needsSignIn = p.getBoolean(K_NEEDS_SIGN_IN, false),
        )
    }

    private suspend fun save(change: (State) -> State) = withContext(Dispatchers.IO) {
        val s = change(_state.value)
        _state.value = s
        prefs.edit()
            .putBoolean(K_CONNECTED, s.connected)
            .putString(K_EMAIL, s.email)
            .putBoolean(K_AUTO, s.auto)
            .putLong(K_LAST, s.lastBackupAt)
            .putBoolean(K_NEEDS_SIGN_IN, s.needsSignIn)
            .apply()
    }

    /** After the user agreed: remember the account. */
    suspend fun connected(token: String) {
        val email = withContext(Dispatchers.IO) { runCatching { DriveClient(token).email() }.getOrNull() }
        save { it.copy(connected = true, email = email, needsSignIn = false) }
    }

    suspend fun disconnect(token: String?) {
        if (token != null) withContext(Dispatchers.IO) { runCatching { DriveAuth.revoke(token) } }
        setAuto(false)
        save { State() }
    }

    suspend fun setAuto(on: Boolean) {
        save { it.copy(auto = on) }
        val wm = WorkManager.getInstance(context)
        if (on) {
            val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresCharging(true)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    /** Makes a backup and uploads it; returns the Drive file id. */
    suspend fun backup(token: String, onProgress: (Float) -> Unit = {}): String = withContext(Dispatchers.IO) {
        val (file, manifest) = backups.exportToCache { d, t -> if (t > 0) onProgress(0.4f * d / t) }
        try {
            val client = DriveClient(token)
            val id = client.upload(file, fileName(manifest.createdAt)) { d, t -> onProgress(0.4f + 0.6f * d / t) }
            // Keep only the newest few.
            runCatching { client.list().drop(KEEP).forEach { client.delete(it.id) } }
            save { it.copy(lastBackupAt = manifest.createdAt, needsSignIn = false) }
            id
        } finally {
            file.delete()
        }
    }

    suspend fun list(token: String): List<DriveFile> = withContext(Dispatchers.IO) { DriveClient(token).list() }

    /** Downloads [file] and checks it; on Ready the app restarts to finish. */
    suspend fun prepareRestore(token: String, file: DriveFile, onProgress: (Float) -> Unit = {}): BackupManager.Prepared =
        withContext(Dispatchers.IO) {
            val tmp = File(context.cacheDir, "download-backup.zip")
            try {
                tmp.outputStream().use { out ->
                    DriveClient(token).download(file.id, out) { d -> if (file.size > 0) onProgress(0.7f * d / file.size) }
                }
                onProgress(0.8f)
                tmp.inputStream().use { backups.prepareRestore(it, tmp.length()) }
            } finally {
                tmp.delete()
            }
        }

    /** The scheduled run: silent sign-in; if Google needs the user, mark it so Settings can ask. */
    suspend fun autoBackup(): Boolean {
        load()
        if (!_state.value.connected || !_state.value.auto) return true
        return when (val r = DriveAuth.authorize(context)) {
            is DriveAuth.Result.Token -> try {
                backup(r.accessToken)
                true
            } catch (e: DriveException) {
                if (e.code == 401 || e.code == 403) save { it.copy(needsSignIn = true) }
                e.code !in 400..499
            } catch (e: java.io.IOException) {
                false // network: try again later
            }
            is DriveAuth.Result.NeedsConsent -> { save { it.copy(needsSignIn = true) }; true }
            else -> true
        }
    }

    companion object {
        const val KEEP = 5
        private const val PREFS = "drive_backup"
        private const val WORK = "drive-backup"
        private const val K_CONNECTED = "connected"
        private const val K_EMAIL = "email"
        private const val K_AUTO = "auto"
        private const val K_LAST = "last"
        private const val K_NEEDS_SIGN_IN = "needs_sign_in"

        fun fileName(at: Long): String {
            val f = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).apply { timeZone = TimeZone.getDefault() }
            return "${DriveClient.PREFIX}${f.format(Date(at))}.zip"
        }
    }
}

/** Daily automatic backup to Drive (only when the user turned it on). */
class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun driveBackup(): DriveBackupService
    }

    override suspend fun doWork(): Result {
        val service = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).driveBackup()
        return if (service.autoBackup()) Result.success() else Result.retry()
    }
}
