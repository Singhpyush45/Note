package com.appname.notes.feature.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.SettingsActionRow
import com.appname.notes.core.designsystem.component.SettingsCard
import com.appname.notes.core.designsystem.component.SettingsSwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object BackupTestTags {
    const val CARD = "backup_card"
    const val EXPORT = "backup_export"
    const val IMPORT = "backup_import"
    const val DRIVE_CONNECT = "backup_drive_connect"
    const val DRIVE_BACKUP = "backup_drive_backup"
    const val DRIVE_RESTORE = "backup_drive_restore"
    const val DRIVE_AUTO = "backup_drive_auto"
    const val DRIVE_DISCONNECT = "backup_drive_disconnect"
    const val DRIVE_DELETE = "backup_drive_delete"
    const val STATUS = "backup_status"
    const val CONFIRM_RESTORE = "backup_confirm_restore"
}

data class BackupUi(
    val busy: Int? = null, // string res of what is running
    val progress: Float? = null,
    @param:StringRes val message: Int? = null,
    val ready: BackupArchive.Manifest? = null, // a checked backup waiting for "Restore and restart"
    val driveFiles: List<DriveFile>? = null,
    val consent: PendingIntent? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backups: BackupManager,
    private val drive: DriveBackupService,
) : ViewModel() {
    private val _ui = MutableStateFlow(BackupUi())
    val ui: StateFlow<BackupUi> = _ui.asStateFlow()
    val driveState = drive.state

    private enum class Action { CONNECT, BACKUP, LIST, DISCONNECT, AUTO_ON, DELETE_ALL }

    private var pending: Action? = null
    private var chosen: DriveFile? = null

    init {
        viewModelScope.launch {
            drive.load()
            if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { BackupManager.consumeRestoreDone(context) }) {
                _ui.update { it.copy(message = R.string.backup_restored) }
            }
        }
    }

    private fun work(@StringRes label: Int, block: suspend () -> Unit) {
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = label, progress = null, message = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: DriveException) {
                _ui.update { it.copy(message = if (e.code == 401 || e.code == 403) R.string.backup_drive_sign_in_again else R.string.backup_drive_failed) }
            } catch (e: java.io.IOException) {
                _ui.update { it.copy(message = R.string.backup_failed) }
            } finally {
                _ui.update { it.copy(busy = null, progress = null) }
            }
        }
    }

    private fun progress(p: Float) = _ui.update { it.copy(progress = p.coerceIn(0f, 1f)) }

    fun exportTo(uri: Uri) = work(R.string.backup_working) {
        val out = context.contentResolver.openOutputStream(uri, "w") ?: throw java.io.IOException("no stream")
        out.use { backups.export(it) { d, t -> if (t > 0) progress(d.toFloat() / t) } }
        _ui.update { it.copy(message = R.string.backup_saved) }
    }

    fun restoreFrom(uri: Uri) = work(R.string.backup_checking) {
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("no stream")
        val r = input.use { backups.prepareRestore(it, size) }
        handlePrepared(r)
    }

    private fun handlePrepared(r: BackupManager.Prepared) {
        when (r) {
            is BackupManager.Prepared.Ready -> _ui.update { it.copy(ready = r.manifest) }
            BackupManager.Prepared.Damaged -> _ui.update { it.copy(message = R.string.backup_damaged) }
            BackupManager.Prepared.TooNew -> _ui.update { it.copy(message = R.string.backup_too_new) }
            BackupManager.Prepared.NoSpace -> _ui.update { it.copy(message = R.string.backup_no_space) }
        }
    }

    fun confirmRestore() {
        _ui.update { it.copy(ready = null) }
        backups.restartApp()
    }

    fun cancelRestore() {
        _ui.update { it.copy(ready = null) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { backups.cancelPending() }
    }

    // Drive

    fun connect() = withToken(Action.CONNECT)
    fun backupToDrive() = withToken(Action.BACKUP)
    fun restoreFromDrive() = withToken(Action.LIST)
    fun disconnect() = withToken(Action.DISCONNECT)

    /** Removes every backup this app put in the user's Drive (data deletion, SRS 13.2). */
    fun deleteDriveBackups() = withToken(Action.DELETE_ALL)
    fun setAuto(on: Boolean) = if (on) withToken(Action.AUTO_ON) else viewModelScope.launch { drive.setAuto(false) }.let { }

    fun pick(file: DriveFile?) {
        _ui.update { it.copy(driveFiles = null) }
        if (file == null) return
        chosen = file
        withToken(Action.LIST, afterPick = true)
    }

    private var afterPick = false

    private fun withToken(action: Action, afterPick: Boolean = false) {
        if (_ui.value.busy != null) return
        this.afterPick = afterPick
        viewModelScope.launch {
            _ui.update { it.copy(message = null) }
            when (val r = DriveAuth.authorize(context)) {
                is DriveAuth.Result.Token -> run(action, r.accessToken)
                is DriveAuth.Result.NeedsConsent -> {
                    pending = action
                    _ui.update { it.copy(consent = r.intent) }
                }
                else -> authFailed(r, action)
            }
        }
    }

    fun consentLaunched() = _ui.update { it.copy(consent = null) }

    fun onConsent(data: Intent?) {
        val action = pending ?: return
        pending = null
        when (val r = DriveAuth.fromIntent(context, data)) {
            is DriveAuth.Result.Token -> run(action, r.accessToken)
            else -> authFailed(r, action)
        }
    }

    private fun authFailed(r: DriveAuth.Result, action: Action) {
        if (action == Action.DISCONNECT) { viewModelScope.launch { drive.disconnect(null) }; return }
        _ui.update {
            it.copy(
                message = when (r) {
                    DriveAuth.Result.NotRegistered -> R.string.backup_drive_not_registered
                    DriveAuth.Result.NoPlayServices -> R.string.backup_drive_no_play
                    else -> R.string.backup_drive_cancelled
                },
            )
        }
    }

    private fun run(action: Action, token: String) {
        when (action) {
            Action.CONNECT -> work(R.string.backup_drive_connecting) { drive.connected(token) }
            Action.DISCONNECT -> work(R.string.backup_drive_disconnecting) { drive.disconnect(token) }
            Action.DELETE_ALL -> work(R.string.backup_drive_deleting) {
                drive.deleteAll(token)
                _ui.update { it.copy(message = R.string.backup_drive_deleted) }
            }
            Action.AUTO_ON -> work(R.string.backup_drive_connecting) {
                if (!drive.state.value.connected) drive.connected(token)
                drive.setAuto(true)
            }
            Action.BACKUP -> work(R.string.backup_drive_uploading) {
                if (!drive.state.value.connected) drive.connected(token)
                drive.backup(token) { progress(it) }
                _ui.update { it.copy(message = R.string.backup_drive_done) }
            }
            Action.LIST -> {
                val file = chosen
                if (afterPick && file != null) {
                    chosen = null
                    work(R.string.backup_drive_downloading) { handlePrepared(drive.prepareRestore(token, file) { progress(it) }) }
                } else {
                    work(R.string.backup_drive_listing) {
                        if (!drive.state.value.connected) drive.connected(token)
                        val files = drive.list(token)
                        if (files.isEmpty()) _ui.update { it.copy(message = R.string.backup_drive_none) } else _ui.update { it.copy(driveFiles = files) }
                    }
                }
            }
        }
    }
}

/** Settings › Backup & restore. */
@Composable
fun BackupCard(viewModel: BackupViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val drive by viewModel.driveState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restoreFrom(uri)
    }
    val consent: ActivityResultLauncher<IntentSenderRequest> =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r -> viewModel.onConsent(r.data) }
    LaunchedEffect(ui.consent) {
        val pi = ui.consent ?: return@LaunchedEffect
        viewModel.consentLaunched()
        consent.launch(IntentSenderRequest.Builder(pi).build())
    }

    SettingsCard(stringResource(R.string.backup_section), Modifier.testTag(BackupTestTags.CARD)) {
        SettingsActionRow(
            title = stringResource(R.string.backup_export),
            summary = stringResource(R.string.backup_export_summary),
            onClick = { export.launch(DriveBackupService.fileName(System.currentTimeMillis())) },
            modifier = Modifier.testTag(BackupTestTags.EXPORT),
        )
        SettingsActionRow(
            title = stringResource(R.string.backup_import),
            summary = stringResource(R.string.backup_import_summary),
            onClick = { import.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
            modifier = Modifier.testTag(BackupTestTags.IMPORT),
        )
        if (!drive.connected) {
            SettingsActionRow(
                title = stringResource(R.string.backup_drive_connect),
                summary = stringResource(R.string.backup_drive_connect_summary),
                onClick = viewModel::connect,
                modifier = Modifier.testTag(BackupTestTags.DRIVE_CONNECT),
            )
        } else {
            val last = if (drive.lastBackupAt > 0) {
                DateUtils.getRelativeTimeSpanString(drive.lastBackupAt).toString()
            } else {
                stringResource(R.string.backup_drive_never)
            }
            SettingsActionRow(
                title = stringResource(R.string.backup_drive_backup),
                summary = stringResource(R.string.backup_drive_backup_summary, drive.email ?: stringResource(R.string.backup_drive_account), last),
                onClick = viewModel::backupToDrive,
                modifier = Modifier.testTag(BackupTestTags.DRIVE_BACKUP),
            )
            SettingsActionRow(
                title = stringResource(R.string.backup_drive_restore),
                onClick = viewModel::restoreFromDrive,
                modifier = Modifier.testTag(BackupTestTags.DRIVE_RESTORE),
            )
            SettingsSwitchRow(
                title = stringResource(R.string.backup_drive_auto),
                summary = stringResource(R.string.backup_drive_auto_summary),
                checked = drive.auto,
                onCheckedChange = viewModel::setAuto,
                modifier = Modifier.testTag(BackupTestTags.DRIVE_AUTO),
            )
            if (drive.needsSignIn) {
                StatusText(stringResource(R.string.backup_drive_sign_in_again))
            }
            var confirmDelete by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            SettingsActionRow(
                title = stringResource(R.string.backup_drive_delete_all),
                onClick = { confirmDelete = true },
                modifier = Modifier.testTag(BackupTestTags.DRIVE_DELETE),
            )
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    text = { Text(stringResource(R.string.backup_drive_delete_confirm)) },
                    confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteDriveBackups() }) { Text(stringResource(R.string.action_delete)) } },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
                )
            }
            SettingsActionRow(
                title = stringResource(R.string.backup_drive_disconnect),
                onClick = viewModel::disconnect,
                modifier = Modifier.testTag(BackupTestTags.DRIVE_DISCONNECT),
            )
        }
        val busy = ui.busy
        if (busy != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(busy), style = MaterialTheme.typography.bodyMedium)
                val p = ui.progress
                if (p != null) {
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
        ui.message?.let { StatusText(stringResource(it)) }
    }

    ui.ready?.let { m ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_confirm_body,
                        DateUtils.formatDateTime(context, m.createdAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR),
                        Formatter.formatShortFileSize(context, m.totalBytes),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore, modifier = Modifier.testTag(BackupTestTags.CONFIRM_RESTORE)) {
                    Text(stringResource(R.string.backup_confirm_action))
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    ui.driveFiles?.let { files ->
        AlertDialog(
            onDismissRequest = { viewModel.pick(null) },
            title = { Text(stringResource(R.string.backup_drive_pick)) },
            text = {
                LazyColumn {
                    items(files, key = { it.id }) { f ->
                        Text(
                            text = f.name.removePrefix(DriveClient.PREFIX).removeSuffix(".zip") + "  ·  " + Formatter.formatShortFileSize(context, f.size),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.pick(f) }.padding(vertical = 14.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { viewModel.pick(null) }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(BackupTestTags.STATUS),
    )
}
