package com.appname.notes.feature.ink

import android.graphics.Bitmap
import android.graphics.Color
import com.appname.notes.feature.attachments.decodeImage
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import kotlin.math.max
import kotlin.math.sqrt
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
            return try { offlineFallback(input, out, Result.Failed) } finally { recycle(bitmap, input) }
        } catch (e: LinkageError) {
            return try { offlineFallback(input, out, Result.Failed) } finally { recycle(bitmap, input) }
        }
        try {
            repeat(ATTEMPTS) { attempt ->
                try {
                    val result = Tasks.await(segmenter.process(InputImage.fromBitmap(input, 0)))
                    val fg = result.foregroundBitmap
                    if (fg == null || !hasSubject(fg)) {
                        fg?.recycle()
                        return offlineFallback(input, out, Result.NoSubject)
                    }
                    return try { saveCutout(fg, out) } finally { fg.recycle() }
                } catch (e: ExecutionException) {
                    val cause = e.cause
                    // Give Play services a short chance to fetch the model, then use the offline cutout.
                    if (cause is MlKitException && cause.errorCode == MlKitException.UNAVAILABLE) {
                        if (attempt == 0) {
                            Thread.sleep(WAIT_MS)
                        } else {
                            return offlineFallback(input, out, Result.NotReady)
                        }
                    } else {
                        return offlineFallback(input, out, Result.Failed)
                    }
                }
            }
            return offlineFallback(input, out, Result.NotReady)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return offlineFallback(input, out, Result.Failed)
        } catch (e: Exception) {
            return offlineFallback(input, out, Result.Failed)
        } catch (e: LinkageError) {
            return offlineFallback(input, out, Result.Failed)
        } finally {
            runCatching { segmenter.close() }
            recycle(bitmap, input)
        }
    }

    private fun recycle(bitmap: Bitmap, input: Bitmap) {
        if (input !== bitmap) input.recycle()
        bitmap.recycle()
    }

    private fun saveCutout(bitmap: Bitmap, out: File): Result {
        if (!hasSubject(bitmap)) return Result.NoSubject
        out.parentFile?.mkdirs()
        val saved = out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return if (saved) Result.Done(out) else Result.Failed
    }

    private fun offlineFallback(source: Bitmap, out: File, ifUnavailable: Result): Result {
        val cutout = try { cutOutOnDeviceFallback(source) } catch (_: OutOfMemoryError) { null } ?: return ifUnavailable
        return try {
            val result = saveCutout(cutout, out)
            if (result == Result.NoSubject) ifUnavailable else result
        } catch (_: Exception) {
            ifUnavailable
        } finally {
            cutout.recycle()
        }
    }

    /** Removes border-connected pixels similar to the sampled background, without Google Play services. */
    private fun cutOutOnDeviceFallback(src: Bitmap): Bitmap? {
        val w = src.width
        val h = src.height
        if (w < 4 || h < 4) return null
        val count = w * h
        val pixels = IntArray(count)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var borderCount = 0
        val stepX = max(1, w / 32)
        val stepY = max(1, h / 32)
        for (x in 0 until w step stepX) {
            for (idx in intArrayOf(x, (h - 1) * w + x)) {
                rSum += Color.red(pixels[idx]); gSum += Color.green(pixels[idx]); bSum += Color.blue(pixels[idx]); borderCount++
            }
        }
        for (y in 0 until h step stepY) {
            for (idx in intArrayOf(y * w, y * w + w - 1)) {
                rSum += Color.red(pixels[idx]); gSum += Color.green(pixels[idx]); bSum += Color.blue(pixels[idx]); borderCount++
            }
        }
        if (borderCount == 0) return null
        val bgR = (rSum / borderCount).toInt()
        val bgG = (gSum / borderCount).toInt()
        val bgB = (bSum / borderCount).toInt()

        var variation = 0.0
        var samples = 0
        for (x in 0 until w step stepX) {
            val c = pixels[x]
            val dr = Color.red(c) - bgR
            val dg = Color.green(c) - bgG
            val db = Color.blue(c) - bgB
            variation += sqrt((dr * dr + dg * dg + db * db).toDouble())
            samples++
        }
        val threshold = (variation / max(1, samples)).coerceIn(18.0, 52.0) + 28.0
        val thresholdSq = (threshold * threshold).toInt()
        val softThresholdSq = ((threshold + 18.0) * (threshold + 18.0)).toInt()
        val visited = BooleanArray(count)
        val background = BooleanArray(count)
        val queue = IntArray(count)
        var head = 0
        var tail = 0

        fun colorDistanceSq(index: Int): Int {
            val c = pixels[index]
            val dr = Color.red(c) - bgR
            val dg = Color.green(c) - bgG
            val db = Color.blue(c) - bgB
            return dr * dr + dg * dg + db * db
        }
        fun enqueue(index: Int) {
            if (!visited[index]) {
                visited[index] = true
                if (colorDistanceSq(index) <= thresholdSq) {
                    background[index] = true
                    queue[tail++] = index
                }
            }
        }
        for (x in 0 until w) { enqueue(x); enqueue((h - 1) * w + x) }
        for (y in 0 until h) { enqueue(y * w); enqueue(y * w + w - 1) }
        while (head < tail) {
            val index = queue[head++]
            val x = index % w
            val y = index / w
            if (x > 0) enqueue(index - 1)
            if (x + 1 < w) enqueue(index + 1)
            if (y > 0) enqueue(index - w)
            if (y + 1 < h) enqueue(index + w)
        }
        for (y in 0 until h) for (x in 0 until w) {
            val index = y * w + x
            if (background[index]) {
                pixels[index] = Color.TRANSPARENT
            } else {
                val nearBackground = (x > 0 && background[index - 1]) ||
                    (x + 1 < w && background[index + 1]) ||
                    (y > 0 && background[index - w]) ||
                    (y + 1 < h && background[index + w])
                val distance = colorDistanceSq(index)
                if (nearBackground && distance < softThresholdSq) {
                    val alpha = ((distance - thresholdSq).toFloat() / max(1, softThresholdSq - thresholdSq)).coerceIn(0.15f, 1f)
                    val c = pixels[index]
                    pixels[index] = Color.argb((Color.alpha(c) * alpha).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
                }
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(pixels, 0, w, 0, 0, w, h) }
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
