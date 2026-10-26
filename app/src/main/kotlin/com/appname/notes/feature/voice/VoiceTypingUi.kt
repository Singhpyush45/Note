package com.appname.notes.feature.voice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.feature.voice.VoiceTypingState.Status

object VoiceTestTags {
    const val SHEET = "voice_sheet"
    const val START = "voice_start"
    const val PAUSE = "voice_pause"
    const val STOP = "voice_stop"
    const val TEXT = "voice_text"
    const val LANGUAGE = "voice_language"
    const val UNAVAILABLE = "voice_unavailable"
    fun language(l: VoiceLanguage) = "voice_lang_${l.key}"
}

data class VoiceTypingActions(
    val start: () -> Unit,
    val pause: () -> Unit,
    val language: (VoiceLanguage) -> Unit,
    val useOnline: () -> Unit,
    val done: () -> Unit,
)

/**
 * Voice typing (owner request): a slim bar at the bottom of the editor. While it listens, every finished sentence
 * goes straight into the note at the cursor — no pop-up, no Insert — so a whole lecture can be written down; the
 * words being recognised right now show in the bar. Language: English (India/US/UK), Hindi, Hinglish (Hindi in
 * English letters). Pause/Resume; Done or Back closes it. The screen stays on while listening.
 */
@Composable
fun VoiceTypingBar(state: VoiceTypingState, actions: VoiceTypingActions, modifier: Modifier = Modifier) {
    BackHandler(onBack = actions.done)
    val listening = state.status == Status.LISTENING
    // A lecture can be long: don't let the screen switch off (that would stop the microphone).
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
            if (state.status == Status.UNAVAILABLE) {
                Text(stringResource(R.string.voice_unavailable), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(VoiceTestTags.UNAVAILABLE))
                TextButton(onClick = actions.done, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_close)) }
                return@Column
            }
            // What is being heard right now (it goes into the note when the sentence ends).
            Row(verticalAlignment = Alignment.CenterVertically) {
                ListeningDot(listening)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        state.partial.isNotBlank() -> state.partial
                        listening -> stringResource(R.string.voice_speak_now)
                        else -> stringResource(R.string.voice_paused)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.partial.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).heightIn(min = 24.dp).testTag(VoiceTestTags.TEXT).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            state.error?.let { e ->
                Text(
                    stringResource(
                        when (e) {
                            SpeechError.NETWORK -> R.string.voice_error_network
                            SpeechError.PERMISSION -> R.string.voice_error_permission
                            SpeechError.BUSY -> R.string.voice_error_busy
                            SpeechError.LANGUAGE_UNAVAILABLE -> R.string.voice_error_language
                            SpeechError.UNAVAILABLE -> R.string.voice_unavailable
                            else -> R.string.voice_error_other
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (e == SpeechError.LANGUAGE_UNAVAILABLE && state.onDevice) {
                    TextButton(onClick = actions.useOnline) { Text(stringResource(R.string.voice_use_online)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LanguageButton(state.language, actions.language)
                Icon(
                    if (state.onDevice) Icons.Outlined.PhoneAndroid else Icons.Outlined.CloudQueue,
                    stringResource(if (state.onDevice) R.string.voice_on_device else R.string.voice_online),
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (listening) {
                    OutlinedButton(onClick = actions.pause, modifier = Modifier.testTag(VoiceTestTags.PAUSE)) {
                        Icon(Icons.Outlined.Pause, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.voice_pause), maxLines = 1)
                    }
                } else {
                    OutlinedButton(onClick = actions.start, modifier = Modifier.testTag(VoiceTestTags.START)) {
                        Icon(Icons.Outlined.Mic, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.voice_resume), maxLines = 1)
                    }
                }
                Button(onClick = actions.done, modifier = Modifier.testTag(VoiceTestTags.STOP)) { Text(stringResource(R.string.voice_done), maxLines = 1) }
            }
        }
    }
}

@Composable
private fun ListeningDot(listening: Boolean) {
    val pulse = rememberInfiniteTransition(label = "listening")
    val a by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha")
    Box(
        Modifier
            .size(12.dp)
            .alpha(if (listening) a else 1f)
            .background(if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline, CircleShape),
    )
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

private fun languageName(l: VoiceLanguage) = when (l) {
    VoiceLanguage.ENGLISH_INDIA -> R.string.voice_lang_in
    VoiceLanguage.HINDI -> R.string.voice_lang_hi
    VoiceLanguage.HINGLISH -> R.string.voice_lang_hinglish
    VoiceLanguage.ENGLISH_US -> R.string.voice_lang_us
    VoiceLanguage.ENGLISH_UK -> R.string.voice_lang_uk
}
