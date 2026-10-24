package com.appname.notes.feature.reminders

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.Reminder
import com.appname.notes.core.domain.model.ReminderId
import com.appname.notes.core.domain.model.ReminderRepeat
import com.appname.notes.core.domain.repository.ReminderRepository
import com.appname.notes.ui.common.UserMessage
import com.appname.notes.ui.common.toUserMessage
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One note's reminders for an editor's ViewModel (FR-010); the same for text, handwritten and PDF notes.
 * Created through [Factory] with the ViewModel's scope and a way to show messages.
 */
class NoteReminders(
    private val noteId: NoteId,
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val scope: CoroutineScope,
    private val onMessage: (UserMessage) -> Unit,
    /** Runs before a reminder is saved (the text editor saves the note so a task's text is stored). */
    private val beforeSave: (blockId: String?) -> Unit,
) {
    class Factory @Inject constructor(
        private val repository: ReminderRepository,
        private val scheduler: ReminderScheduler,
        private val notifier: ReminderNotifier,
    ) {
        fun create(noteId: NoteId, scope: CoroutineScope, beforeSave: (String?) -> Unit = {}, onMessage: (UserMessage) -> Unit) =
            NoteReminders(noteId, repository, scheduler, notifier, scope, onMessage, beforeSave)
    }

    /** Pending first; null until read. */
    val reminders: StateFlow<List<Reminder>?> =
        repository.observe(noteId).stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Notifications are allowed, so reminders can alert. Can change in system settings at any time. */
    fun canAlert(): Boolean = notifier.canAlert

    fun canScheduleExact(): Boolean = scheduler.canScheduleExact

    /** Sets a new reminder ([existing] null) or changes one. */
    fun save(existing: ReminderId?, blockId: String?, at: LocalDateTime, repeat: ReminderRepeat) {
        beforeSave(blockId)
        scope.launch {
            val zone = ZoneId.systemDefault()
            val r = if (existing == null) repository.add(noteId, blockId, at, repeat, zone) else repository.update(existing, at, repeat, zone)
            onMessage(
                when (r) {
                    is AppResult.Success -> UserMessage(R.string.reminder_set)
                    is AppResult.Failure -> r.error.toUserMessage()
                },
            )
        }
    }

    fun delete(id: ReminderId) {
        scope.launch {
            when (val r = repository.remove(id)) {
                is AppResult.Success -> {
                    notifier.cancel(id)
                    onMessage(UserMessage(R.string.reminder_removed, undo = { scope.launch { repository.restore(r.value) } }))
                }
                is AppResult.Failure -> onMessage(r.error.toUserMessage())
            }
        }
    }
}

/** The reminder dialog and the permission/settings flows around it, for one editor screen. */
@Stable
class ReminderUi internal constructor(
    private val openTarget: (String) -> Unit,
    canAlertState: () -> Boolean,
    private val fix: () -> Unit,
) {
    private val readCanAlert = canAlertState

    /** Whether reminders can alert right now (re-read whenever the screen resumes). */
    val canAlert: Boolean get() = readCanAlert()

    /** New reminder for the note, or for checklist item [blockId]. */
    fun add(blockId: String? = null) = openTarget("new:${blockId.orEmpty()}")

    fun edit(reminder: Reminder) = openTarget("edit:${reminder.id.value}")

    /** Asks for the notification permission, or opens the settings where it can be turned on. */
    fun fixAlerts() = fix()
}

/**
 * Remembers the dialog state (kept across rotation) and shows the dialog when open.
 * [taskText] gives a checklist item's text (null when it's gone; text notes only).
 */
@Composable
fun rememberReminderUi(
    reminders: NoteReminders,
    editable: Boolean,
    taskText: (String) -> String? = { null },
): ReminderUi {
    val context = LocalContext.current
    val currentEditable by rememberUpdatedState(editable)
    val loaded by reminders.reminders.collectAsStateWithLifecycle()
    var canAlert by remember { mutableStateOf(reminders.canAlert()) }
    var canExact by remember { mutableStateOf(reminders.canScheduleExact()) }
    LifecycleResumeEffect(Unit) {
        canAlert = reminders.canAlert()
        canExact = reminders.canScheduleExact()
        onPauseOrDispose {}
    }
    // "new:<checklist item id or empty>" or "edit:<reminder id>".
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canAlert = reminders.canAlert()
        val activity = context.findActivity()
        // Refused for good ("Don't ask again", or twice): only the system settings can change it now.
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            openNotificationSettings(context)
        }
    }
    /** Asks for notifications (Android 13+) if not granted yet; returns false when there is nothing to ask. */
    val askNotifications: () -> Boolean = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            true
        } else {
            false
        }
    }

    target?.let { t ->
        val list = loaded
        val existing = if (t.startsWith("edit:")) list.orEmpty().firstOrNull { it.id.value == t.removePrefix("edit:") } else null
        if (!editable) {
            LaunchedEffect(t) { target = null }
        } else if (t.startsWith("edit:") && existing == null) {
            // Deleted meanwhile (once read; right after rotation the list may not be loaded yet).
            if (list != null) LaunchedEffect(t) { target = null }
        } else {
            val blockId = existing?.blockId ?: t.removePrefix("new:").ifEmpty { null }
            ReminderDialog(
                existing = existing,
                taskText = blockId?.let(taskText),
                canScheduleExact = canExact,
                onAllowExact = { openExactAlarmSettings(context) },
                onSave = { at, repeat ->
                    target = null
                    reminders.save(existing?.id, blockId, at, repeat)
                    askNotifications()
                },
                onDelete = existing?.let { r -> { target = null; reminders.delete(r.id) } },
                onDismiss = { target = null },
            )
        }
    }

    return remember(reminders) {
        ReminderUi(
            openTarget = { if (currentEditable) target = it },
            canAlertState = { canAlert },
            fix = { if (!askNotifications()) openNotificationSettings(context) },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The app's notification settings, where a refused or switched-off notification permission can be turned on. */
fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }
}

/** "Alarms & reminders" access for this app (Android 12+), so reminders go off on the minute. */
fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    try {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }
}
