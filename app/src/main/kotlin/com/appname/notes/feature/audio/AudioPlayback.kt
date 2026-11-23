package com.appname.notes.feature.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.appname.notes.core.domain.model.Attachment
import com.appname.notes.core.domain.model.AttachmentId
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the player shows: which recording, where, and whether it is playing. */
data class PlaybackState(
    val attachmentId: AttachmentId? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    /** Playback speed for all recordings of the note (kept when switching recordings). */
    val speed: Float = 1f,
)

/**
 * Plays one recording at a time for an open note (FR-012), with seeking (marker taps jump here).
 * Main thread only; [release] when the note closes.
 */
class AudioPlayback(private val scope: CoroutineScope, private val fileOf: (Attachment) -> File, private val onError: () -> Unit) {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    /** Plays [attachment] from [fromMs] (or from where it was paused when null). */
    fun play(attachment: Attachment, fromMs: Long? = null) {
        val current = player
        if (current != null && _state.value.attachmentId == attachment.id) {
            fromMs?.let { current.seekTo(it.toInt()) }
            current.start()
            applySpeed(current)
            started()
            return
        }
        release()
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_MEDIA).build())
            p.setDataSource(fileOf(attachment).path)
            p.prepare() // a local file: fast
        } catch (e: IOException) {
            p.release()
            onError()
            return
        } catch (e: RuntimeException) {
            p.release()
            onError()
            return
        }
        p.setOnCompletionListener { _state.value = _state.value.copy(playing = false, positionMs = _state.value.durationMs) ; ticker?.cancel() }
        player = p
        val duration = p.duration.toLong().takeIf { it > 0 } ?: attachment.durationMs ?: 0
        _state.value = PlaybackState(attachment.id, 0, duration, playing = false, speed = _state.value.speed)
        fromMs?.let { p.seekTo(it.toInt()) }
        p.start()
        applySpeed(p)
        started()
    }

    /**
     * Sets the speed ([SPEEDS]). Applied at once while playing; otherwise at the next play (setting it on a paused
     * player would start playback on some Android versions).
     */
    fun setSpeed(speed: Float) {
        _state.value = _state.value.copy(speed = speed)
        player?.takeIf { it.isPlaying }?.let { applySpeed(it) }
    }

    private fun applySpeed(p: MediaPlayer) {
        try {
            p.playbackParams = p.playbackParams.setSpeed(_state.value.speed)
        } catch (e: IllegalStateException) {
            // player not ready: applied at the next start
        } catch (e: IllegalArgumentException) { // speed not supported by this decoder
            _state.value = _state.value.copy(speed = 1f)
        }
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        ticker?.cancel()
        _state.value = _state.value.copy(playing = false, positionMs = player?.currentPosition?.toLong() ?: _state.value.positionMs)
    }

    fun seekTo(attachment: Attachment, ms: Long) {
        if (_state.value.attachmentId != attachment.id || player == null) {
            play(attachment, ms)
            return
        }
        player?.seekTo(ms.toInt())
        _state.value = _state.value.copy(positionMs = ms)
    }

    fun release() {
        ticker?.cancel()
        player?.release()
        player = null
        _state.value = PlaybackState(speed = _state.value.speed)
    }

    private fun started() {
        _state.value = _state.value.copy(playing = true, positionMs = player?.currentPosition?.toLong() ?: 0)
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val p = player ?: break
                _state.value = _state.value.copy(positionMs = p.currentPosition.toLong())
                delay(TICK_MS)
            }
        }
    }

    companion object {
        private const val TICK_MS = 250L

        /** The speeds offered, in the order the speed button cycles through them. */
        val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)

        fun nextSpeed(current: Float): Float = SPEEDS[(SPEEDS.indexOf(current).coerceAtLeast(0) + 1) % SPEEDS.size]

        /** "1×", "1.25×", "0.75×". */
        fun label(speed: Float): String =
            (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString().trimEnd('0')) + "×"
    }
}
