package com.appname.notes.feature.editor

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Web clipping (8c): for a link in a typed note, reads the page's title, summary and preview picture (the tags web
 * pages publish for link previews). Only when the user taps "Clip page" — the app fetches nothing on its own.
 */
object WebClipper {
    data class Clip(val title: String, val description: String?, val imageUrl: String?, val url: String)

    /** Fetches and reads [url]; null if it isn't reachable or isn't a web page. Blocking: call off the main thread. */
    fun fetch(url: String): Clip? {
        val (finalUrl, bytes, type) = get(url, MAX_PAGE) ?: return null
        if (type != null && !type.contains("html", ignoreCase = true)) return null
        val charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(type.orEmpty())?.groupValues?.get(1)
        val html = runCatching { String(bytes, charset(charset ?: "UTF-8")) }.getOrElse { String(bytes, Charsets.UTF_8) }
        return parse(html, finalUrl)
    }

    /** Downloads a picture (at most [MAX_IMAGE]) as [name] + its extension in [dir]; null if it fails or isn't a picture. */
    fun download(url: String, dir: File, name: String): File? {
        val (_, bytes, type) = get(url, MAX_IMAGE) ?: return null
        val ext = when {
            type == null -> return null
            type.startsWith("image/png", true) -> "png"
            type.startsWith("image/jpeg", true) || type.startsWith("image/jpg", true) -> "jpg"
            type.startsWith("image/webp", true) -> "webp"
            type.startsWith("image/gif", true) -> "gif"
            else -> return null
        }
        dir.mkdirs()
        val safe = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").trim().take(60).ifBlank { "Web page" }
        return File(dir, "$safe.$ext").also { it.writeBytes(bytes) }
    }

    /** Title, summary and picture from a page's HTML (Open Graph tags first, then the usual ones). */
    fun parse(html: String, url: String): Clip? {
        val head = html.take(MAX_HEAD)
        fun meta(vararg names: String): String? {
            for (n in names) {
                val a = Regex("""<meta[^>]+(?:property|name)\s*=\s*["']${Regex.escape(n)}["'][^>]*>""", RegexOption.IGNORE_CASE).find(head)?.value
                val content = a?.let { Regex("""content\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
                if (!content.isNullOrBlank()) return decode(content).trim()
            }
            return null
        }
        val title = meta("og:title", "twitter:title")
            ?: Regex("""<title[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)?.let { decode(it).trim() }
            ?: return null
        val description = meta("og:description", "twitter:description", "description")
        val image = meta("og:image", "twitter:image")?.let { resolve(url, it) }
        return Clip(title.replace(Regex("\\s+"), " ").take(300), description?.replace(Regex("\\s+"), " ")?.take(600), image, url)
    }

    private fun resolve(base: String, href: String): String? = runCatching { URL(URL(base), href).toString() }.getOrNull()
        ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

    private val entities = mapOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&nbsp;" to " ")

    private fun decode(s: String): String {
        var out = s
        entities.forEach { (k, v) -> out = out.replace(k, v) }
        out = Regex("&#(\\d+);").replace(out) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        out = Regex("&#x([0-9a-fA-F]+);").replace(out) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
        return out
    }

    /** GET with up to 5 redirects; returns the final URL, at most [limit] bytes and the content type. */
    private fun get(url: String, limit: Int): Triple<String, ByteArray, String?>? {
        // HTTPS only (network security config): plain-http links are tried over HTTPS.
        var current = if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
        repeat(MAX_REDIRECTS) {
            if (!current.startsWith("https://") && !current.startsWith("http://")) return null
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) NotesApp-LinkPreview")
                setRequestProperty("Accept", "text/html,image/*;q=0.9,*/*;q=0.5")
            }
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    current = c.getHeaderField("Location")?.let { resolve(current, it) }?.let { if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it } ?: return null
                    return@repeat
                }
                if (code !in 200..299) return null
                val out = ByteArrayOutputStream()
                c.inputStream.use { input ->
                    val buf = ByteArray(16 * 1024)
                    while (out.size() < limit) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, minOf(n, limit - out.size()))
                    }
                }
                return Triple(current, out.toByteArray(), c.contentType)
            } catch (e: IOException) {
                return null
            } finally {
                c.disconnect()
            }
        }
        return null
    }

    private const val MAX_REDIRECTS = 5
    private const val TIMEOUT = 10_000
    private const val MAX_PAGE = 1_500_000
    private const val MAX_HEAD = 300_000
    private const val MAX_IMAGE = 8_000_000
}
