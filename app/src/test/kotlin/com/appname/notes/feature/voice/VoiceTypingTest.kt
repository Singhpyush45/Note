package com.appname.notes.feature.voice

import com.appname.notes.feature.voice.VoiceTypingState.Status
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

/** Voice typing straight into the note (owner request), with a fake recogniser and an immediate scheduler. */
class VoiceTypingTest {

    private class FakeEngine(var available: Boolean = true, var onDevice: Boolean = false) : SpeechEngine {
        var listener: SpeechEngine.Listener? = null
        val starts = mutableListOf<Pair<String, Boolean>>()
        var cancels = 0
        var releases = 0

        override fun isAvailable() = available
        override fun canRunOnDevice() = onDevice
        override fun start(languageTag: String, onDevice: Boolean, listener: SpeechEngine.Listener) {
            starts += languageTag to onDevice
            this.listener = listener
        }
        override fun stop() = Unit
        override fun cancel() { cancels++ }
        override fun release() { releases++ }

        fun partial(t: String) = listener!!.onPartial(t)
        fun final(t: String) = listener!!.onFinal(t)
        fun segment(t: String) = listener!!.onSegment(t)
        fun error(e: SpeechError) = listener!!.onError(e)
    }

    private val engine = FakeEngine()
    private val written = mutableListOf<String>()
    private val voice = VoiceTyping(engine, VoiceLanguage.ENGLISH_INDIA, write = { written += it }, later = { _, block -> block() })
    private val state get() = voice.state.value

    @Test
    fun `each sentence goes straight into the note and listening goes on`() {
        voice.start()
        assertThat(engine.starts.single()).isEqualTo("en-IN" to false)
        engine.partial("hello")
        assertThat(state.partial).isEqualTo("hello")
        assertThat(written).isEmpty()
        engine.final("hello world.")
        assertThat(written).containsExactly("hello world.")
        assertThat(engine.starts).hasSize(2) // listening again by itself
        engine.segment("next part") // a long session hands back pieces without restarting
        assertThat(written).containsExactly("hello world.", "next part").inOrder()
        assertThat(engine.starts).hasSize(2)
        assertThat(state.status).isEqualTo(Status.LISTENING)
    }

    @Test
    fun `pause writes the half-heard words, late results are ignored`() {
        voice.start()
        engine.partial("half a sentence")
        voice.pause()
        assertThat(written).containsExactly("half a sentence")
        engine.final("too late")
        assertThat(written).containsExactly("half a sentence")
        assertThat(state.status).isEqualTo(Status.PAUSED)
    }

    @Test
    fun `silence keeps listening for a lecture, busy is retried, then it pauses`() {
        voice.start()
        repeat(50) { engine.error(SpeechError.SILENCE) }
        assertThat(state.status).isEqualTo(Status.LISTENING)
        repeat(VoiceTyping.MAX_BUSY_RETRIES) { engine.error(SpeechError.BUSY) }
        assertThat(state.status).isEqualTo(Status.LISTENING)
        assertThat(engine.releases).isEqualTo(VoiceTyping.MAX_BUSY_RETRIES) // a fresh recogniser each time
        engine.error(SpeechError.BUSY)
        assertThat(state.status).isEqualTo(Status.PAUSED)
        assertThat(state.error).isEqualTo(SpeechError.BUSY)
    }

    @Test
    fun `Hinglish recognises Hindi and writes it in English letters`() {
        val v = VoiceTyping(engine, VoiceLanguage.HINGLISH, write = { written += it }, later = { _, block -> block() })
        v.start()
        assertThat(engine.starts.last().first).isEqualTo("hi-IN")
        engine.partial("मैं घर")
        assertThat(v.state.value.partial).isEqualTo("main ghar")
        engine.final("मैं घर जा रहा हूँ")
        assertThat(written).containsExactly("main ghar ja raha hun")
    }

    @Test
    fun `no recogniser on the phone`() {
        val v = VoiceTyping(FakeEngine(available = false), VoiceLanguage.HINDI)
        assertThat(v.state.value.status).isEqualTo(Status.UNAVAILABLE)
    }

    @Test
    fun `romanizing common Hindi words`() {
        assertThat(Hinglish.romanize("नमस्ते भारत")).isEqualTo("namaste bharat")
        assertThat(Hinglish.romanize("वह रहता है, समझना कमरा नहीं।")).isEqualTo("vah rahta hai, samajhna kamra nahin.")
        assertThat(Hinglish.romanize("क्या notes 123")).isEqualTo("kya notes 123")
    }

    @Test
    fun `default language follows the phone's country`() {
        assertThat(VoiceLanguage.defaultFor(Locale("en", "IN"))).isEqualTo(VoiceLanguage.ENGLISH_INDIA)
        assertThat(VoiceLanguage.defaultFor(Locale.US)).isEqualTo(VoiceLanguage.ENGLISH_US)
        assertThat(VoiceLanguage.fromTag("hi-Latn-IN")).isEqualTo(VoiceLanguage.HINGLISH)
    }
}
