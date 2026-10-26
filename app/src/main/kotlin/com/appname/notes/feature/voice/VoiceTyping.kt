package com.appname.notes.feature.voice

import android.os.Handler
import android.os.Looper
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Languages offered for voice typing (§11). [key] is what is saved; [tag] is what the recogniser hears;
 * [roman] writes Hindi in English letters (Hinglish).
 */
enum class VoiceLanguage(val key: String, val tag: String, val roman: Boolean = false) {
    ENGLISH_INDIA("en-IN", "en-IN"),
    HINDI("hi-IN", "hi-IN"),
    HINGLISH("hi-Latn-IN", "hi-IN", roman = true),
    ENGLISH_US("en-US", "en-US"),
    ENGLISH_UK("en-GB", "en-GB"),
    ;

    /** What the recogniser returned, written for this option (Hinglish: Devanagari → Roman letters). */
    fun shown(text: String): String = if (roman) Hinglish.romanize(text) else text

    companion object {
        fun fromTag(key: String?): VoiceLanguage? = entries.firstOrNull { it.key == key }

        /** English (India) for phones set to India, otherwise English (US). */
        fun defaultFor(locale: Locale): VoiceLanguage = when (locale.country) {
            "IN" -> ENGLISH_INDIA
            "GB" -> ENGLISH_UK
            else -> ENGLISH_US
        }
    }
}

data class VoiceTypingState(
    val status: Status = Status.READY,
    /** The words being recognised right now (not in the note yet). */
    val partial: String = "",
    val language: VoiceLanguage = VoiceLanguage.ENGLISH_US,
    /** Recognition will run on the phone (no audio sent anywhere). */
    val onDevice: Boolean = false,
    /** Why it stopped, when it stopped on its own. */
    val error: SpeechError? = null,
    /** Pieces written into the note in this session. */
    val written: Int = 0,
) {
    enum class Status {
        READY,
        LISTENING,
        PAUSED,

        /** No recogniser on this phone. */
        UNAVAILABLE,
    }
}

/**
 * Voice typing straight into the note (owner request): once started it keeps listening — through pauses,
 * sentence after sentence, for a whole lecture — and writes each finished sentence at the cursor through [write].
 * The words being recognised show live in the voice bar. Pause/Resume; closing stops it. A busy recogniser (it is
 * restarted between sentences, and some phones need a moment) is retried a few times before giving up.
 */
class VoiceTyping(
    private val engine: SpeechEngine,
    language: VoiceLanguage,
    private val write: (String) -> Unit = {},
    private val later: (Long, () -> Unit) -> Unit = { ms, block -> Handler(Looper.getMainLooper()).postDelayed(block, ms) },
) {

    private val _state = MutableStateFlow(
        if (engine.isAvailable()) {
            VoiceTypingState(language = language, onDevice = engine.canRunOnDevice())
        } else {
            VoiceTypingState(VoiceTypingState.Status.UNAVAILABLE, language = language)
        },
    )
    val state: StateFlow<VoiceTypingState> = _state.asStateFlow()

    private var silentRestarts = 0
    private var busyRetries = 0

    /** Bumped on every start/pause/stop, so a restart planned before it is dropped. */
    private var generation = 0

    private val listener = object : SpeechEngine.Listener {
        override fun onPartial(text: String) {
            val s = _state.value
            if (s.status == VoiceTypingState.Status.LISTENING) _state.value = s.copy(partial = s.language.shown(text))
        }

        override fun onSegment(text: String) {
            if (_state.value.status != VoiceTypingState.Status.LISTENING) return
            silentRestarts = 0
            busyRetries = 0
            emit(text)
        }

        override fun onFinal(text: String) {
            if (_state.value.status != VoiceTypingState.Status.LISTENING) return
            silentRestarts = 0
            busyRetries = 0
            emit(text)
            restart(RESTART_MS)
        }

        override fun onSessionEnd() {
            if (_state.value.status == VoiceTypingState.Status.LISTENING) restart(RESTART_MS)
        }

        override fun onError(error: SpeechError) {
            val s = _state.value
            if (s.status != VoiceTypingState.Status.LISTENING) return // paused/stopped meanwhile: expected
            if (error.recoverable && ++silentRestarts <= MAX_SILENT_RESTARTS) {
                if (s.partial.isNotBlank()) emit(s.partial, converted = true)
                restart(RESTART_MS)
                return
            }
            if (error == SpeechError.BUSY && ++busyRetries <= MAX_BUSY_RETRIES) {
                // Free the recogniser completely and try again a little later.
                engine.cancel()
                engine.release()
                restart(BUSY_RETRY_MS * busyRetries)
                return
            }
            // Keep what was half-recognised; stop listening.
            if (s.partial.isNotBlank()) emit(s.partial, converted = true)
            _state.value = _state.value.copy(
                status = VoiceTypingState.Status.PAUSED,
                partial = "",
                error = error.takeUnless { it.recoverable },
            )
        }
    }

    private fun emit(text: String, converted: Boolean = false) {
        val s = _state.value
        val piece = (if (converted) text else s.language.shown(text)).trim()
        _state.value = s.copy(partial = "", written = if (piece.isEmpty()) s.written else s.written + 1)
        if (piece.isNotEmpty()) write(piece)
    }

    fun setLanguage(language: VoiceLanguage) {
        val s = _state.value
        if (s.language == language) return
        _state.value = s.copy(language = language)
        if (s.status == VoiceTypingState.Status.LISTENING) {
            engine.cancel()
            restart(0)
        }
    }

    /** Use the phone's (possibly online) speech service instead of on-device recognition, e.g. when the
     *  on-device pack for the language isn't installed. */
    fun useOnlineService() {
        engine.cancel()
        engine.release()
        _state.value = _state.value.copy(onDevice = false, error = null)
    }

    fun start() {
        val s = _state.value
        if (s.status == VoiceTypingState.Status.UNAVAILABLE || s.status == VoiceTypingState.Status.LISTENING) return
        silentRestarts = 0
        busyRetries = 0
        _state.value = s.copy(status = VoiceTypingState.Status.LISTENING, error = null)
        generation++
        listen()
    }

    /** Stops listening; the half-recognised words go into the note. */
    fun pause() {
        val s = _state.value
        if (s.status != VoiceTypingState.Status.LISTENING) return
        generation++
        if (s.partial.isNotBlank()) emit(s.partial, converted = true)
        _state.value = _state.value.copy(status = VoiceTypingState.Status.PAUSED, partial = "")
        engine.cancel()
    }

    fun release() {
        pause()
        generation++
        engine.cancel()
        engine.release()
    }

    private fun restart(delayMs: Long) {
        val g = generation
        later(delayMs) {
            if (g == generation && _state.value.status == VoiceTypingState.Status.LISTENING) listen()
        }
    }

    private fun listen() {
        val s = _state.value
        engine.start(s.language.tag, s.onDevice, listener)
    }

    companion object {
        /** Silent stretches in a row (a few seconds each; about ten minutes) before voice typing pauses itself. */
        const val MAX_SILENT_RESTARTS = 120
        const val MAX_BUSY_RETRIES = 5
        const val RESTART_MS = 150L
        const val BUSY_RETRY_MS = 400L
    }
}
