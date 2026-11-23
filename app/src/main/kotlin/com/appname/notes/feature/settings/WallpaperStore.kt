package com.appname.notes.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.common.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/** Photos used as the app's wallpaper (owner brief §19). A small interface so ViewModel tests don't need files. */
interface WallpaperPhotos {
    /** Copies the picked photo in, at about screen size, and returns its stored name; null if it couldn't be read. */
    suspend fun import(uri: Uri): String?

    /** Deletes stored wallpaper photos except [keep] (the one in use). */
    suspend fun deleteAllExcept(keep: String?)

    /** The stored photo at about screen size, or null if it's missing or damaged. */
    suspend fun load(fileName: String): ImageBitmap?
}

/**
 * Stores wallpaper photos in app-private storage as JPEGs no bigger than [MAX_SIDE] pixels (a 48-megapixel photo
 * becomes ~1 MB), turned upright. Only the one in use is kept.
 */
@Singleton
class WallpaperStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger,
) : WallpaperPhotos {

    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    override suspend fun import(uri: Uri): String? = withContext(dispatchers.io) {
        try {
            val bitmap = decode(uri) ?: return@withContext null
            val name = "${UUID.randomUUID()}.jpg"
            val temp = File(dir, "$name.part")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            bitmap.recycle()
            if (!temp.renameTo(File(dir, name))) {
                temp.delete()
                return@withContext null
            }
            name
        } catch (e: IOException) {
            logger.warn(TAG, "Wallpaper photo couldn't be saved", e)
            null
        } catch (e: SecurityException) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    override suspend fun load(fileName: String): ImageBitmap? = withContext(dispatchers.io) {
        val file = File(dir, fileName)
        if (!file.isFile) return@withContext null
        try {
            BitmapFactory.decodeFile(file.path)?.asImageBitmap()
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    override suspend fun deleteAllExcept(keep: String?) = withContext(dispatchers.io) {
        dir.listFiles()?.filter { it.name != keep }?.forEach { it.delete() }
        Unit
    }

    private fun decode(uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the photo's EXIF rotation and decodes at the reduced size directly.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val long = maxOf(info.size.width, info.size.height)
                if (long > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / long
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }

    private companion object {
        const val DIR = "wallpapers"
        const val TAG = "WallpaperStore"
        const val MAX_SIDE = 1600
        const val QUALITY = 88
    }
}
