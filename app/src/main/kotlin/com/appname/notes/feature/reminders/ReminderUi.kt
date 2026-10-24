package com.appname.notes.feature.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.appname.notes.R
import com.appname.notes.core.domain.model.Reminder
import com.appname.notes.core.domain.model.ReminderPresets
import com.appname.notes.core.domain.model.ReminderRepeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

object ReminderTestTags {
    const val ROW = "reminders_row"
    const val ALERTS_OFF = "reminders_alerts_off"
    const val DIALOG = "reminder_dialog"
    const val DATE = "reminder_date"
    const val TIME = "reminder_time"
    const val REPEAT = "reminder_repeat"
    const val SAVE = "reminder_save"
    const val DELETE = "reminder_delete"
    const val EXACT_HINT = "reminder_exact_hint"
    const val MENU = "reminder_menu"
    fun chip(r: Reminder) = "reminder_${r.id.value}"
    fun preset(kind: ReminderPresets.Kind) = "reminder_preset_${kind.name}"
}

/**
 * The note's reminders under its title (FR-010): one chip each with its time ("Tomorrow, 9:00 AM"), the
 * task it's for, and whether it repeats; past one-off reminders are shown struck through until removed.
 * When notifications are off, a line says reminders can't alert and offers to fix it.
 */
@Composable
fun RemindersRow(
    reminders: List<Reminder>,
    taskText: (String) -> String?,
    canAlert: Boolean,
    onEdit: (Reminder) -> Unit,
    onFixAlerts: () -> Unit,
) {
    if (reminders.isEmpty()) return
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().testTag(ReminderTestTags.ROW), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!canAlert && reminders.any { it.isPending }) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.testTag(ReminderTestTags.ALERTS_OFF)) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        stringResource(R.string.reminder_alerts_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    TextButton(onClick = onFixAlerts) { Text(stringResource(R.string.reminder_turn_on)) }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reminders.forEach { r ->
                val time = ReminderDates.format(context, LocalDateTime.ofInstant(r.triggerAt, ZoneId.systemDefault()))
                val task = r.blockId?.let { taskText(it) ?: stringResource(R.string.reminder_task_removed) }
                val label = listOfNotNull(task, time).joinToString(" · ")
                val repeat = r.repeat.takeIf { it != ReminderRepeat.NONE }?.let { stringResource(ReminderDates.repeatLabel(it)) }
                val description = listOfNotNull(
                    stringResource(if (r.isPending) R.string.reminder_chip_pending else R.string.reminder_chip_past, label),
                    repeat,
                ).joinToString(", ")
                AssistChip(
                    onClick = { onEdit(r) },
                    label = {
                        Text(
                            label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (r.isPending) null else TextDecoration.LineThrough,
                        )
                    },
                    leadingIcon = { Icon(if (r.isPending) Icons.Outlined.Alarm else Icons.Outlined.AlarmOff, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    trailingIcon = if (repeat != null) {
                        { Icon(Icons.Outlined.Repeat, null, Modifier.size(AssistChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag(ReminderTestTags.chip(r)).semantics { contentDescription = description },
                )
            }
        }
    }
}

/**
 * Sets or edits a reminder: quick choices ("Later today", "Tomorrow morning"...), a date and a time, and how
 * it repeats. A time in the past can't be saved. Without exact-alarm access it says reminders may be a few
 * minutes late and links to the setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    existing: Reminder?,
    taskText: String?,
    canScheduleExact: Boolean,
    onAllowExact: () -> Unit,
    onSave: (LocalDateTime, ReminderRepeat) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    now: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    val context = LocalContext.current
    val presets = remember { ReminderPresets.at(now()) }
    val initial = remember {
        existing?.let { LocalDateTime.ofInstant(it.triggerAt, ZoneId.systemDefault()) }
            ?.takeIf { it.isAfter(now().toLocalDateTime()) }
            ?: presets.first { it.kind == ReminderPresets.Kind.TOMORROW_MORNING }.at
    }
    var date by rememberSaveable { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable { mutableStateOf(initial.toLocalTime().withSecond(0).toString()) }
    var repeat by rememberSaveable { mutableStateOf((existing?.repeat ?: ReminderRepeat.NONE).name) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) } // "date" | "time"
    val chosen = LocalDate.parse(date).atTime(LocalTime.parse(time))
    val inFuture = chosen.isAfter(now().toLocalDateTime())

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ReminderTestTags.DIALOG),
        icon = { Icon(Icons.Outlined.Alarm, null) },
        title = { Text(stringResource(if (existing == null) R.string.reminder_add else R.string.reminder_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                taskText?.let {
                    Text(stringResource(R.string.reminder_for_task, it), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p ->
                        SuggestionChip(
                            onClick = {
                                date = p.at.toLocalDate().toString()
                                time = p.at.toLocalTime().toString()
                            },
                            label = { Text(stringResource(presetLabel(p.kind))) },
                            modifier = Modifier.testTag(ReminderTestTags.preset(p.kind)),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = "date" }, modifier = Modifier.weight(1.4f).testTag(ReminderTestTags.DATE)) {
                        Text(ReminderDates.format(context, chosen).substringBeforeLast(", "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = { picking = "time" }, modifier = Modifier.weight(1f).testTag(ReminderTestTags.TIME)) {
                        Text(ReminderDates.format(context, chosen).substringAfterLast(", "), maxLines = 1)
                    }
                }
                RepeatPicker(ReminderRepeat.fromName(repeat)) { repeat = it.name }
                if (!inFuture) {
                    Text(stringResource(R.string.reminder_in_past), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (!canScheduleExact) {
                    Column(Modifier.testTag(ReminderTestTags.EXACT_HINT)) {
                        Text(stringResource(R.string.reminder_inexact), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onAllowExact) { Text(stringResource(R.string.reminder_allow_exact)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(chosen, ReminderRepeat.fromName(repeat)) },
                enabled = inFuture,
                modifier = Modifier.testTag(ReminderTestTags.SAVE),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.testTag(ReminderTestTags.DELETE)) { Text(stringResource(R.string.reminder_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )

    when (picking) {
        "date" -> {
            val today = now().toLocalDate()
            val state = rememberDatePickerState(
                initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)

                    override fun isSelectableYear(year: Int): Boolean = year >= today.year
                },
            )
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                        picking = null
                    }) { Text(stringResource(R.string.action_ok)) }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel)) } },
            ) { DatePicker(state) }
        }
        "time" -> {
            val current = LocalTime.parse(time)
            val state = rememberTimePickerState(current.hour, current.minute, android.text.format.DateFormat.is24HourFormat(context))
            Dialog(onDismissRequest = { picking = null }) {
                Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        TimePicker(state)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel)) }
                            TextButton(onClick = {
                                time = LocalTime.of(state.hour, state.minute).toString()
                                picking = null
                            }) { Text(stringResource(R.string.action_ok)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepeatPicker(value: ReminderRepeat, onPick: (ReminderRepeat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag(ReminderTestTags.REPEAT)) {
            Icon(Icons.Outlined.Repeat, null, Modifier.size(18.dp))
            Text(stringResource(ReminderDates.repeatLabel(value)), Modifier.weight(1f).padding(horizontal = 8.dp))
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ReminderRepeat.entries.forEach { r ->
                DropdownMenuItem(text = { Text(stringResource(ReminderDates.repeatLabel(r))) }, onClick = { open = false; onPick(r) })
            }
        }
    }
}

private fun presetLabel(kind: ReminderPresets.Kind) = when (kind) {
    ReminderPresets.Kind.LATER_TODAY -> R.string.reminder_preset_later
    ReminderPresets.Kind.THIS_EVENING -> R.string.reminder_preset_evening
    ReminderPresets.Kind.TOMORROW_MORNING -> R.string.reminder_preset_tomorrow
    ReminderPresets.Kind.NEXT_WEEK -> R.string.reminder_preset_next_week
}

/**
 * For handwritten and PDF notes, where the page has no room for chips: an alarm button in the top bar (only
 * when the note has reminders) listing them, each opening the dialog, plus "Remind me" and the alerts warning.
 */
@Composable
fun ReminderMenuButton(reminders: List<Reminder>, ui: ReminderUi, editable: Boolean) {
    if (reminders.isEmpty()) return
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val pending = reminders.any { it.isPending }
    Box {
        androidx.compose.material3.IconButton(onClick = { open = true }, modifier = Modifier.testTag(ReminderTestTags.MENU)) {
            Icon(
                if (!ui.canAlert && pending) Icons.Outlined.NotificationsOff else Icons.Outlined.Alarm,
                stringResource(R.string.reminder_menu, reminders.size),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (!ui.canAlert && pending) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reminder_alerts_off), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.NotificationsOff, null) },
                    onClick = { open = false; ui.fixAlerts() },
                )
            }
            reminders.forEach { r ->
                val time = ReminderDates.format(context, LocalDateTime.ofInstant(r.triggerAt, ZoneId.systemDefault()))
                DropdownMenuItem(
                    text = { Text(time, textDecoration = if (r.isPending) null else TextDecoration.LineThrough) },
                    leadingIcon = { Icon(if (r.isPending) Icons.Outlined.Alarm else Icons.Outlined.AlarmOff, null) },
                    trailingIcon = if (r.repeat != ReminderRepeat.NONE) {
                        { Icon(Icons.Outlined.Repeat, stringResource(ReminderDates.repeatLabel(r.repeat))) }
                    } else {
                        null
                    },
                    enabled = editable,
                    onClick = { open = false; ui.edit(r) },
                )
            }
            if (editable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reminder_add)) },
                    leadingIcon = { Icon(Icons.Outlined.AlarmAdd, null) },
                    onClick = { open = false; ui.add() },
                )
            }
        }
    }
}
