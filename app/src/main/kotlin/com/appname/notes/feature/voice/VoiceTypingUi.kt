package com.appname.notes.feature.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.appname.notes.R

object VoiceTestTags {
    const val LABEL = "voice_label"
    const val MENU = "voice_menu"
    const val DIALOG = "voice_pack_dialog"
    const val DOWNLOAD = "voice_pack_download"
    fun language(l: VoiceLanguage) = "voice_lang_${l.key}"
    fun source(s: VoiceSource) = "voice_source_${s.name}"
}

/** "Recording on" green (fixed, so it reads as "on" whatever the theme or wallpaper colours are). */
private val LiveGreen = Color(0xFF1E8E3E)

data class VoiceMicState(
    val language: VoiceLanguage,
    val source: VoiceSource,
    val status: LiveTranscriber.Status,
    /** Loudness 0..1 of what is heard. */
    val level: Float,
    /** 0..1 while the language's pack downloads. */
    val download: Float?,
    val phoneAudioSupported: Boolean,
)

/**
 * Voice typing as one microphone button in the formatting toolbar, like a keyboard's mic (owner request: no extra
 * panel). Tap: it turns green and pulses with the sound while words are written into the note; tap again: off.
 * Touch and hold (or tap the small language label while it is on): language and sound source. While the voice pack
 * downloads, a ring around the mic shows the progress.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceMic(
    state: VoiceMicState,
    onTap: () -> Unit,
    onLanguage: (VoiceLanguage) -> Unit,
    onSource: (VoiceSource) -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val listening = state.status == LiveTranscriber.Status.LISTENING
    val starting = state.status == LiveTranscriber.Status.STARTING
    val on = listening || starting
    // Keep the screen on while listening, so a long lecture isn't cut off by the screen timeout.
    val view = LocalView.current
    DisposableEffect(listening) {
        view.keepScreenOn = listening
        onDispose { view.keepScreenOn = false }
    }
    val description = stringResource(if (on) R.string.voice_mic_on else R.string.voice_mic_off)
    val stateText = stringResource(if (on) R.string.voice_state_on else R.string.voice_state_off)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = buttonModifier
                    .size(48.dp)
                    .then(if (listening) Modifier.pulseRings(state.level) else Modifier)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 24.dp),
                        role = Role.Button,
                        onClick = onTap,
                        onLongClick = { menu = true },
                    )
                    .semantics {
                        contentDescription = description
                        stateDescription = stateText
                    },
            ) {
                when {
                    state.download != null -> {
                        CircularProgressIndicator(progress = { state.download }, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                        Icon(Icons.Outlined.Mic, null, Modifier.size(18.dp))
                    }
                    listening -> Box(Modifier.size(36.dp).background(LiveGreen, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Mic, null, Modifier.size(20.dp), tint = Color.White)
                    }
                    starting -> {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp, color = LiveGreen)
                        Icon(Icons.Filled.Mic, null, Modifier.size(20.dp), tint = LiveGreen)
                    }
                    else -> Icon(Icons.Outlined.Mic, null)
                }
            }
            VoiceMenu(menu, state, onDismiss = { menu = false }, onLanguage = { menu = false; onLanguage(it) }, onSource = { menu = false; onSource(it) })
        }
        if (on) {
            // Which language is being written; tap to change it.
            Text(
                stringResource(shortName(state.language)),
                style = MaterialTheme.typography.labelMedium,
                color = LiveGreen,
                maxLines = 1,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .combinedClickable(onClick = { menu = true })
                    .padding(end = 6.dp, top = 15.dp)
                    .testTag(VoiceTestTags.LABEL),
            )
        }
    }
}

/** A halo that follows the loudness and rings moving out of the green mic. */
@Composable
private fun Modifier.pulseRings(level: Float): Modifier {
    val t = rememberInfiniteTransition(label = "pulse")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val loud by animateFloatAsState(level.coerceIn(0f, 1f), tween(120), label = "level")
    return drawBehind {
        val base = 18.dp.toPx()
        val extra = 5.dp.toPx()
        // The sound itself: a soft halo that follows the loudness.
        drawCircle(LiveGreen.copy(alpha = 0.22f), radius = base + extra * (loud * 4f).coerceAtMost(1f))
        // A steady ring moving outwards, so "on" is visible even in silence.
        for (k in 0..1) {
            val p = (phase + k * 0.5f) % 1f
            drawCircle(LiveGreen.copy(alpha = 0.35f * (1f - p)), radius = base + extra * p, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun VoiceMenu(
    open: Boolean,
    state: VoiceMicState,
    onDismiss: () -> Unit,
    onLanguage: (VoiceLanguage) -> Unit,
    onSource: (VoiceSource) -> Unit,
) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss, modifier = Modifier.testTag(VoiceTestTags.MENU)) {
        MenuHeader(R.string.voice_menu_language)
        VoiceLanguage.entries.forEach { l ->
            DropdownMenuItem(
                text = { Text(stringResource(languageName(l))) },
                onClick = { onLanguage(l) },
                leadingIcon = { Check(l == state.language) },
                modifier = Modifier.testTag(VoiceTestTags.language(l)),
            )
        }
        if (state.phoneAudioSupported) {
            HorizontalDivider()
            MenuHeader(R.string.voice_menu_source)
            VoiceSource.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(stringResource(sourceName(s))) },
                    onClick = { onSource(s) },
                    leadingIcon = { Check(s == state.source) },
                    modifier = Modifier.testTag(VoiceTestTags.source(s)),
                )
            }
        }
    }
}

@Composable
private fun MenuHeader(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Check(on: Boolean) {
    if (on) Icon(Icons.Filled.Check, null, Modifier.size(20.dp)) else Spacer(Modifier.width(20.dp))
}

/**
 * Shown the first time voice typing is used (or for a language not downloaded yet): pick the language; its voice
 * pack is downloaded once. A language already on the phone starts at once.
 */
@Composable
fun VoicePackDialog(
    initial: VoiceLanguage,
    installed: (VoiceLanguage) -> Boolean,
    onConfirm: (VoiceLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by rememberSaveable { mutableStateOf(initial) }
    val ready = installed(picked)
    AlertDialog(
        modifier = Modifier.testTag(VoiceTestTags.DIALOG),
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Mic, null) },
        title = { Text(stringResource(R.string.voice_pack_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.voice_pack_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(8.dp))
                VoiceLanguage.entries.forEach { l ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = l == picked, onClick = { picked = l }, role = Role.RadioButton)
                            .testTag(VoiceTestTags.language(l)),
                    ) {
                        RadioButton(selected = l == picked, onClick = null)
                        Text(stringResource(languageName(l)), Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (installed(l)) stringResource(R.string.voice_pack_ready) else stringResource(R.string.voice_pack_size, l.pack.sizeMb),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(picked) }, modifier = Modifier.testTag(VoiceTestTags.DOWNLOAD), shape = RoundedCornerShape(50)) {
                Text(stringResource(if (ready) R.string.voice_pack_start else R.string.voice_pack_download))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun sourceName(s: VoiceSource) = when (s) {
    VoiceSource.MICROPHONE -> R.string.voice_source_mic
    VoiceSource.PHONE_AUDIO -> R.string.voice_source_phone
}

fun languageName(l: VoiceLanguage) = when (l) {
    VoiceLanguage.ENGLISH_INDIA -> R.string.voice_lang_in
    VoiceLanguage.HINDI -> R.string.voice_lang_hi
    VoiceLanguage.HINGLISH -> R.string.voice_lang_hinglish
    VoiceLanguage.ENGLISH_US -> R.string.voice_lang_us
    VoiceLanguage.ENGLISH_UK -> R.string.voice_lang_uk
}

private fun shortName(l: VoiceLanguage) = when (l) {
    VoiceLanguage.ENGLISH_INDIA -> R.string.voice_short_in
    VoiceLanguage.HINDI -> R.string.voice_short_hi
    VoiceLanguage.HINGLISH -> R.string.voice_short_hinglish
    VoiceLanguage.ENGLISH_US -> R.string.voice_short_us
    VoiceLanguage.ENGLISH_UK -> R.string.voice_short_uk
}
