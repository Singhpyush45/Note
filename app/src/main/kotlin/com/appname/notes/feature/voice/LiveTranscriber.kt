package com.appname.notes.feature.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Where the sound comes from. */
enum class VoiceSource {
    /** The microphone: a lecture in the room, the user speaking. */
    MICROPHONE,

    /** Sound playing on this phone (a video in another app), Android 10+, with the user's permission each time. */
    PHONE_AUDIO,
}

/**
 * The built-in live transcriber (owner request: "inbuilt", word by word, not the phone's speech service). Audio is
 * read continuously in [TranscribeService] (a foreground service, so it keeps going while another app is open) and
 * turned into text on the phone by Vosk with an offline [VoicePack]. Words being heard arrive as [Event.Partial]
 * several times a second; at each pause the sentence arrives as [Event.Final]. Nothing is sent anywhere.
 */
object LiveTranscriber {
    sealed interface Event {
        /** [at]: when it was heard (System.nanoTime), to tell a pause between sentences from one inside a sentence. */
        data class Partial(val text: String, val at: Long = System.nanoTime()) : Event

        /** [last]: the end of listening (Stop), so the sentence is closed with a full stop. */
        data class Final(val text: String, val last: Boolean = false, val at: Long = System.nanoTime()) : Event
    }

    enum class Status { IDLE, STARTING, LISTENING }

    enum class Problem {
        /** The microphone or phone audio couldn't be opened. */
        AUDIO,

        /** The voice pack couldn't be loaded (damaged download): download it again. */
        MODEL,

        /** Phone audio stays silent: the app playing doesn't allow other apps to hear it. */
        SILENT_PHONE_AUDIO,

        /** Android ended the phone-audio permission (e.g. the user stopped it from the notification). */
        STOPPED,
    }

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _problem = MutableStateFlow<Problem?>(null)
    val problem: StateFlow<Problem?> = _problem.asStateFlow()

    /** Loudness 0..1 of what is heard, for the bar's level meter. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /** Starts listening with [pack] from [source]; [projection] is the phone-audio permission result (code, data). */
    fun start(context: Context, pack: VoicePack, source: VoiceSource, projection: Pair<Int, Intent>? = null) {
        _problem.value = null
        _status.value = Status.STARTING
        val intent = Intent(context, TranscribeService::class.java)
            .setAction(TranscribeService.ACTION_START)
            .putExtra(TranscribeService.EXTRA_PACK, pack.name)
            .putExtra(TranscribeService.EXTRA_SOURCE, source.name)
        if (projection != null) {
            intent.putExtra(TranscribeService.EXTRA_CODE, projection.first)
            intent.putExtra(TranscribeService.EXTRA_DATA, projection.second)
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: RuntimeException) {
            fail(Problem.AUDIO)
        }
    }

    fun stop(context: Context) {
        if (_status.value == Status.IDLE) return
        runCatching { context.startService(Intent(context, TranscribeService::class.java).setAction(TranscribeService.ACTION_STOP)) }
    }

    /** Phone audio needs Android 10 or newer. */
    val phoneAudioSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    // ------------------------------------------------------------ called by the service

    internal fun listening() {
        _status.value = Status.LISTENING
    }

    internal fun stopped() {
        _status.value = Status.IDLE
        _level.value = 0f
    }

    internal fun fail(p: Problem) {
        _problem.value = p
        stopped()
    }

    internal fun warn(p: Problem?) {
        _problem.value = p
    }

    internal fun level(v: Float) {
        _level.value = v
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** The text in a Vosk result: {"partial": "..."} or {"text": "..."}. */
    internal fun field(result: String, name: String): String =
        runCatching { json.parseToJsonElement(result).jsonObject[name]?.jsonPrimitive?.content }.getOrNull().orEmpty()

    /**
     * The text of a final Vosk result, leaving out short bits the recogniser wasn't sure of: in silence or noise
     * small models "hear" a word or two ("no", "in", "the") that nobody said.
     */
    internal fun confidentText(result: String): String = runCatching {
        val o = json.parseToJsonElement(result).jsonObject
        val text = o["text"]?.jsonPrimitive?.content.orEmpty()
        val words = (o["result"] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.get("conf")?.jsonPrimitive?.content?.toFloatOrNull() }
        if (words.isNotEmpty() && words.size <= SHORT_WORDS && words.average() < SHORT_MIN_CONFIDENCE) "" else text
    }.getOrDefault("")

    private const val SHORT_WORDS = 2
    private const val SHORT_MIN_CONFIDENCE = 0.85

    internal fun emit(e: Event) {
        _events.tryEmit(e)
    }
}

/**
 * Turns what the recogniser wrote into note text: Hinglish gets English letters; English gets "I" capitalised. The
 * offline models don't punctuate, so a sentence is closed with a full stop (Hindi: "।") when the speaker pauses
 * long enough ([PAUSE_NANOS]) or listening stops; a short breath inside a sentence just continues it.
 */
object VoiceFormat {
    /** Silence between two pieces of speech that counts as the end of a sentence. */
    const val PAUSE_NANOS = 700_000_000L

    fun partial(text: String, language: VoiceLanguage): String = clean(text, language)

    /** A finished piece; [end] (listening stopped) closes it with a full stop. */
    fun final(text: String, language: VoiceLanguage, end: Boolean = false): String {
        val t = clean(text, language)
        if (t.isEmpty() || !end || t.last() in ".!?।") return t
        return t + stop(language)
    }

    fun stop(language: VoiceLanguage): String = if (language.hindiScript) "।" else "."

    private fun clean(text: String, language: VoiceLanguage): String {
        // Vosk marks words it isn't sure of as "[unk]"; leave them out.
        var t = text.replace("[unk]", "").replace(Regex("\\s+"), " ").trim()
        if (language.roman) t = Hinglish.romanize(t)
        if (language.pack != VoicePack.HINDI || language.roman) {
            t = t.replace(Regex("\\bi\\b"), "I").replace(Regex("\\bi'(m|ve|ll|d)\\b"), "I'$1")
        }
        return t
    }
}
