package com.appname.notes.feature.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Makes the speech-to-text engine; the one place to switch to another provider. */
class SpeechEngineFactory @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun create(): SpeechEngine = AndroidSpeechEngine(context)
}
