package com.appname.notes.feature.audio

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.domain.model.Attachment
import com.appname.notes.core.domain.model.AudioMarker
import com.appname.notes.core.domain.model.NoteId
import kotlinx.coroutines.delay

object AudioTestTags {
    const val RECORDING_BAR = "audio_recording_bar"
    const val ADD_MARKER = "audio_add_marker"
    const val STOP = "audio_stop"
    const val RECORD = "attach_record"
    fun player(a: Attachment) = "audio_player_${a.id.value}"
    fun marker(m: AudioMarker) = "audio_marker_${m.positionMs}"
    const val SPEED = "audio_speed"
}

/** Everything a recording's player row needs (FR-012). */
data class AudioControls(
    val playback: PlaybackState,
    val play: (Attachment) -> Unit,
    val pause: () -> Unit,
    val seek: (Attachment, Long) -> Unit,
    val markers: (Attachment) -> List<AudioMarker>,
    val playMarker: (AudioMarker) -> Unit,
    val setSpeed: (Float) -> Unit = {},
)

/**
 * Shown under the top bar while a recording runs (FR-012 "display recording state"): a red dot and the
 * running time (announced politely to TalkBack), "Add marker" for the note being recorded into, and Stop.
 */
@Composable
fun RecordingBar(state: RecordingState, noteId: NoteId, onAddMarker: () -> Unit, onStop: () -> Unit) {
    val recording = (state as? RecordingState.Recording)?.recording ?: return
    val here = recording.noteId == noteId
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording.startedAtElapsed) {
        while (true) {
            elapsed = recording.startedAtElapsed?.let { SystemClock.elapsedRealtime() - it } ?: 0
            delay(500)
        }
    }
    val label = if (here) stringResource(R.string.audio_recording_elapsed, AudioMarker.formatDuration(elapsed)) else stringResource(R.string.audio_recording_elsewhere)
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().testTag(AudioTestTags.RECORDING_BAR)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (here) {
                TextButton(onClick = onAddMarker, modifier = Modifier.testTag(AudioTestTags.ADD_MARKER)) {
                    Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.audio_add_marker), Modifier.padding(start = 4.dp))
                }
            }
            TextButton(onClick = onStop, modifier = Modifier.testTag(AudioTestTags.STOP)) {
                Icon(Icons.Outlined.Stop, null, Modifier.size(18.dp))
                Text(stringResource(R.string.audio_stop), Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** A recording in the note: play/pause, a seek bar, time, and its markers as chips that play from there. */
@Composable
fun AudioPlayerRow(a: Attachment, controls: AudioControls, onMore: (() -> Unit)?) {
    val active = controls.playback.attachmentId == a.id
    val duration = (if (active) controls.playback.durationMs else 0).takeIf { it > 0 } ?: a.durationMs ?: 0
    val position = if (active) controls.playback.positionMs else 0
    val playing = active && controls.playback.playing
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    OutlinedCard(Modifier.fillMaxWidth().testTag(AudioTestTags.player(a))) {
        Column(Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { if (playing) controls.pause() else controls.play(a) }) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        stringResource(if (playing) R.string.audio_pause else R.string.audio_play),
                    )
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Mic, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            a.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Text(
                        "${AudioMarker.formatDuration(if (dragging) dragValue.toLong() else position)} / ${AudioMarker.formatDuration(duration)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val speed = controls.playback.speed
                val speedDescription = stringResource(R.string.audio_speed, AudioPlayback.label(speed))
                TextButton(
                    onClick = { controls.setSpeed(AudioPlayback.nextSpeed(speed)) },
                    modifier = Modifier.testTag(AudioTestTags.SPEED).semantics { contentDescription = speedDescription },
                ) { Text(AudioPlayback.label(speed), style = MaterialTheme.typography.labelLarge) }
                if (onMore != null) {
                    IconButton(onClick = onMore) {
                        Icon(Icons.Outlined.MoreVert, stringResource(R.string.attach_more_for, a.displayName))
                    }
                }
            }
            val seekLabel = stringResource(R.string.audio_position)
            Slider(
                value = if (dragging) dragValue else position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { dragging = false; controls.seek(a, dragValue.toLong()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                enabled = duration > 0,
                modifier = Modifier.semantics { contentDescription = seekLabel },
            )
            val markers = controls.markers(a)
            if (markers.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    markers.forEach { m ->
                        val description = stringResource(R.string.audio_play_from, m.label)
                        AssistChip(
                            onClick = { controls.playMarker(m) },
                            label = { Text(m.label) },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, null, Modifier.size(16.dp)) },
                            modifier = Modifier.testTag(AudioTestTags.marker(m)).semantics { contentDescription = description },
                        )
                    }
                }
            }
        }
    }
}
