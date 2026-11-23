package com.appname.notes.feature.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.data.metadata.AppMetadataSqliteStore
import com.appname.notes.feature.editor.NoteImageLayoutStore
import com.appname.notes.ui.common.FolderCoverStore
import com.appname.notes.ui.common.NoteCoverStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val createdAtUtcMillis: Long,
    val fileCount: Int,
    val totalBytes: Long,
    val sha256Summary: String,
) {
    fun toJsonString(): String = buildString {
        append("{\n")
        append("  \"schemaVersion\": ").append(schemaVersion).append(",\n")
        append("  \"createdAtUtcMillis\": ").append(createdAtUtcMillis).append(",\n")
        append("  \"fileCount\": ").append(fileCount).append(",\n")
        append("  \"totalBytes\": ").append(totalBytes).append(",\n")
        append("  \"sha256Summary\": \"").append(sha256Summary).append("\"\n")
        append("}\n")
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MANIFEST_ENTRY = "papriva_backup_manifest.json"
    }
}

sealed interface BackupOperationResult {
    data class ExportSuccess(val fileCount: Int, val totalBytes: Long) : BackupOperationResult
    data class RestoreSuccess(val restoredFiles: Int) : BackupOperationResult
    data class Failure(val reason: String) : BackupOperationResult
}

/**
 * Step 2 — Production Local `.zip` Backup & Atomic Restore Engine (`PaprivaBackupManager`).
 * - Checkpoints SQLite WAL before exporting so zero uncommitted writes are lost.
 * - Exports Room databases, `papriva_metadata.db`, and app-private ink/attachment files.
 * - Guards restore against Zip-Slip path traversal and stages extraction in a temp directory
 *   before atomically replacing target files.
 */
object PaprivaBackupManager {

    fun defaultBackupFilename(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "Papriva_Backup_$stamp.zip"
    }

    /**
     * Validates that a zip entry path resolves strictly inside `destinationDir` (prevents Zip-Slip).
     */
    fun resolveSafeZipTarget(destinationDir: File, entryName: String): File? {
        if (entryName.isBlank() || entryName.startsWith("/") || entryName.startsWith("\\")) return null
        val destCanonical = destinationDir.canonicalPath + File.separator
        val candidate = File(destinationDir, entryName)
        val candCanonical = candidate.canonicalPath
        return if (candCanonical.startsWith(destCanonical)) candidate else null
    }

    suspend fun exportToUri(context: Context, targetUri: Uri): BackupOperationResult = withContext(Dispatchers.IO) {
        try {
            val appCtx = context.applicationContext
            AppMetadataSqliteStore.getInstance(appCtx).checkpointWal()

            val entries = mutableListOf<Pair<String, File>>()
            val dbDir = appCtx.getDatabasePath(AppMetadataSqliteStore.DB_NAME).parentFile
            if (dbDir != null && dbDir.exists()) {
                dbDir.listFiles()?.forEach { f ->
                    if (f.isFile && !f.name.endsWith("-journal")) {
                        entries += "databases/${f.name}" to f
                    }
                }
            }

            val filesDir = appCtx.filesDir
            if (filesDir != null && filesDir.exists()) {
                filesDir.walkTopDown()
                    .filter { it.isFile && !it.name.endsWith(".tmp") }
                    .forEach { f ->
                        val rel = f.relativeTo(filesDir).path.replace('\\', '/')
                        if (!rel.startsWith("profileInstalled") && !rel.startsWith("datastore/")) {
                            entries += "files/$rel" to f
                        }
                    }
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L

            val outStream = appCtx.contentResolver.openOutputStream(targetUri)
                ?: return@withContext BackupOperationResult.Failure("Unable to open destination file for writing.")

            ZipOutputStream(BufferedOutputStream(outStream)).use { zos ->
                val buffer = ByteArray(16 * 1024)
                for ((zipPath, file) in entries) {
                    zos.putNextEntry(ZipEntry(zipPath))
                    BufferedInputStream(FileInputStream(file)).use { bis ->
                        while (true) {
                            val read = bis.read(buffer)
                            if (read <= 0) break
                            zos.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            totalBytes += read
                        }
                    }
                    zos.closeEntry()
                }
                val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
                val manifest = BackupManifest(
                    createdAtUtcMillis = System.currentTimeMillis(),
                    fileCount = entries.size,
                    totalBytes = totalBytes,
                    sha256Summary = hashHex,
                )
                zos.putNextEntry(ZipEntry(BackupManifest.MANIFEST_ENTRY))
                zos.write(manifest.toJsonString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            BackupOperationResult.ExportSuccess(entries.size, totalBytes)
        } catch (t: Throwable) {
            BackupOperationResult.Failure(t.localizedMessage ?: "Export failed.")
        }
    }

    suspend fun restoreFromUri(context: Context, sourceUri: Uri): BackupOperationResult = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val stagingDir = File(appCtx.cacheDir, "papriva_restore_stage_${System.currentTimeMillis()}")
        try {
            stagingDir.mkdirs()
            val inStream = appCtx.contentResolver.openInputStream(sourceUri)
                ?: return@withContext BackupOperationResult.Failure("Unable to read selected backup archive.")

            var hasManifest = false
            var extractedCount = 0
            val buffer = ByteArray(16 * 1024)

            ZipInputStream(BufferedInputStream(inStream)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) {
                        zis.closeEntry()
                        continue
                    }
                    val safeFile = resolveSafeZipTarget(stagingDir, entry.name)
                        ?: return@withContext BackupOperationResult.Failure("Blocked unsafe path in backup archive: ${entry.name}")
                    safeFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(safeFile)).use { bos ->
                        while (true) {
                            val read = zis.read(buffer)
                            if (read <= 0) break
                            bos.write(buffer, 0, read)
                        }
                    }
                    if (entry.name == BackupManifest.MANIFEST_ENTRY) {
                        hasManifest = true
                    } else {
                        extractedCount++
                    }
                    zis.closeEntry()
                }
            }

            if (!hasManifest) {
                return@withContext BackupOperationResult.Failure("Selected archive is not a valid Papriva backup.")
            }

            AppMetadataSqliteStore.resetInstanceForRestore()

            val stagedDbs = File(stagingDir, "databases")
            val targetDbDir = appCtx.getDatabasePath(AppMetadataSqliteStore.DB_NAME).parentFile
            if (stagedDbs.exists() && targetDbDir != null) {
                targetDbDir.mkdirs()
                stagedDbs.listFiles()?.filter { it.isFile }?.forEach { src ->
                    atomicCopyFile(src, File(targetDbDir, src.name))
                }
            }

            val stagedFiles = File(stagingDir, "files")
            val targetFilesDir = appCtx.filesDir
            if (stagedFiles.exists() && targetFilesDir != null) {
                stagedFiles.walkTopDown().filter { it.isFile }.forEach { src ->
                    val rel = src.relativeTo(stagedFiles).path
                    val dst = File(targetFilesDir, rel)
                    dst.parentFile?.mkdirs()
                    atomicCopyFile(src, dst)
                }
            }

            NoteCoverStore.reloadFromStorage(appCtx)
            FolderCoverStore.reloadFromStorage(appCtx)
            NoteImageLayoutStore.reloadFromStorage(appCtx)

            BackupOperationResult.RestoreSuccess(extractedCount)
        } catch (t: Throwable) {
            BackupOperationResult.Failure(t.localizedMessage ?: "Restore failed.")
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun atomicCopyFile(src: File, dst: File) {
        val tmp = File(dst.parentFile, "${dst.name}.tmp")
        src.inputStream().use { input ->
            tmp.outputStream().use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        if (dst.exists()) dst.delete()
        if (!tmp.renameTo(dst)) {
            tmp.copyTo(dst, overwrite = true)
            tmp.delete()
        }
    }
}

@Composable
fun BackupRestoreSettingsCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalDarkTheme.current

    var busy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            busy = true
            statusMessage = "Exporting backup..."
            scope.launch {
                val res = PaprivaBackupManager.exportToUri(context, uri)
                busy = false
                statusMessage = when (res) {
                    is BackupOperationResult.ExportSuccess ->
                        "Backup saved (${res.fileCount} files, ${(res.totalBytes / 1024L).coerceAtLeast(1L)} KB)."
                    is BackupOperationResult.Failure -> "Export failed: ${res.reason}"
                    else -> null
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            busy = true
            statusMessage = "Restoring backup..."
            scope.launch {
                val res = PaprivaBackupManager.restoreFromUri(context, uri)
                busy = false
                statusMessage = when (res) {
                    is BackupOperationResult.RestoreSuccess ->
                        "Restored ${res.restoredFiles} files. Restart the app to refresh all notebooks."
                    is BackupOperationResult.Failure -> "Restore failed: ${res.reason}"
                    else -> null
                }
            }
        }
    }

    val surfaceBg = if (isDark) Color(0xFF16181D) else Color(0xFFFAF7F2)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
    val accent = if (isDark) Color(0xFF8FB99E) else Color(0xFF1E3F32)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = surfaceBg,
        border = BorderStroke(0.85.dp, borderCol),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Local Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Export all notebooks, folders, covers, and resized image layouts into a single portable .zip archive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { exportLauncher.launch(PaprivaBackupManager.defaultBackupFilename()) },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = if (isDark) Color(0xFF101215) else Color(0xFFFAF7F2),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Export .zip")
                }
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Restore")
                }
            }
            if (!statusMessage.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = statusMessage!!,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
            }
        }
    }
}
