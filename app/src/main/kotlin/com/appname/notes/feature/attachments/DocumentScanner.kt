package com.appname.notes.feature.attachments

import android.app.Activity
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
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
 * "Scan <date time>.pdf" (the caller attaches it, then deletes it).
 *
 * Without Google Play services (or before its scanner module is installed) the phone's camera app is used
 * instead: the photo becomes a one-page PDF, as taken (no edge detection or clean-up — those need Google's
 * scanner). [onUnavailable] is called only when neither works (no camera app); cancelling does nothing.
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
    // Fallback: the camera app. The path is saved because the camera may outlive our process.
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val path = photoPath
        photoPath = null
        val ctx = activity
        if (path == null || ctx == null) return@rememberLauncherForActivityResult
        val photo = File(path)
        if (!taken) {
            photo.delete()
            return@rememberLauncherForActivityResult
        }
        val pdf = photoToPdf(ctx, photo)
        photo.delete()
        if (pdf != null) scanned(pdf) else unavailable()
    }
    return remember(activity) {
        {
            val a = activity
            fun useCamera() {
                val ctx = a ?: return unavailable()
                val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                val photo = File(dir, "scan-photo-${System.currentTimeMillis()}.jpg")
                try {
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", photo)
                    photoPath = photo.path
                    camera.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    photoPath = null
                    unavailable()
                } catch (e: IllegalArgumentException) {
                    photoPath = null
                    unavailable()
                }
            }
            if (a == null) {
                unavailable()
            } else {
                try {
                    GmsDocumentScanning.getClient(DocumentScannerOptions.options)
                        .getStartScanIntent(a)
                        .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                        .addOnFailureListener { useCamera() }
                } catch (e: RuntimeException) { // Play services missing entirely
                    useCamera()
                }
            }
        }
    }
}

/**
 * A camera photo as a one-page PDF "Scan <date time>.pdf" (page = photo, long side 842 pt, like A4), the photo
 * turned upright from its EXIF orientation. Null if the photo can't be read.
 */
internal fun photoToPdf(context: Context, photo: File, now: Date = Date()): File? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(photo.path, opts)
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
    // Decode at most ~2000 px on the long side: sharp enough to read, small enough for memory.
    var sample = 1
    while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= MAX_SCAN_PX) sample *= 2
    val raw = BitmapFactory.decodeFile(photo.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val rotation = try {
        when (ExifInterface(photo.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: IOException) {
        0f
    }
    val bmp = if (rotation == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation) }, true)
    val scale = PAGE_LONG_PT / maxOf(bmp.width, bmp.height)
    val w = (bmp.width * scale).toInt().coerceAtLeast(1)
    val h = (bmp.height * scale).toInt().coerceAtLeast(1)
    val doc = PdfDocument()
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.ROOT).format(now)
    val target = File(dir, "Scan $stamp.pdf")
    return try {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, 1).create())
        page.canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        doc.finishPage(page)
        target.outputStream().use { doc.writeTo(it) }
        target
    } catch (e: IOException) {
        target.delete()
        null
    } finally {
        doc.close()
        if (bmp !== raw) bmp.recycle()
        raw.recycle()
    }
}

private const val MAX_SCAN_PX = 2000
private const val PAGE_LONG_PT = 842f

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
