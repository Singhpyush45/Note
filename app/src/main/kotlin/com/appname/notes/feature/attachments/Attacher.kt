package com.appname.notes.feature.attachments

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.appname.notes.R
import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.domain.model.Attachment
import com.appname.notes.core.domain.model.AttachmentId
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.core.domain.model.NewAttachment
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.repository.AttachmentRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.withContext

/** The message shown when a file can't be attached. */
val AttachError.messageRes: Int
    @StringRes get() = when (this) {
        AttachError.UNREADABLE -> R.string.attach_error_unreadable
        AttachError.TOO_LARGE -> R.string.attach_error_too_large
        AttachError.STORAGE_FULL -> R.string.attach_error_storage_full
        AttachError.STORAGE -> R.string.attach_error_storage
    }

/** What happened when attaching several files: how many made it, and why the others didn't. */
data class AttachOutcome(val added: List<Attachment>, val errors: List<AttachError>) {
    /** One message for the user: the first problem (others are usually the same), or null if all worked. */
    val errorMessage: Int? get() = errors.firstOrNull()?.messageRes
}

/** Attach use case: files into storage, then rows in the database; files whose row fails are removed again. */
class Attacher(
    private val store: AttachmentFileStore,
    private val repository: AttachmentRepository,
    private val dispatchers: DispatcherProvider,
    /** Called after files were attached (starts text indexing). */
    private val onAttached: () -> Unit = {},
) {
    suspend fun attach(noteId: NoteId, uris: List<Uri>): AttachOutcome = withContext(dispatchers.io) {
        val (ok, errors) = importAll(uris)
        if (ok.isEmpty()) return@withContext AttachOutcome(emptyList(), errors)
        when (val r = repository.add(noteId, ok)) {
            is AppResult.Success -> AttachOutcome(r.value, errors).also { onAttached() }
            is AppResult.Failure -> {
                ok.forEach { store.file(it.fileName).delete() }
                AttachOutcome(emptyList(), errors + AttachError.STORAGE)
            }
        }
    }

    /** New note holding [uris] (photos/files shared from another app). Null note when nothing could be attached. */
    suspend fun createNote(notebookId: NotebookId?, uris: List<Uri>): Pair<NoteId?, AttachOutcome> = withContext(dispatchers.io) {
        val (ok, errors) = importAll(uris)
        if (ok.isEmpty()) return@withContext null to AttachOutcome(emptyList(), errors)
        when (val r = repository.createNoteWith(notebookId, "", ok)) {
            is AppResult.Success -> (r.value to AttachOutcome(emptyList(), errors)).also { onAttached() }
            is AppResult.Failure -> {
                ok.forEach { store.file(it.fileName).delete() }
                null to AttachOutcome(emptyList(), errors + AttachError.STORAGE)
            }
        }
    }

    /**
     * Attaches a finished recording (FR-012) with the id its markers already use. Safe to repeat: if it is
     * already attached (an earlier try succeeded), or its note no longer exists, that counts as done.
     */
    suspend fun attachRecording(noteId: NoteId, file: File, id: AttachmentId, displayName: String, durationMs: Long): AttachOutcome =
        withContext(dispatchers.io) {
            val existing = (repository.list(noteId) as? AppResult.Success)?.value
            if (existing != null && existing.any { it.id == id }) return@withContext AttachOutcome(emptyList(), emptyList())
            when (val r = store.import(Uri.fromFile(file))) {
                is AttachResult.Failed -> AttachOutcome(emptyList(), listOf(r.error))
                is AttachResult.Ok -> {
                    val recording = r.attachment.copy(
                        kind = AttachmentKind.AUDIO,
                        displayName = displayName,
                        mimeType = "audio/aac",
                        durationMs = durationMs,
                        id = id,
                    )
                    when (val added = repository.add(noteId, listOf(recording))) {
                        is AppResult.Success -> AttachOutcome(added.value, emptyList())
                        is AppResult.Failure -> {
                            store.file(recording.fileName).delete()
                            // The note was deleted meanwhile: nothing to attach to, nothing to retry.
                            if (added.error is DomainError.NotFound) AttachOutcome(emptyList(), emptyList())
                            else AttachOutcome(emptyList(), listOf(AttachError.STORAGE))
                        }
                    }
                }
            }
        }

    private fun importAll(uris: List<Uri>): Pair<List<NewAttachment>, List<AttachError>> {
        val ok = ArrayList<NewAttachment>()
        val errors = ArrayList<AttachError>()
        for (uri in uris) {
            when (val r = store.import(uri)) {
                is AttachResult.Ok -> ok.add(r.attachment)
                is AttachResult.Failed -> errors.add(r.error)
            }
        }
        return ok to errors
    }

    /** Removes stored attachment files nothing uses any more. Safe to call any time. */
    suspend fun cleanUp(): Int = withContext(dispatchers.io) {
        when (val refs = repository.referencedFiles()) {
            is AppResult.Success -> store.deleteUnreferenced(refs.value)
            is AppResult.Failure -> 0
        }
    }
}

/**
 * Opening and sharing one attachment with another app. The app gets a content link to that single file with
 * read-only access, through the FileProvider; no storage permission is involved on either side.
 */
class AttachmentIntents(private val context: Context, private val store: AttachmentFileStore) {

    fun contentUri(attachment: Attachment): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", store.file(attachment.fileName), attachment.displayName)

    fun viewIntent(attachment: Attachment): Intent = withRead(
        Intent(Intent.ACTION_VIEW).setDataAndType(contentUri(attachment), attachment.mimeType),
        attachment,
    )

    fun shareIntent(attachment: Attachment, chooserTitle: String): Intent = Intent.createChooser(
        withRead(Intent(Intent.ACTION_SEND).setType(attachment.mimeType).putExtra(Intent.EXTRA_STREAM, contentUri(attachment)), attachment),
        chooserTitle,
    )

    /** Starts [intent]; false when no installed app can handle it (the caller says so). */
    fun start(activityContext: Context, intent: Intent): Boolean = try {
        activityContext.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun withRead(intent: Intent, attachment: Attachment): Intent = intent.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri(attachment.displayName, contentUri(attachment))
    }

    /**
     * Where "Take photo" writes before the photo is attached (then copied into storage and deleted). Named by
     * date and time, which becomes the attachment's name.
     */
    fun newCameraFile(): Pair<File, Uri> {
        val dir = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // leftovers of earlier captures
        val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.ROOT).format(Date())
        val file = File(dir, "Photo $stamp.jpg")
        return file to uriFor(file)
    }

    /** Content link for a file in a FileProvider folder (camera photos). */
    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    private companion object {
        const val CAMERA_DIR = "camera"
    }
}
