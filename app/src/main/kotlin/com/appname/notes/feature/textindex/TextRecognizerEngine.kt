package com.appname.notes.feature.textindex

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutionException

/** The OCR engine can't run on this device (model or native code missing). Retried later, never fatal. */
class OcrUnavailableException(cause: Throwable? = null) : Exception("Text recognition unavailable", cause)

/** Reads text in a picture (FR-011). Behind an interface so the vendor can change (NFR-11) and tests can fake it. */
fun interface TextRecognizerEngine {
    /** Blocking; call from a background thread. Throws [OcrUnavailableException] when OCR can't run here. */
    fun recognize(bitmap: Bitmap): String

    /** Frees models and native memory until the next [recognize] (called when an indexing run ends). */
    fun release() {}
}

/**
 * Google ML Kit, on the device: nothing is uploaded. Models are bundled in the app, so it works offline from
 * the first launch. Latin script (English and other European languages) plus Devanagari (Hindi, Marathi, …).
 */
class MlKitTextRecognizer : TextRecognizerEngine {

    // Created on first use and closed by release(): the models take tens of MB of native memory.
    private var latinClient: TextRecognizer? = null
    private var devanagariClient: TextRecognizer? = null

    private val latin: TextRecognizer
        @Synchronized get() = latinClient ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { latinClient = it }
    private val devanagari: TextRecognizer
        @Synchronized get() = devanagariClient ?: TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()).also { devanagariClient = it }

    @Synchronized
    override fun release() {
        latinClient?.close()
        devanagariClient?.close()
        latinClient = null
        devanagariClient = null
    }

    override fun recognize(bitmap: Bitmap): String = try {
        val image = InputImage.fromBitmap(bitmap, 0)
        val latinText = run(latin, image)
        // The Devanagari model reads Latin too, less well: keep only the lines in Devanagari from it.
        val hindiLines = run(devanagari, image).lines().filter { line -> line.any { it in DEVANAGARI } }
        (listOf(latinText) + hindiLines).filter { it.isNotBlank() }.joinToString("\n")
    } catch (e: LinkageError) { // native library or model missing on this device/ABI (also when first created)
        throw OcrUnavailableException(e)
    }

    private fun run(recognizer: TextRecognizer, image: InputImage): String = try {
        Tasks.await(recognizer.process(image)).text
    } catch (e: ExecutionException) {
        val cause = e.cause
        if (cause is MlKitException && cause.errorCode == MlKitException.UNAVAILABLE) throw OcrUnavailableException(cause)
        throw cause ?: e
    } catch (e: MlKitException) {
        if (e.errorCode == MlKitException.UNAVAILABLE) throw OcrUnavailableException(e) else throw e
    }

    private companion object {
        val DEVANAGARI = 'ऀ'..'ॿ'
    }
}
