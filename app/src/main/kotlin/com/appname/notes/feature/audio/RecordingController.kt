package com.appname.notes.feature.audio

import android.content.Context
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.domain.model.AttachmentId
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.feature.attachments.Attacher
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A recording in progress (FR-012). [attachmentId] is chosen up front so markers can refer to it. */
data class ActiveRecording(
    val noteId: NoteId,
    val attachmentId: AttachmentId,
    val file: File,
    val displayName: String,
    /** SystemClock.elapsedRealtime() when audio capture started; null while the recorder is starting. */
    val startedAtElapsed: Long?,
)

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val recording: ActiveRecording) : RecordingState
}

/** Why a recording couldn't start or was lost. */
enum class RecordingError { ALREADY_RECORDING, MICROPHONE_UNAVAILABLE, STORAGE }

/**
 * The one recording that can run at a time, shared by the editor screens and [AudioRecordingService].
 * Recordings are written to `files/recordings/<attachmentId>.aac` with a small sidecar file naming the note,
 * so a recording interrupted by the app being killed is still attached at the next start ([recoverInterrupted]).
 */
class RecordingController(
    private val context: Context,
    private val attacher: Attacher,
    private val ids: IdGenerator,
    private val appScope: CoroutineScope,
    private val logger: AppLogger,
) {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    /** Problems to show (consumed by the screen). */
    private val _errors = MutableStateFlow<RecordingError?>(null)
    val errors: StateFlow<RecordingError?> = _errors.asStateFlow()

    val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    /** Starts recording into [noteId]. The caller has checked the microphone permission. */
    fun start(noteId: NoteId): Boolean {
        if (_state.value is RecordingState.Recording) {
            _errors.value = RecordingError.ALREADY_RECORDING
            return false
        }
        val id = AttachmentId(ids.newId())
        val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.ROOT).format(Date())
        val recording = ActiveRecording(noteId, id, File(dir, "${id.value}.aac"), "Recording $stamp.aac", startedAtElapsed = null)
        try {
            sidecar(recording.file).writeText("${noteId.value}\n${recording.displayName}")
        } catch (e: IOException) {
            _errors.value = RecordingError.STORAGE
            return false
        }
        _state.value = RecordingState.Recording(recording)
        ContextCompat.startForegroundService(context, AudioRecordingService.startIntent(context))
        return true
    }

    /** Asks the service to stop; the recording is then attached to its note. */
    fun stop() {
        if (_state.value is RecordingState.Recording) context.startService(AudioRecordingService.stopIntent(context))
    }

    /** Milliseconds recorded so far (0 while starting or idle). */
    fun elapsedMs(): Long {
        val started = (_state.value as? RecordingState.Recording)?.recording?.startedAtElapsed ?: return 0
        return SystemClock.elapsedRealtime() - started
    }

    fun errorShown() {
        _errors.value = null
    }

    // ------------------------------------------------------------------ called by the service

    internal fun current(): ActiveRecording? = (_state.value as? RecordingState.Recording)?.recording

    internal fun onCaptureStarted(atElapsed: Long) {
        val r = current() ?: return
        _state.value = RecordingState.Recording(r.copy(startedAtElapsed = atElapsed))
    }

    internal fun onCaptureFailed() {
        val r = current() ?: return
        _state.value = RecordingState.Idle
        _errors.value = RecordingError.MICROPHONE_UNAVAILABLE
        r.file.delete()
        sidecar(r.file).delete()
    }

    /** Capture ended with [durationMs] of audio (0 = nothing usable). Attaches it in the background. */
    internal fun onCaptureFinished(durationMs: Long) {
        val r = current() ?: return
        _state.value = RecordingState.Idle
        appScope.launch { finish(r.noteId, r.attachmentId, r.file, r.displayName, durationMs) }
    }

    // ------------------------------------------------------------------ attaching

    private suspend fun finish(noteId: NoteId, id: AttachmentId, file: File, displayName: String, durationMs: Long) {
        if (durationMs <= 0 || !file.isFile || file.length() == 0L) {
            file.delete()
            sidecar(file).delete()
            return
        }
        val outcome = attacher.attachRecording(noteId, file, id, displayName, durationMs)
        if (outcome.errors.isEmpty()) {
            file.delete()
            sidecar(file).delete()
        } else {
            // Keep the file and its sidecar: the next start tries again (recoverInterrupted).
            logger.warn(TAG, "Recording not attached yet: ${outcome.errors}")
            _errors.value = RecordingError.STORAGE
        }
    }

    /**
     * Attaches recordings that never finished normally (the app was killed while recording, or attaching
     * failed). ADTS audio stays playable up to where it stopped. Call at startup, while nothing records.
     */
    suspend fun recoverInterrupted(): Int {
        if (_state.value is RecordingState.Recording) return 0
        var recovered = 0
        dir.listFiles { f -> f.name.endsWith(".aac") }?.forEach { audio ->
            val info = sidecar(audio).takeIf { it.isFile }?.readLines().orEmpty()
            val noteId = info.getOrNull(0)?.takeIf { it.isNotBlank() }
            if (noteId == null) {
                audio.delete()
                return@forEach
            }
            val name = info.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Recording.aac"
            finish(NoteId(noteId), AttachmentId(audio.nameWithoutExtension), audio, name, estimateDurationMs(audio))
            recovered++
        }
        return recovered
    }

    private fun sidecar(audio: File) = File(audio.parentFile, audio.nameWithoutExtension + ".note")

    companion object {
        private const val TAG = "Recording"
        const val DIR = "recordings"
        const val BIT_RATE = 64_000
        const val SAMPLE_RATE = 44_100

        /** From the file size at the constant bit rate we record with (good to about a second). */
        fun estimateDurationMs(file: File): Long = file.length() * 8 * 1000 / BIT_RATE
    }
}
