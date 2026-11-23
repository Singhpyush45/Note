package com.appname.notes.core.performance

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Step 3 — Production LRU Bitmap Cache & Safe Downsampler (`BitmapMemoryCache`).
 * Prevents `OutOfMemoryError` and scroll jank when rendering libraries with 100+ handwritten
 * note thumbnails, cover previews, and inline note images.
 */
object BitmapMemoryCache {

    private val maxCacheKb: Int = run {
        val maxMemKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        (maxMemKb / 8).coerceIn(16 * 1024, 64 * 1024)
    }

    private val cache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    fun get(key: String): Bitmap? {
        val bmp = cache.get(key)
        return if (bmp != null && !bmp.isRecycled) bmp else null
    }

    fun put(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            cache.put(key, bitmap)
        }
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.evictAll()
    }

    /**
     * Call from `Application.onTrimMemory(level)` to release thumbnail cache cleanly under RAM pressure.
     */
    fun onTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> cache.evictAll()
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> cache.trimToSize(maxCacheKb / 2)
        }
    }

    /**
     * Computes the largest power-of-two `inSampleSize` keeping decoded dimensions >= target.
     */
    fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        if (srcWidth <= 0 || srcHeight <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    suspend fun loadSampledFromFile(
        file: File,
        reqWidthPx: Int,
        reqHeightPx: Int,
        preferRgb565: Boolean = false,
        cacheKey: String = "${file.absolutePath}_${reqWidthPx}x${reqHeightPx}_${file.lastModified()}",
    ): Bitmap? = withContext(Dispatchers.IO) {
        get(cacheKey)?.let { return@withContext it }
        if (!file.exists() || !file.isFile) return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidthPx, reqHeightPx)
            inPreferredConfig = if (preferRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
        if (decoded != null) put(cacheKey, decoded)
        decoded
    }

    suspend fun loadSampledFromUri(
        context: Context,
        uri: Uri,
        reqWidthPx: Int,
        reqHeightPx: Int,
        preferRgb565: Boolean = false,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${uri}_${reqWidthPx}x${reqHeightPx}"
        get(key)?.let { return@withContext it }

        val resolver = context.applicationContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidthPx, reqHeightPx)
            inPreferredConfig = if (preferRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
        if (decoded != null) put(key, decoded)
        decoded
    }
}
