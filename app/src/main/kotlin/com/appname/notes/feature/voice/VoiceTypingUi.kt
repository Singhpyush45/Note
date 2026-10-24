package com.appname.notes.feature.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.feature.voice.VoiceTypingState.Status

object VoiceTestTags {
    const val SHEET = "voice_sheet"
    const val START = "voice_start"
    const val PAUSE = "voice_pause"
    const val STOP = "voice_stop"
    const val INSERT = "voice_insert"
    const val TEXT = "voice_text"
    const val UNAVAILABLE = "voice_unavailable"
    fun language(l: VoiceLanguage) = "voice_lang_${l.tag}"
}

data class VoiceTypingActions(
    val start: () -> Unit,
    val pause: () -> Unit,
    val stop: () -> Unit,
    val edit: (String) -> Unit,
    val insert: () -> Unit,
    val language: (VoiceLanguage) -> Unit,
    val useOnline: () -> Unit,
    val dismiss: () -> Unit,
)

/**
 * Voice typing (§11): choose English (India/US/UK), see whether speech stays on the phone or goes to the phone's
 * speech service, then Start. Words appear as they're recognised; Pause/Resume, Stop to check and edit, Insert to
 * put the text at the cursor as normal editable text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTypingSheet(state: VoiceTypingState, actions: VoiceTypingActions) {
    ModalBottomSheet(
        onDismissRequest = actions.dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(VoiceTestTags.SHEET),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.voice_title), style = MaterialTheme.typography.titleLarge)
            if (state.status == Status.UNAVAILABLE) {
                Text(
                    stringResource(R.string.voice_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(VoiceTestTags.UNAVAILABLE),
                )
                TextButton(onClick = actions.dismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_close)) }
                return@Column
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceLanguage.entries.forEach { l ->
                    FilterChip(
                        selected = state.language == l,
                        onClick = { actions.language(l) },
                        label = { Text(stringResource(languageName(l))) },
                        modifier = Modifier.testTag(VoiceTestTags.language(l)),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.onDevice) Icons.Outlined.PhoneAndroid else Icons.Outlined.CloudQueue,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(if (state.onDevice) R.string.voice_on_device else R.string.voice_online),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            if (state.status == Status.REVIEW) {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = actions.edit,
                    label = { Text(stringResource(R.string.voice_review)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag(VoiceTestTags.TEXT),
                )
            } else {
                val faded = MaterialTheme.colorScheme.onSurfaceVariant
                val shown = buildAnnotatedString {
                    append(state.text)
                    if (state.partial.isNotBlank()) {
                        if (state.text.isNotEmpty()) append(' ')
                        withStyle(SpanStyle(color = faded)) { append(state.partial) }
                    }
                }
                Text(
                    if (shown.isEmpty()) {
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = faded)) {
                                append(stringResource(if (state.status == Status.LISTENING) R.string.voice_speak_now else R.string.voice_hint))
                            }
                        }
                    } else {
                        shown
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .testTag(VoiceTestTags.TEXT)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            statusText(state)?.let { (text, isError) ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (state.error == SpeechError.LANGUAGE_UNAVAILABLE && state.onDevice) {
                TextButton(onClick = actions.useOnline) { Text(stringResource(R.string.voice_use_online)) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (state.status) {
                    Status.LISTENING -> {
                        OutlinedButton(onClick = actions.pause, modifier = Modifier.testTag(VoiceTestTags.PAUSE)) { Text(stringResource(R.string.voice_pause)) }
                        OutlinedButton(onClick = actions.stop, modifier = Modifier.testTag(VoiceTestTags.STOP)) { Text(stringResource(R.string.voice_stop)) }
                    }
                    Status.PAUSED, Status.REVIEW -> {
                        OutlinedButton(onClick = actions.start, modifier = Modifier.testTag(VoiceTestTags.START)) {
                            Icon(Icons.Outlined.Mic, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.voice_resume))
                        }
                        if (state.status == Status.PAUSED) {
                            OutlinedButton(onClick = actions.stop, modifier = Modifier.testTag(VoiceTestTags.STOP)) { Text(stringResource(R.string.voice_stop)) }
                        }
                    }
                    Status.READY -> Button(onClick = actions.start, modifier = Modifier.testTag(VoiceTestTags.START)) {
                        Icon(Icons.Outlined.Mic, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.voice_start))
                    }
                    Status.UNAVAILABLE -> Unit
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = actions.dismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = actions.insert, enabled = state.canInsert, modifier = Modifier.testTag(VoiceTestTags.INSERT)) {
                    Text(stringResource(R.string.voice_insert))
                }
            }
        }
    }
}

@Composable
private fun statusText(state: VoiceTypingState): Pair<String, Boolean>? {
    state.error?.let { e ->
        return stringResource(
            when (e) {
                SpeechError.NETWORK -> R.string.voice_error_network
                SpeechError.PERMISSION -> R.string.voice_error_permission
                SpeechError.BUSY -> R.string.voice_error_busy
                SpeechError.LANGUAGE_UNAVAILABLE -> R.string.voice_error_language
                SpeechError.UNAVAILABLE -> R.string.voice_unavailable
                else -> R.string.voice_error_other
            },
        ) to true
    }
    return when (state.status) {
        Status.LISTENING -> stringResource(R.string.voice_listening) to false
        Status.PAUSED -> stringResource(R.string.voice_paused) to false
        else -> null
    }
}

private fun languageName(l: VoiceLanguage) = when (l) {
    VoiceLanguage.ENGLISH_INDIA -> R.string.voice_lang_in
    VoiceLanguage.ENGLISH_US -> R.string.voice_lang_us
    VoiceLanguage.ENGLISH_UK -> R.string.voice_lang_uk
}
