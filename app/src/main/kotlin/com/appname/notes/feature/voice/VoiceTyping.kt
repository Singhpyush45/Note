package com.appname.notes.feature.voice

import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** English variants offered for voice typing (§11). */
enum class VoiceLanguage(val tag: String) {
    ENGLISH_INDIA("en-IN"),
    ENGLISH_US("en-US"),
    ENGLISH_UK("en-GB"),
    ;

    companion object {
        fun fromTag(tag: String?): VoiceLanguage? = entries.firstOrNull { it.tag == tag }

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
    /** Recognised so far (editable in [Status.REVIEW]). */
    val text: String = "",
    /** The words being recognised right now, not final yet. */
    val partial: String = "",
    val language: VoiceLanguage = VoiceLanguage.ENGLISH_US,
    /** Recognition will run on the phone (no audio sent anywhere). */
    val onDevice: Boolean = false,
    /** Why it stopped, when it stopped on its own. */
    val error: SpeechError? = null,
) {
    enum class Status {
        /** Not started: the user sees the language and where speech is processed, then taps Start. */
        READY,
        LISTENING,
        PAUSED,

        /** Stopped: the transcript can be edited, then inserted or discarded. */
        REVIEW,

        /** No recogniser on this phone. */
        UNAVAILABLE,
    }

    val canInsert: Boolean get() = (text + partial).isNotBlank()
}

/**
 * Dictation on top of a one-utterance-at-a-time [SpeechEngine] (§11): keeps listening across pauses in speech
 * (restarting after each sentence and after silence), joins sentences with the right spacing, and lets the user
 * pause, resume, stop, edit and insert. Too much silence pauses by itself, so the microphone isn't left on.
 */
class VoiceTyping(private val engine: SpeechEngine, language: VoiceLanguage) {

    private val _state = MutableStateFlow(
        if (engine.isAvailable()) {
            VoiceTypingState(language = language, onDevice = engine.canRunOnDevice())
        } else {
            VoiceTypingState(VoiceTypingState.Status.UNAVAILABLE, language = language)
        },
    )
    val state: StateFlow<VoiceTypingState> = _state.asStateFlow()

    private var silentRestarts = 0

    private val listener = object : SpeechEngine.Listener {
        override fun onPartial(text: String) {
            if (_state.value.status == VoiceTypingState.Status.LISTENING) _state.value = _state.value.copy(partial = text)
        }

        override fun onFinal(text: String) {
            silentRestarts = 0
            val s = _state.value
            _state.value = s.copy(text = join(s.text, text), partial = "")
            if (_state.value.status == VoiceTypingState.Status.LISTENING) listen()
        }

        override fun onError(error: SpeechError) {
            val s = _state.value
            if (s.status != VoiceTypingState.Status.LISTENING) return // paused/stopped meanwhile: expected
            if (error.recoverable && ++silentRestarts <= MAX_SILENT_RESTARTS) {
                _state.value = s.copy(partial = "")
                listen()
                return
            }
            // Keep what was half-recognised; stop listening.
            _state.value = s.copy(
                status = VoiceTypingState.Status.PAUSED,
                text = join(s.text, s.partial),
                partial = "",
                error = error.takeUnless { it.recoverable },
            )
        }
    }

    fun setLanguage(language: VoiceLanguage) {
        val s = _state.value
        if (s.language == language) return
        _state.value = s.copy(language = language)
        if (s.status == VoiceTypingState.Status.LISTENING) {
            engine.cancel()
            listen()
        }
    }

    /** Use the phone's (possibly online) speech service instead of on-device recognition, e.g. when the
     *  on-device English pack isn't installed. */
    fun useOnlineService() {
        _state.value = _state.value.copy(onDevice = false, error = null)
    }

    fun start() {
        val s = _state.value
        if (s.status == VoiceTypingState.Status.UNAVAILABLE || s.status == VoiceTypingState.Status.LISTENING) return
        silentRestarts = 0
        _state.value = s.copy(status = VoiceTypingState.Status.LISTENING, error = null)
        listen()
    }

    fun pause() {
        val s = _state.value
        if (s.status != VoiceTypingState.Status.LISTENING) return
        // Words heard so far stay (the half-recognised sentence is kept as it was shown).
        _state.value = s.copy(status = VoiceTypingState.Status.PAUSED, text = join(s.text, s.partial), partial = "")
        engine.cancel()
    }

    /** Stops listening and lets the user check and edit the text. */
    fun stop() {
        val s = _state.value
        if (s.status == VoiceTypingState.Status.LISTENING) engine.cancel()
        _state.value = s.copy(status = VoiceTypingState.Status.REVIEW, text = join(s.text, s.partial), partial = "")
    }

    fun edit(text: String) {
        _state.value = _state.value.copy(text = text)
    }

    /** The text to insert (also what's still being recognised); stops listening. */
    fun finish(): String {
        stop()
        return _state.value.text.trim()
    }

    fun release() {
        engine.cancel()
        engine.release()
    }

    private fun listen() {
        val s = _state.value
        engine.start(s.language.tag, s.onDevice, listener)
    }

    companion object {
        /** Silent stretches (no speech for several seconds each) before dictation pauses itself. */
        const val MAX_SILENT_RESTARTS = 3

        /**
         * Appends [next] to [text] with one space, capitalising it when it starts a new sentence (recognisers
         * without formatting return lower-case words).
         */
        fun join(text: String, next: String): String {
            val piece = next.trim()
            if (piece.isEmpty()) return text
            val base = text.trimEnd()
            if (base.isEmpty()) return piece.replaceFirstChar { it.titlecase(Locale.ROOT) }
            val sentenceEnded = base.last() in ".!?"
            val start = if (sentenceEnded) piece.replaceFirstChar { it.titlecase(Locale.ROOT) } else piece
            return "$base $start"
        }
    }
}
