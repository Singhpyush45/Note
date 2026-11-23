package com.appname.notes.feature.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The backup file (FR-025): one zip with the database, the note files (attachments, PDFs, recordings, wallpapers)
 * and the settings, plus `manifest.json` listing every file with its size and SHA-256. A restore checks every file
 * against the manifest before anything on the phone is touched. Pure java.io, so it is tested on the JVM.
 */
object BackupArchive {
    const val FORMAT = 1
    const val MANIFEST = "manifest.json"

    @Serializable
    data class Entry(val path: String, val size: Long, val sha256: String)

    @Serializable
    data class Manifest(
        val format: Int,
        val app: String,
        val appVersion: String,
        val dbVersion: Int,
        val createdAt: Long,
        val entries: List<Entry>,
    ) {
        val totalBytes: Long get() = entries.sumOf { it.size }
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Writes [files] (path in the zip → file on disk) and the manifest to [out]. Photos and PDFs are already
     * compressed, so they are stored; the database and settings are deflated.
     */
    fun write(
        out: OutputStream,
        files: List<Pair<String, File>>,
        appVersion: String,
        dbVersion: Int,
        createdAt: Long,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Manifest {
        val total = files.sumOf { it.second.length() }
        var done = 0L
        val entries = ArrayList<Entry>(files.size)
        val zip = ZipOutputStream(out.buffered(BUFFER))
        val buf = ByteArray(BUFFER)
        for ((path, file) in files) {
            require(safePath(path)) { "Bad path $path" }
            val digest = MessageDigest.getInstance("SHA-256")
            zip.putNextEntry(ZipEntry(path))
            var size = 0L
            file.inputStream().use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    zip.write(buf, 0, n)
                    digest.update(buf, 0, n)
                    size += n
                    done += n
                    onProgress(done, total)
                }
            }
            zip.closeEntry()
            entries += Entry(path, size, digest.digest().hex())
        }
        val manifest = Manifest(FORMAT, APP, appVersion, dbVersion, createdAt, entries)
        zip.putNextEntry(ZipEntry(MANIFEST))
        zip.write(json.encodeToString(Manifest.serializer(), manifest).toByteArray())
        zip.closeEntry()
        zip.finish()
        zip.flush()
        return manifest
    }

    sealed interface ReadResult {
        data class Ok(val manifest: Manifest) : ReadResult
        /** Not a backup of this app, damaged, or a file doesn't match its checksum. */
        data class Damaged(val reason: String) : ReadResult
        /** Made by a newer version of the app (newer backup format or database). */
        data object TooNew : ReadResult
    }

    /**
     * Unpacks [input] into the empty folder [into] and checks it: manifest present, same app, known format, database
     * not newer than [maxDbVersion], and every listed file present with the right size and SHA-256. Files in the zip
     * that the manifest doesn't list are dropped. Nothing outside [into] is written (zip-slip paths are refused).
     */
    fun extract(input: InputStream, into: File, maxDbVersion: Int, maxBytes: Long = Long.MAX_VALUE): ReadResult {
        into.mkdirs()
        val buf = ByteArray(BUFFER)
        val hashes = HashMap<String, Pair<Long, String>>()
        var manifestText: String? = null
        var written = 0L
        try {
            ZipInputStream(input.buffered(BUFFER)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (name == MANIFEST) {
                        manifestText = zip.readBytes().toString(Charsets.UTF_8)
                        continue
                    }
                    if (!safePath(name)) return ReadResult.Damaged("bad path")
                    val target = File(into, name)
                    target.parentFile?.mkdirs()
                    val digest = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    target.outputStream().use { o ->
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            size += n
                            written += n
                            if (written > maxBytes) return ReadResult.Damaged("too big")
                        }
                    }
                    hashes[name] = size to digest.digest().hex()
                }
            }
        } catch (e: java.io.IOException) {
            return ReadResult.Damaged(e.message ?: "unreadable")
        }
        val text = manifestText ?: return ReadResult.Damaged("no manifest")
        val manifest = try {
            json.decodeFromString(Manifest.serializer(), text)
        } catch (e: Exception) {
            return ReadResult.Damaged("bad manifest")
        }
        if (manifest.app != APP) return ReadResult.Damaged("other app")
        if (manifest.format > FORMAT || manifest.dbVersion > maxDbVersion) return ReadResult.TooNew
        for (e in manifest.entries) {
            val got = hashes[e.path] ?: return ReadResult.Damaged("missing ${e.path}")
            if (got.first != e.size || got.second != e.sha256) return ReadResult.Damaged("checksum ${e.path}")
        }
        // Drop anything the manifest doesn't list.
        val listed = manifest.entries.mapTo(HashSet()) { it.path }
        hashes.keys.filter { it !in listed }.forEach { File(into, it).delete() }
        if (manifest.entries.none { it.path == DB_PATH }) return ReadResult.Damaged("no database")
        return ReadResult.Ok(manifest)
    }

    /** Relative, forward-slash, no `..`, no empty parts: can't escape the folder it is unpacked into. */
    fun safePath(path: String): Boolean {
        if (path.isEmpty() || path.startsWith("/") || path.contains('\\') || path.contains(':')) return false
        return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    const val APP = "com.appname.notes"
    const val DB_PATH = "db/notes.db"
    private const val BUFFER = 64 * 1024
}
