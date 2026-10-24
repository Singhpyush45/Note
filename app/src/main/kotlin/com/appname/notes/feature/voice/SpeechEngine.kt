package com.appname.notes.feature.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Why recognition stopped. Recoverable ones (silence) restart quietly during dictation. */
enum class SpeechError {
    /** Nothing recognisable was said. */
    NO_MATCH,

    /** No speech heard for a while. */
    SILENCE,
    NETWORK,

    /** The microphone permission is missing. */
    PERMISSION,

    /** The recogniser is used by another app. */
    BUSY,

    /** The chosen English variant isn't available (e.g. its on-device pack isn't installed). */
    LANGUAGE_UNAVAILABLE,

    /** No speech recognition service on this phone. */
    UNAVAILABLE,
    OTHER,
    ;

    val recoverable: Boolean get() = this == NO_MATCH || this == SILENCE
}

/**
 * Speech-to-text behind an interface (§11 of the owner's brief, NFR-11), so Android's recogniser can be replaced
 * by another provider and tests can use a fake. One utterance per [start]; dictation restarts it.
 */
interface SpeechEngine {
    /** A recogniser exists on this phone. */
    fun isAvailable(): Boolean

    /** Recognition can run on the phone itself (nothing sent over the internet). */
    fun canRunOnDevice(): Boolean

    fun start(languageTag: String, onDevice: Boolean, listener: Listener)

    /** Stops listening; the result of what was said so far still arrives. */
    fun stop()

    /** Stops and discards. */
    fun cancel()

    fun release()

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(error: SpeechError)
    }
}

/**
 * Android's [SpeechRecognizer]. On Android 12+ with an on-device model installed, recognition stays on the
 * phone; otherwise the phone's speech service is used, which may send audio to its provider (the UI says so
 * before starting). Main thread only.
 */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {

    private var recognizer: SpeechRecognizer? = null
    private var recognizerOnDevice = false

    override fun isAvailable(): Boolean = serviceAvailable() || canRunOnDevice()

    override fun canRunOnDevice(): Boolean = try {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    } catch (e: RuntimeException) { // some phones have no configuration for it at all
        false
    } catch (e: LinkageError) { // an API this Android build doesn't have
        false
    }

    private fun serviceAvailable(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(context)
    } catch (e: RuntimeException) {
        false
    } catch (e: LinkageError) {
        false
    }

    override fun start(languageTag: String, onDevice: Boolean, listener: SpeechEngine.Listener) {
        val r = recognizerFor(onDevice) ?: return listener.onError(SpeechError.UNAVAILABLE)
        r.setRecognitionListener(Adapter(listener))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (onDevice) intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Punctuation and capitals where the recogniser supports it.
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
        }
        try {
            r.startListening(intent)
        } catch (e: SecurityException) {
            listener.onError(SpeechError.PERMISSION)
        }
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.cancel()
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun recognizerFor(onDevice: Boolean): SpeechRecognizer? {
        recognizer?.let { if (recognizerOnDevice == onDevice) return it }
        release()
        val created = try {
            if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else if (serviceAvailable()) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else {
                null
            }
        } catch (e: RuntimeException) {
            null
        }
        recognizer = created
        recognizerOnDevice = onDevice
        return created
    }

    private class Adapter(private val listener: SpeechEngine.Listener) : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            first(partialResults)?.let(listener::onPartial)
        }

        override fun onResults(results: Bundle?) {
            val text = first(results)
            if (text.isNullOrBlank()) listener.onError(SpeechError.NO_MATCH) else listener.onFinal(text)
        }

        override fun onError(error: Int) = listener.onError(map(error))

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun first(bundle: Bundle?): String? =
            bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

        private fun map(error: Int): SpeechError = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> SpeechError.NO_MATCH
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechError.SILENCE
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> SpeechError.NETWORK
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.PERMISSION
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechError.BUSY
            // ERROR_LANGUAGE_NOT_SUPPORTED (12) and ERROR_LANGUAGE_UNAVAILABLE (13), Android 12+.
            12, 13 -> SpeechError.LANGUAGE_UNAVAILABLE
            // ERROR_SERVER_DISCONNECTED (11): the service went away.
            11 -> SpeechError.UNAVAILABLE
            else -> SpeechError.OTHER
        }
    }
}
