package com.appname.notes.feature.voice

import com.appname.notes.feature.voice.VoiceTypingState.Status
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

/** Dictation logic on top of a one-sentence-at-a-time recogniser (owner brief §11), with a fake recogniser. */
class VoiceTypingTest {

    private class FakeEngine(var available: Boolean = true, var onDevice: Boolean = false) : SpeechEngine {
        var listener: SpeechEngine.Listener? = null
        val starts = mutableListOf<Pair<String, Boolean>>()
        var cancels = 0
        var released = false

        override fun isAvailable() = available
        override fun canRunOnDevice() = onDevice
        override fun start(languageTag: String, onDevice: Boolean, listener: SpeechEngine.Listener) {
            starts += languageTag to onDevice
            this.listener = listener
        }
        override fun stop() = Unit
        override fun cancel() { cancels++ }
        override fun release() { released = true }

        fun partial(t: String) = listener!!.onPartial(t)
        fun final(t: String) = listener!!.onFinal(t)
        fun error(e: SpeechError) = listener!!.onError(e)
    }

    private val engine = FakeEngine()
    private val voice = VoiceTyping(engine, VoiceLanguage.ENGLISH_INDIA)
    private val state get() = voice.state.value

    @Test
    fun `dictation keeps listening across sentences and joins them`() {
        assertThat(state.status).isEqualTo(Status.READY)
        voice.start()
        assertThat(engine.starts.single()).isEqualTo("en-IN" to false)
        engine.partial("hello")
        assertThat(state.partial).isEqualTo("hello")
        engine.final("hello world.")
        // The next sentence starts listening again by itself.
        assertThat(engine.starts).hasSize(2)
        engine.final("this is a note")
        assertThat(state.text).isEqualTo("Hello world. This is a note")
        assertThat(state.partial).isEmpty()
        assertThat(state.status).isEqualTo(Status.LISTENING)
    }

    @Test
    fun `pause keeps the half-recognised words, resume continues, stop allows editing`() {
        voice.start()
        engine.final("first part")
        engine.partial("second")
        voice.pause()
        assertThat(state.status).isEqualTo(Status.PAUSED)
        assertThat(state.text).isEqualTo("First part second")
        // A late result after pausing is ignored (the recogniser was cancelled).
        engine.partial("ignored")
        assertThat(state.partial).isEmpty()

        voice.start()
        engine.final("third")
        voice.stop()
        assertThat(state.status).isEqualTo(Status.REVIEW)
        voice.edit("First part, second, third.")
        assertThat(voice.finish()).isEqualTo("First part, second, third.")
    }

    @Test
    fun `silence restarts a few times, then pauses so the microphone isn't left on`() {
        voice.start()
        repeat(VoiceTyping.MAX_SILENT_RESTARTS) { engine.error(SpeechError.SILENCE) }
        assertThat(state.status).isEqualTo(Status.LISTENING)
        assertThat(engine.starts).hasSize(1 + VoiceTyping.MAX_SILENT_RESTARTS)
        engine.error(SpeechError.NO_MATCH)
        assertThat(state.status).isEqualTo(Status.PAUSED)
        assertThat(state.error).isNull() // just silence: no error message
    }

    @Test
    fun `speech resets the silence count`() {
        voice.start()
        repeat(VoiceTyping.MAX_SILENT_RESTARTS) { engine.error(SpeechError.SILENCE) }
        engine.final("still here")
        repeat(VoiceTyping.MAX_SILENT_RESTARTS) { engine.error(SpeechError.SILENCE) }
        assertThat(state.status).isEqualTo(Status.LISTENING)
    }

    @Test
    fun `a network error pauses with the reason and keeps the text`() {
        voice.start()
        engine.final("keep this")
        engine.partial("and this")
        engine.error(SpeechError.NETWORK)
        assertThat(state.status).isEqualTo(Status.PAUSED)
        assertThat(state.error).isEqualTo(SpeechError.NETWORK)
        assertThat(state.text).isEqualTo("Keep this and this")
        voice.start()
        assertThat(state.error).isNull()
    }

    @Test
    fun `on-device recognition is used when available, with a switch to the online service`() {
        val e = FakeEngine(onDevice = true)
        val v = VoiceTyping(e, VoiceLanguage.ENGLISH_US)
        assertThat(v.state.value.onDevice).isTrue()
        v.start()
        assertThat(e.starts.single()).isEqualTo("en-US" to true)
        e.error(SpeechError.LANGUAGE_UNAVAILABLE)
        assertThat(v.state.value.error).isEqualTo(SpeechError.LANGUAGE_UNAVAILABLE)
        v.useOnlineService()
        v.start()
        assertThat(e.starts.last()).isEqualTo("en-US" to false)
        assertThat(v.state.value.onDevice).isFalse()
    }

    @Test
    fun `changing language while listening restarts in the new language`() {
        voice.start()
        voice.setLanguage(VoiceLanguage.ENGLISH_UK)
        assertThat(engine.cancels).isEqualTo(1)
        assertThat(engine.starts.last().first).isEqualTo("en-GB")
    }

    @Test
    fun `no recogniser on the phone`() {
        val v = VoiceTyping(FakeEngine(available = false), VoiceLanguage.ENGLISH_US)
        assertThat(v.state.value.status).isEqualTo(Status.UNAVAILABLE)
        v.start()
        assertThat(v.state.value.status).isEqualTo(Status.UNAVAILABLE)
    }

    @Test
    fun `release frees the recogniser`() {
        voice.start()
        voice.release()
        assertThat(engine.released).isTrue()
    }

    @Test
    fun `joining text`() {
        assertThat(VoiceTyping.join("", "  hello  ")).isEqualTo("Hello")
        assertThat(VoiceTyping.join("Hello", "there")).isEqualTo("Hello there")
        assertThat(VoiceTyping.join("Done!", "next one")).isEqualTo("Done! Next one")
        assertThat(VoiceTyping.join("Keep  ", "")).isEqualTo("Keep  ")
    }

    @Test
    fun `default language follows the phone's country`() {
        assertThat(VoiceLanguage.defaultFor(Locale("en", "IN"))).isEqualTo(VoiceLanguage.ENGLISH_INDIA)
        assertThat(VoiceLanguage.defaultFor(Locale("hi", "IN"))).isEqualTo(VoiceLanguage.ENGLISH_INDIA)
        assertThat(VoiceLanguage.defaultFor(Locale.UK)).isEqualTo(VoiceLanguage.ENGLISH_UK)
        assertThat(VoiceLanguage.defaultFor(Locale.GERMANY)).isEqualTo(VoiceLanguage.ENGLISH_US)
        assertThat(VoiceLanguage.fromTag("en-IN")).isEqualTo(VoiceLanguage.ENGLISH_INDIA)
        assertThat(VoiceLanguage.fromTag("fr-FR")).isNull()
    }
}
