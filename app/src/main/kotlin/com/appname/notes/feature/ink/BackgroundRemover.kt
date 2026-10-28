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
import java.util.ArrayDeque
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Removes the background of a photo on the phone (owner request: "Selection › remove background").
 *
 * Uses ML Kit's on-device Subject Segmentation when Google Play services has the model ready; if Play
 * services is unavailable, offline, or still downloading the model, falls back to an on-device border-connected
 * flood-fill background remover that preserves 100% of interior foreground pixel colours and opacity.
 *
 * Blocking: call from an IO dispatcher.
 */
object BackgroundRemover {

    sealed interface Result {
        data class Done(val file: File) : Result

        /** Nothing in the picture stood out as a subject, or the whole picture already was one. */
        data object NoSubject : Result

        /** Google Play services is still downloading the subject-segmentation model (first use). */
        data object NotReady : Result

        data object Failed : Result
    }

    fun cutOut(source: File, target: File): Result {
        val bitmap = decodeImage(source, MAX_PX) ?: return Result.Failed
        val segmenter = runCatching {
            SubjectSegmentation.getClient(
                SubjectSegmenterOptions.Builder()
                    .enableForegroundBitmap()
                    .build(),
            )
        }.getOrNull()
        try {
            if (segmenter != null) {
                val image = InputImage.fromBitmap(bitmap, 0)
                var cut: Bitmap? = null
                var unavailable = false
                for (attempt in 0 until READY_TRIES) {
                    try {
                        cut = Tasks.await(segmenter.process(image), TIMEOUT_SECONDS, TimeUnit.SECONDS).foregroundBitmap
                        break
                    } catch (e: ExecutionException) {
                        val ml = e.cause as? MlKitException
                        if (ml?.errorCode == MlKitException.UNAVAILABLE && attempt + 1 < READY_TRIES) {
                            unavailable = true
                            Thread.sleep(READY_WAIT_MS)
                        } else {
                            unavailable = true
                            break
                        }
                    } catch (e: Exception) {
                        unavailable = true
                        break
                    }
                }
                if (cut != null && hasCutout(cut)) {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        if (!cut.compress(Bitmap.CompressFormat.PNG, 100, out)) return Result.Failed
                    }
                    return Result.Done(target)
                }
                if (!unavailable && cut != null) {
                    return Result.NoSubject
                }
            }
            // Fallback when Google Play services is unavailable or model isn't ready: non-destructive border cutout.
            val fallback = cutOutOnDeviceFallback(bitmap) ?: return Result.NoSubject
            target.parentFile?.mkdirs()
            target.outputStream().use { out ->
                if (!fallback.compress(Bitmap.CompressFormat.PNG, 100, out)) return Result.Failed
            }
            return Result.Done(target)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return Result.Failed
        } catch (e: Exception) {
            return Result.Failed
        } finally {
            runCatching { segmenter?.close() }
        }
    }

    /**
     * On-device border flood-fill background removal fallback.
     * Samples the outer border color and flood-fills inward from the edges to remove a connected background.
     * Preserves 100% of interior subject RGB colors and full opacity; only 1px boundary pixels adjacent to
     * the removed background are softened for anti-aliasing.
     */
    internal fun cutOutOnDeviceFallback(bitmap: Bitmap): Bitmap? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Sample border pixels to estimate the dominant background color.
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        for (x in 0 until w) {
            val top = pixels[x]
            val bot = pixels[(h - 1) * w + x]
            rSum += Color.red(top) + Color.red(bot)
            gSum += Color.green(top) + Color.green(bot)
            bSum += Color.blue(top) + Color.blue(bot)
            count += 2
        }
        for (y in 1 until h - 1) {
            val left = pixels[y * w]
            val right = pixels[y * w + (w - 1)]
            rSum += Color.red(left) + Color.red(right)
            gSum += Color.green(left) + Color.green(right)
            bSum += Color.blue(left) + Color.blue(right)
            count += 2
        }
        if (count == 0) return null
        val bgR = (rSum / count).toInt()
        val bgG = (gSum / count).toInt()
        val bgB = (bSum / count).toInt()

        // Measure border variance to adapt threshold safely without eating into colorful subjects.
        var varSum = 0.0
        for (x in 0 until w step maxOf(1, w / 32)) {
            varSum += colorDist(pixels[x], bgR, bgG, bgB)
            varSum += colorDist(pixels[(h - 1) * w + x], bgR, bgG, bgB)
        }
        val avgBorderDist = (varSum / (2.0 * (w / maxOf(1, w / 32)).coerceAtLeast(1))).toFloat()
        val threshold = (avgBorderDist * 1.6f + 22f).coerceIn(24f, 54f)

        val visited = BooleanArray(w * h)
        val queue = ArrayDeque<Int>(w * 2 + h * 2)

        fun tryEnqueue(x: Int, y: Int) {
            if (x !in 0 until w || y !in 0 until h) return
            val idx = y * w + x
            if (visited[idx]) return
            val dist = colorDist(pixels[idx], bgR, bgG, bgB)
            if (dist <= threshold) {
                visited[idx] = true
                queue.add(idx)
            }
        }

        for (x in 0 until w) {
            tryEnqueue(x, 0)
            tryEnqueue(x, h - 1)
        }
        for (y in 1 until h - 1) {
            tryEnqueue(0, y)
            tryEnqueue(w - 1, y)
        }

        var removed = 0
        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            removed++
            val x = idx % w
            val y = idx / w
            tryEnqueue(x - 1, y)
            tryEnqueue(x + 1, y)
            tryEnqueue(x, y - 1)
            tryEnqueue(x, y + 1)
        }

        val total = w * h
        // Require both a meaningful background removal and an intact central subject.
        if (removed < total / 12 || removed > total * 9 / 10) return null
        var centerKept = 0
        var centerTotal = 0
        for (y in (h / 4) until (h * 3 / 4)) {
            val row = y * w
            for (x in (w / 4) until (w * 3 / 4)) {
                centerTotal++
                if (!visited[row + x]) centerKept++
            }
        }
        if (centerTotal > 0 && centerKept < centerTotal / 4) return null

        val softThreshold = threshold * 1.25f
        val outPixels = pixels.copyOf()
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val idx = row + x
                if (visited[idx]) {
                    outPixels[idx] = Color.TRANSPARENT
                } else {
                    // Only feather pixels on the 1px boundary directly adjacent to visited background.
                    val onBoundary = (x > 0 && visited[idx - 1]) ||
                        (x + 1 < w && visited[idx + 1]) ||
                        (y > 0 && visited[idx - w]) ||
                        (y + 1 < h && visited[idx + w])
                    if (onBoundary) {
                        val p = pixels[idx]
                        val d = colorDist(p, bgR, bgG, bgB)
                        if (d < softThreshold) {
                            val factor = ((d - threshold * 0.8f) / (softThreshold - threshold * 0.8f)).coerceIn(0.35f, 1f)
                            val newA = (Color.alpha(p) * factor).toInt().coerceIn(0, 255)
                            outPixels[idx] = Color.argb(newA, Color.red(p), Color.green(p), Color.blue(p))
                        }
                    }
                }
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun colorDist(c: Int, r: Int, g: Int, b: Int): Float {
        val dr = (Color.red(c) - r).toFloat()
        val dg = (Color.green(c) - g).toFloat()
        val db = (Color.blue(c) - b).toFloat()
        return sqrt(dr * dr * 0.30f + dg * dg * 0.59f + db * db * 0.11f)
    }

    /** True when the mask kept a subject and removed a background (neither all transparent nor all opaque). */
    internal fun hasCutout(b: Bitmap): Boolean {
        val stepX = (b.width / SAMPLE_GRID).coerceAtLeast(1)
        val stepY = (b.height / SAMPLE_GRID).coerceAtLeast(1)
        var kept = 0
        var clear = 0
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val a = Color.alpha(b.getPixel(x, y))
                if (a >= 128) kept++ else if (a < 32) clear++
                x += stepX
            }
            y += stepY
        }
        val total = (kept + clear).coerceAtLeast(1)
        return kept * 20 >= total && clear * 20 >= total // each at least 5 % of the picture
    }

    private const val MAX_PX = 1600
    private const val SAMPLE_GRID = 48
    private const val TIMEOUT_SECONDS = 5L
    private const val READY_TRIES = 8
    private const val READY_WAIT_MS = 150L
}
