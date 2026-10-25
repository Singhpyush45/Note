package com.appname.notes.feature.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Google sign-in for Drive backup (Phase 9). Asks only for `drive.appdata`: a hidden folder in the user's own Drive
 * that only this app can see — the app cannot read any of the user's other Drive files. No API key or secret is in
 * the app: Google recognises it by package name and signing certificate (registered by the owner in Google Cloud).
 */
object DriveAuth {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    sealed interface Result {
        data class Token(val accessToken: String) : Result
        /** Google wants the user to pick an account / agree; launch this, then call [fromIntent]. */
        data class NeedsConsent(val intent: PendingIntent) : Result
        /** This build is not registered with Google (OAuth client missing): see docs/phase-9-report.md. */
        data object NotRegistered : Result
        data object NoPlayServices : Result
        data class Failed(val message: String) : Result
    }

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Gets an access token; without UI if the user already agreed before. */
    suspend fun authorize(context: Context): Result = suspendCancellableCoroutine { cont ->
        val task = try {
            Identity.getAuthorizationClient(context).authorize(request())
        } catch (e: Exception) {
            cont.resume(Result.NoPlayServices)
            return@suspendCancellableCoroutine
        }
        task.addOnSuccessListener { r -> cont.resume(toResult(r)) }
        task.addOnFailureListener { e -> cont.resume(failure(e)) }
    }

    /** The result of the consent screen. */
    fun fromIntent(context: Context, data: Intent?): Result = try {
        toResult(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (e: Exception) {
        failure(e)
    }

    private fun toResult(r: AuthorizationResult): Result {
        val pending = r.pendingIntent
        if (r.hasResolution() && pending != null) return Result.NeedsConsent(pending)
        val token = r.accessToken ?: return Result.Failed("no token")
        return Result.Token(token)
    }

    private fun failure(e: Exception): Result {
        if (e is ApiException) {
            return when (e.statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR -> Result.NotRegistered
                CommonStatusCodes.API_NOT_CONNECTED, CommonStatusCodes.SERVICE_DISABLED, CommonStatusCodes.SERVICE_VERSION_UPDATE_REQUIRED -> Result.NoPlayServices
                CommonStatusCodes.CANCELED -> Result.Failed("cancelled")
                else -> Result.Failed("code ${e.statusCode}")
            }
        }
        return Result.Failed(e.message ?: e.javaClass.simpleName)
    }

    /** Cancels the app's access to the user's Drive (Google's standard token revocation endpoint). */
    fun revoke(token: String) {
        val c = URL("https://oauth2.googleapis.com/revoke").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write("token=${URLEncoder.encode(token, "UTF-8")}".toByteArray()) }
            c.responseCode
        } finally {
            c.disconnect()
        }
    }
}

/** One backup file in the app's Drive folder. */
data class DriveFile(val id: String, val name: String, val size: Long, val createdTime: String)

/** Thrown for an HTTP error from Drive; [code] 401 means the token expired or access was removed. */
class DriveException(val code: Int, message: String) : IOException(message)

/**
 * The few Drive REST calls the backup needs, over HttpURLConnection (no extra library): list, resumable upload in
 * chunks (works for large backups and survives slow networks better), download, delete, and the account's email.
 */
class DriveClient(private val token: String) {
    private val json = Json { ignoreUnknownKeys = true }

    fun email(): String? {
        val o = getJson("$API/about?fields=user(emailAddress)")
        return o["user"]?.jsonObject?.get("emailAddress")?.jsonPrimitive?.content
    }

    fun list(): List<DriveFile> {
        val q = URLEncoder.encode("name contains '$PREFIX' and trashed = false", "UTF-8")
        val o = getJson("$API/files?spaces=appDataFolder&q=$q&orderBy=createdTime%20desc&pageSize=100&fields=files(id,name,size,createdTime)")
        return o["files"]?.jsonArray?.map { f ->
            val x = f.jsonObject
            DriveFile(
                id = x["id"]!!.jsonPrimitive.content,
                name = x["name"]?.jsonPrimitive?.content.orEmpty(),
                size = x["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                createdTime = x["createdTime"]?.jsonPrimitive?.content.orEmpty(),
            )
        }.orEmpty()
    }

    fun upload(file: File, name: String, onProgress: (Long, Long) -> Unit = { _, _ -> }): String {
        val total = file.length()
        // 1. Start a resumable session.
        val start = open("$UPLOAD/files?uploadType=resumable&fields=id", "POST")
        start.doOutput = true
        start.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        start.setRequestProperty("X-Upload-Content-Type", "application/zip")
        start.setRequestProperty("X-Upload-Content-Length", total.toString())
        start.outputStream.use { it.write("""{"name":"$name","parents":["appDataFolder"]}""".toByteArray()) }
        check(start)
        val session = start.getHeaderField("Location") ?: throw DriveException(0, "no upload session")
        start.disconnect()
        // 2. Send the file in chunks (multiples of 256 KiB, as Drive requires).
        if (total == 0L) throw DriveException(0, "empty file")
        file.inputStream().use { input ->
            val buf = ByteArray(CHUNK)
            var sent = 0L
            while (sent < total) {
                var n = 0
                while (n < CHUNK) {
                    val r = input.read(buf, n, CHUNK - n)
                    if (r < 0) break
                    n += r
                }
                val c = URL(session).openConnection() as HttpURLConnection
                c.requestMethod = "PUT"
                c.doOutput = true
                c.connectTimeout = TIMEOUT
                c.readTimeout = TIMEOUT
                c.setFixedLengthStreamingMode(n)
                c.setRequestProperty("Content-Range", "bytes $sent-${sent + n - 1}/$total")
                c.outputStream.use { it.write(buf, 0, n) }
                val code = c.responseCode
                sent += n
                onProgress(sent, total)
                if (code == 308) { c.disconnect(); continue }
                if (code !in 200..299) throw DriveException(code, error(c))
                val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                c.disconnect()
                return json.parseToJsonElement(body).jsonObject["id"]!!.jsonPrimitive.content
            }
        }
        throw DriveException(0, "upload ended early")
    }

    fun download(id: String, out: OutputStream, onProgress: (Long) -> Unit = {}) {
        val c = open("$API/files/$id?alt=media", "GET")
        check(c)
        c.inputStream.use { input ->
            val buf = ByteArray(64 * 1024)
            var done = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                done += n
                onProgress(done)
            }
        }
        c.disconnect()
    }

    fun delete(id: String) {
        val c = open("$API/files/$id", "DELETE")
        check(c)
        c.disconnect()
    }

    private fun getJson(url: String): JsonObject {
        val c = open(url, "GET")
        check(c)
        val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        c.disconnect()
        return json.parseToJsonElement(body).jsonObject
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun check(c: HttpURLConnection) {
        val code = c.responseCode
        if (code !in 200..299) throw DriveException(code, error(c)).also { c.disconnect() }
    }

    private fun error(c: HttpURLConnection): String =
        "HTTP ${c.responseCode}: " + (c.errorStream?.use { it.readBytes().toString(Charsets.UTF_8).take(300) } ?: "")

    companion object {
        const val PREFIX = "APP_NAME-backup-"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val CHUNK = 8 * 1024 * 1024
        private const val TIMEOUT = 60_000
    }
}
