package com.appname.notes.feature.ink

import android.graphics.Bitmap
import com.appname.notes.feature.attachments.decodeImage
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import java.util.concurrent.ExecutionException

/**
 * Cuts the main subject out of a photo (owner request: remove the background of a pasted or dropped picture), on the
 * phone with ML Kit subject segmentation: people, pets, objects — whatever stands out in front. The result is a PNG
 * of the same size, transparent where the background was. Nothing is uploaded; Google Play services fetches the
 * segmentation model once (at install, or on first use — then this asks to try again in a moment).
 * Blocking: call off the main thread.
 */
object BackgroundRemover {

    sealed interface Result {
        data class Done(val file: File) : Result

        /** Nothing stands out in the picture to keep. */
        data object NoSubject : Result

        /** The model is still being downloaded by Google Play services. */
        data object NotReady : Result

        /** No Google Play services, or the picture can't be read. */
        data object Failed : Result
    }

    fun cutOut(source: File, out: File): Result {
        val bitmap = try {
            decodeImage(source, MAX_PX)
        } catch (e: OutOfMemoryError) {
            null
        } ?: return Result.Failed
        val input = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val segmenter = try {
            SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        } catch (e: Exception) {
            return Result.Failed
        } catch (e: LinkageError) {
            return Result.Failed
        }
        try {
            repeat(ATTEMPTS) { attempt ->
                try {
                    val result = Tasks.await(segmenter.process(InputImage.fromBitmap(input, 0)))
                    val fg = result.foregroundBitmap ?: return Result.NoSubject
                    if (!hasSubject(fg)) return Result.NoSubject
                    out.parentFile?.mkdirs()
                    out.outputStream().use { fg.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    fg.recycle()
                    return Result.Done(out)
                } catch (e: ExecutionException) {
                    val cause = e.cause
                    // The model isn't on the phone yet: Play services is fetching it; wait a little and try again.
                    if (cause is MlKitException && cause.errorCode == MlKitException.UNAVAILABLE) {
                        if (attempt < ATTEMPTS - 1) Thread.sleep(WAIT_MS)
                    } else {
                        return Result.Failed
                    }
                }
            }
            return Result.NotReady
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return Result.Failed
        } catch (e: Exception) {
            return Result.Failed
        } catch (e: LinkageError) {
            return Result.Failed
        } finally {
            runCatching { segmenter.close() }
            if (input !== bitmap) input.recycle()
            bitmap.recycle()
        }
    }

    /** At least a few percent of the picture was kept. */
    private fun hasSubject(b: Bitmap): Boolean {
        val step = maxOf(1, maxOf(b.width, b.height) / 64)
        var kept = 0
        var total = 0
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                total++
                if ((b.getPixel(x, y) ushr 24) > 32) kept++
                x += step
            }
            y += step
        }
        return total > 0 && kept * 50 >= total
    }

    private const val MAX_PX = 2048
    private const val ATTEMPTS = 8
    private const val WAIT_MS = 2_500L
}
