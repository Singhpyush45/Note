package com.appname.notes.feature.attachments

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Google's document scanner (FR-011): finds the page edges, crops, straightens and cleans up, several pages,
 * optionally from the gallery. It runs in Google Play services, so the app needs no camera permission.
 * The result is one PDF.
 */
object DocumentScannerOptions {
    const val PAGE_LIMIT = 30

    val options: GmsDocumentScannerOptions = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(PAGE_LIMIT)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()
}

/**
 * Returns a function that opens the scanner. [onScanned] gets the scanned PDF already copied to a file named
 * "Scan <date time>.pdf" (the caller attaches it, then deletes it). [onUnavailable] is called when the scanner
 * can't start (no Play services, module not installed yet); cancelling does nothing.
 */
@Composable
fun rememberDocumentScanner(onScanned: (File) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val scanned by rememberUpdatedState(onScanned)
    val unavailable by rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pdf = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf ?: return@rememberLauncherForActivityResult
        val ctx = activity ?: return@rememberLauncherForActivityResult
        val file = stageScan(ctx, pdf.uri)
        if (file != null) scanned(file) else unavailable()
    }
    return remember(activity) {
        {
            val a = activity
            if (a == null) {
                unavailable()
            } else {
                try {
                    GmsDocumentScanning.getClient(DocumentScannerOptions.options)
                        .getStartScanIntent(a)
                        .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                        .addOnFailureListener { unavailable() }
                } catch (e: RuntimeException) { // Play services missing entirely
                    unavailable()
                }
            }
        }
    }
}

/** Copies the scanner's PDF to the app's camera folder under a readable, dated name. Null if unreadable. */
internal fun stageScan(context: Context, source: Uri, now: Date = Date()): File? {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.ROOT).format(now)
    val target = File(dir, "Scan $stamp.pdf")
    return try {
        val input = context.contentResolver.openInputStream(source) ?: return null
        input.use { ins -> target.outputStream().use { ins.copyTo(it) } }
        target
    } catch (e: IOException) {
        target.delete()
        null
    } catch (e: SecurityException) {
        target.delete()
        null
    }
}
