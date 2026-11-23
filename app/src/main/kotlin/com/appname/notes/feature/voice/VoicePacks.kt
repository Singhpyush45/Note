package com.appname.notes.feature.voice

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Offline speech models for the built-in transcriber (Vosk, Apache-2.0). Downloaded once on the user's tap
 * (tens of MB), kept in the app's private storage; after that transcription works without internet and no audio
 * ever leaves the phone.
 */
enum class VoicePack(val id: String, val sizeMb: Int) {
    ENGLISH_INDIA("vosk-model-small-en-in-0.4", 36),
    ENGLISH_US("vosk-model-small-en-us-0.15", 40),
    HINDI("vosk-model-small-hi-0.22", 43),
    ;

    val url: String get() = "https://alphacephei.com/vosk/models/$id.zip"
}

/** Languages offered (§11 + owner request): English (India/US/UK), Hindi, Hinglish (Hindi in English letters). */
enum class VoiceLanguage(val key: String, val pack: VoicePack, val roman: Boolean = false) {
    ENGLISH_INDIA("en-IN", VoicePack.ENGLISH_INDIA),
    HINDI("hi-IN", VoicePack.HINDI),
    HINGLISH("hi-Latn-IN", VoicePack.HINDI, roman = true),
    ENGLISH_US("en-US", VoicePack.ENGLISH_US),
    ENGLISH_UK("en-GB", VoicePack.ENGLISH_US),
    ;

    val hindiScript: Boolean get() = pack == VoicePack.HINDI && !roman

    companion object {
        fun fromTag(key: String?): VoiceLanguage? = entries.firstOrNull { it.key == key }

        /** English (India) for phones set to India, otherwise English (US). */
        fun defaultFor(locale: Locale): VoiceLanguage = when {
            locale.language == "hi" -> HINDI
            locale.country == "IN" -> ENGLISH_INDIA
            locale.country == "GB" -> ENGLISH_UK
            else -> ENGLISH_US
        }
    }
}

object VoicePacks {
    private const val DIR = "voice-packs"
    private const val READY = "READY"

    /** Download progress 0..1 per pack being downloaded. */
    private val _progress = MutableStateFlow<Map<VoicePack, Float>>(emptyMap())
    val progress: StateFlow<Map<VoicePack, Float>> = _progress.asStateFlow()

    fun dir(context: Context, pack: VoicePack) = File(File(context.filesDir, DIR), pack.id)

    fun installed(context: Context, pack: VoicePack) = File(dir(context, pack), READY).exists()

    /** Downloads and unpacks [pack]; true when it is ready. Safe to call again (resumes from scratch). */
    suspend fun download(context: Context, pack: VoicePack): Boolean = withContext(Dispatchers.IO) {
        if (installed(context, pack)) return@withContext true
        if (_progress.value.containsKey(pack)) return@withContext false
        _progress.update { it + (pack to 0f) }
        val zip = File(context.cacheDir, "${pack.id}.zip")
        val target = dir(context, pack)
        val staging = File(target.parentFile, "${pack.id}.part")
        try {
            fetch(pack.url, zip) { done, total -> if (total > 0) _progress.update { it + (pack to 0.9f * done / total) } }
            staging.deleteRecursively()
            unzip(zip, staging)
            // The zip has one top folder (the model's name); its contents are the model.
            val root = staging.listFiles()?.singleOrNull { it.isDirectory } ?: staging
            target.deleteRecursively()
            target.parentFile?.mkdirs()
            if (!root.renameTo(target)) throw IOException("move")
            File(target, READY).writeText(pack.id)
            true
        } catch (e: Exception) {
            target.deleteRecursively()
            false
        } finally {
            zip.delete()
            staging.deleteRecursively()
            _progress.update { it - pack }
        }
    }

    fun delete(context: Context, pack: VoicePack) {
        dir(context, pack).deleteRecursively()
    }

    private fun fetch(url: String, into: File, onProgress: (Long, Long) -> Unit) {
        var current = url
        repeat(5) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
            }
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    current = c.getHeaderField("Location")?.let { URL(URL(current), it).toString() } ?: throw IOException("redirect")
                    if (!current.startsWith("https://")) throw IOException("insecure redirect")
                    return@repeat
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                val total = c.contentLengthLong
                c.inputStream.use { input ->
                    into.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
                return
            } finally {
                c.disconnect()
            }
        }
        throw IOException("too many redirects")
    }

    /** Unpacks [zip] into [into]; refuses entries that would land outside it. */
    private fun unzip(zip: File, into: File) {
        into.mkdirs()
        val base = into.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val out = File(into, e.name)
                if (!out.canonicalPath.startsWith(base)) throw IOException("bad path")
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { z.copyTo(it) }
                }
            }
        }
    }
}
