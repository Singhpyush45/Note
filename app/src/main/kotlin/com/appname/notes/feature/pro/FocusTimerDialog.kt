package com.appname.notes.feature.pro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.feature.lock.AppLock

/**
 * Compact Focus / Pomodoro timer chip that can sit in the Planner or Editor top bar.
 */
@Composable
fun FocusTimerChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by NoteProStore.timer.collectAsStateWithLifecycle()
    val active = state.running || state.remainingSeconds < state.totalSeconds
    val containerColor = if (state.running) {
        MaterialTheme.colorScheme.primaryContainer
    } else if (active) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
    }
    val contentColor = if (state.running) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else if (active) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("focus_timer_chip"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = stringResource(R.string.focus_timer_title),
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            if (active) {
                Text(
                    text = state.formattedTime,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
            }
        }
    }
}

/**
 * Full Focus / Pomodoro Study Timer dialog with preset selection (25m Focus, 45m Deep Work, 5m/15m Break),
 * progress ring/bar, session counter, and Start/Pause/Reset controls.
 */
@Composable
fun FocusTimerDialog(
    onDismiss: () -> Unit,
) {
    val state by NoteProStore.timer.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.focus_timer_title), style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Preset pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                        .padding(3.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    FocusPreset.entries.forEach { preset ->
                        val selected = state.preset == preset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 34.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(50),
                                )
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { NoteProStore.selectPreset(preset) },
                                )
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${preset.minutes}m",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Large digital countdown display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.preset.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.formattedTime,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(0.75f).height(6.dp).clip(CircleShape),
                        )
                        if (state.completedSessions > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.focus_sessions_completed, state.completedSessions),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Controls: Start/Pause + Reset
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { NoteProStore.toggleTimer() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (state.running) R.string.focus_pause else R.string.focus_start))
                    }
                    FilledTonalButton(
                        onClick = { NoteProStore.resetTimer() },
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.focus_reset), modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

/**
 * Dialog for unlocking or setting a Per-Note Lock PIN when biometric/device screen lock is not configured,
 * or triggers Android's BiometricPrompt first when available.
 */
@Composable
fun NoteLockPromptDialog(
    title: String,
    onAuthenticated: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: androidx.activity.compose.LocalActivity.current as? FragmentActivity
    var triedBiometric by remember { mutableStateOf(false) }
    var showPinFallback by remember { mutableStateOf(!AppLock.canLock(context) || activity == null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!triedBiometric && AppLock.canLock(context) && activity != null) {
            triedBiometric = true
            AppLock.prompt(activity, title) { ok ->
                if (ok) onAuthenticated() else onDismiss()
            }
        } else {
            showPinFallback = true
        }
    }

    if (showPinFallback) {
        val existingPin = remember { NoteProStore.getFallbackPin(context) }
        var pin by rememberSaveable { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        val isSettingPin = existingPin == null
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (isSettingPin) stringResource(R.string.note_lock_set_pin_title) else title)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            if (isSettingPin) R.string.note_lock_set_pin_body else R.string.note_lock_enter_pin_body,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 8 && it.all(Char::isDigit)) {
                                pin = it
                                error = false
                            }
                        },
                        label = { Text(stringResource(R.string.note_lock_pin_label)) },
                        singleLine = true,
                        isError = error,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().testTag("note_lock_pin_field"),
                    )
                    if (error) {
                        Text(
                            text = stringResource(R.string.note_lock_pin_wrong),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pin.length >= 4,
                    onClick = {
                        if (isSettingPin) {
                            NoteProStore.setFallbackPin(context, pin)
                            onAuthenticated()
                        } else if (pin == existingPin) {
                            onAuthenticated()
                        } else {
                            error = true
                        }
                    },
                    modifier = Modifier.testTag("note_lock_pin_confirm"),
                ) {
                    Text(stringResource(if (isSettingPin) R.string.action_save else R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
