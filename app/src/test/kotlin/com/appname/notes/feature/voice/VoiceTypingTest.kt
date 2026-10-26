package com.appname.notes.feature.voice

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

/** Text cleanup for live transcription (built-in offline recogniser) and the Hinglish romanizer. */
class VoiceTypingTest {

    @Test
    fun `a finished sentence gets a full stop and English I`() {
        assertThat(VoiceFormat.final("i think i'm ready", VoiceLanguage.ENGLISH_INDIA)).isEqualTo("I think I'm ready.")
        assertThat(VoiceFormat.final("is it ready?", VoiceLanguage.ENGLISH_US)).isEqualTo("is it ready?")
        assertThat(VoiceFormat.final("  ", VoiceLanguage.ENGLISH_US)).isEmpty()
    }

    @Test
    fun `unknown words are left out`() {
        assertThat(VoiceFormat.partial("the [unk] lecture", VoiceLanguage.ENGLISH_INDIA)).isEqualTo("the lecture")
    }

    @Test
    fun `Hindi ends with a danda, Hinglish is written in English letters`() {
        assertThat(VoiceFormat.final("मैं घर जा रहा हूँ", VoiceLanguage.HINDI)).isEqualTo("मैं घर जा रहा हूँ।")
        assertThat(VoiceFormat.partial("मैं घर", VoiceLanguage.HINGLISH)).isEqualTo("main ghar")
        assertThat(VoiceFormat.final("मैं घर जा रहा हूँ", VoiceLanguage.HINGLISH)).isEqualTo("main ghar ja raha hun.")
    }

    @Test
    fun `romanizing common Hindi words`() {
        assertThat(Hinglish.romanize("नमस्ते भारत")).isEqualTo("namaste bharat")
        assertThat(Hinglish.romanize("वह रहता है, समझना कमरा नहीं।")).isEqualTo("vah rahta hai, samajhna kamra nahin.")
        assertThat(Hinglish.romanize("क्या notes 123")).isEqualTo("kya notes 123")
    }

    @Test
    fun `languages map to their packs and the phone's language picks the default`() {
        assertThat(VoiceLanguage.HINGLISH.pack).isEqualTo(VoicePack.HINDI)
        assertThat(VoiceLanguage.ENGLISH_UK.pack).isEqualTo(VoicePack.ENGLISH_US)
        assertThat(VoiceLanguage.fromTag("hi-Latn-IN")).isEqualTo(VoiceLanguage.HINGLISH)
        assertThat(VoiceLanguage.defaultFor(Locale.forLanguageTag("hi-IN"))).isEqualTo(VoiceLanguage.HINDI)
        assertThat(VoicePack.HINDI.url).startsWith("https://")
    }
}
