package com.appname.notes.feature.voice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.appname.notes.R

object VoiceTestTags {
    const val SHEET = "voice_sheet"
    const val START = "voice_start"
    const val PAUSE = "voice_pause"
    const val STOP = "voice_stop"
    const val TEXT = "voice_text"
    const val LANGUAGE = "voice_language"
    const val SOURCE = "voice_source"
    const val DOWNLOAD = "voice_download"
    fun language(l: VoiceLanguage) = "voice_lang_${l.key}"
}

data class VoiceBarState(
    val language: VoiceLanguage,
    val source: VoiceSource,
    val packReady: Boolean,
    /** 0..1 while the language's pack downloads. */
    val download: Float?,
    val status: LiveTranscriber.Status,
    val problem: LiveTranscriber.Problem?,
    val level: Float,
    val phoneAudioSupported: Boolean,
)

data class VoiceTypingActions(
    val start: () -> Unit,
    val pause: () -> Unit,
    val language: (VoiceLanguage) -> Unit,
    val source: (VoiceSource) -> Unit,
    val download: () -> Unit,
    val redownload: () -> Unit,
    val done: () -> Unit,
)

/**
 * Live transcription (owner request: built-in, word by word, straight into the note). A slim bar at the bottom of
 * the editor: the words appear in the note as they are spoken (and settle when the sentence ends); the bar shows that
 * it is listening (with a level meter), the language, the sound source (microphone, or sound playing on this phone)
 * and Pause/Resume and Done. The first time a language is used its offline voice pack is downloaded.
 */
@Composable
fun VoiceTypingBar(state: VoiceBarState, actions: VoiceTypingActions, modifier: Modifier = Modifier) {
    BackHandler(onBack = actions.done)
    val listening = state.status == LiveTranscriber.Status.LISTENING
    // Keep the screen on while listening, so a long lecture isn't cut off by the screen timeout.
    val view = LocalView.current
    DisposableEffect(listening) {
        view.keepScreenOn = listening
        onDispose { view.keepScreenOn = false }
    }
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        modifier = modifier.fillMaxWidth().testTag(VoiceTestTags.SHEET),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                state.download != null -> {
                    Text(stringResource(R.string.voice_downloading, (state.download * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { state.download }, modifier = Modifier.fillMaxWidth())
                }
                !state.packReady -> {
                    Text(
                        stringResource(R.string.voice_pack_needed, stringResource(languageName(state.language)), state.language.pack.sizeMb),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelMeter(if (listening) state.level else 0f, listening)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(
                            when (state.status) {
                                LiveTranscriber.Status.LISTENING -> R.string.voice_live_hint
                                LiveTranscriber.Status.STARTING -> R.string.voice_starting
                                LiveTranscriber.Status.IDLE -> R.string.voice_paused
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).testTag(VoiceTestTags.TEXT).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            state.problem?.let { p ->
                Text(
                    stringResource(
                        when (p) {
                            LiveTranscriber.Problem.AUDIO -> R.string.voice_problem_audio
                            LiveTranscriber.Problem.MODEL -> R.string.voice_problem_model
                            LiveTranscriber.Problem.SILENT_PHONE_AUDIO -> R.string.voice_problem_silent
                            LiveTranscriber.Problem.STOPPED -> R.string.voice_problem_stopped
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (p == LiveTranscriber.Problem.MODEL) TextButton(onClick = actions.redownload) { Text(stringResource(R.string.voice_download)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LanguageButton(state.language, actions.language)
                if (state.phoneAudioSupported) SourceButton(state.source, actions.source)
                Spacer(Modifier.weight(1f))
                when {
                    !state.packReady && state.download == null ->
                        Button(onClick = actions.download, modifier = Modifier.testTag(VoiceTestTags.DOWNLOAD)) { Text(stringResource(R.string.voice_download), maxLines = 1) }
                    listening || state.status == LiveTranscriber.Status.STARTING ->
                        OutlinedButton(onClick = actions.pause, modifier = Modifier.testTag(VoiceTestTags.PAUSE)) {
                            Icon(Icons.Outlined.Pause, stringResource(R.string.voice_pause), Modifier.size(18.dp))
                        }
                    state.packReady ->
                        OutlinedButton(onClick = actions.start, modifier = Modifier.testTag(VoiceTestTags.START)) {
                            Icon(Icons.Outlined.Mic, stringResource(R.string.voice_resume), Modifier.size(18.dp))
                        }
                }
                Button(onClick = actions.done, modifier = Modifier.testTag(VoiceTestTags.STOP)) { Text(stringResource(R.string.voice_done), maxLines = 1) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.voice_offline),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** Five bars that rise with the sound heard. */
@Composable
private fun LevelMeter(level: Float, active: Boolean) {
    val shown by animateFloatAsState(level, label = "level")
    Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        listOf(0.5f, 0.8f, 1f, 0.8f, 0.5f).forEach { k ->
            val h = (0.2f + (shown * 4f).coerceAtMost(1f) * k * 0.8f).coerceIn(0.2f, 1f)
            Box(Modifier.width(3.dp).fillMaxHeight(h).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun LanguageButton(current: VoiceLanguage, onPick: (VoiceLanguage) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag(VoiceTestTags.LANGUAGE)) {
            Text(stringResource(languageName(current)), maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            VoiceLanguage.entries.forEach { l ->
                DropdownMenuItem(
                    text = { Text(stringResource(languageName(l))) },
                    onClick = { open = false; onPick(l) },
                    modifier = Modifier.testTag(VoiceTestTags.language(l)),
                )
            }
        }
    }
}

@Composable
private fun SourceButton(current: VoiceSource, onPick: (VoiceSource) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag(VoiceTestTags.SOURCE)) {
            Text(stringResource(sourceName(current)), maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            VoiceSource.entries.forEach { s ->
                DropdownMenuItem(text = { Text(stringResource(sourceName(s))) }, onClick = { open = false; onPick(s) })
            }
        }
    }
}

private fun sourceName(s: VoiceSource) = when (s) {
    VoiceSource.MICROPHONE -> R.string.voice_source_mic
    VoiceSource.PHONE_AUDIO -> R.string.voice_source_phone
}

private fun languageName(l: VoiceLanguage) = when (l) {
    VoiceLanguage.ENGLISH_INDIA -> R.string.voice_lang_in
    VoiceLanguage.HINDI -> R.string.voice_lang_hi
    VoiceLanguage.HINGLISH -> R.string.voice_lang_hinglish
    VoiceLanguage.ENGLISH_US -> R.string.voice_lang_us
    VoiceLanguage.ENGLISH_UK -> R.string.voice_lang_uk
}

