package com.appname.notes.feature.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.appname.notes.MainActivity
import com.appname.notes.R
import com.appname.notes.core.domain.model.DueReminder
import com.appname.notes.core.domain.model.ReminderId
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The notification of a reminder that went off (FR-010): the task or note, the note it belongs to, "Missed"
 * when it is late (phone was off), tap to open the note, and Snooze. One notification per reminder, tagged with
 * its id, so a snooze or a re-fire replaces it instead of piling up.
 */
class ReminderNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** False when the user turned notifications off for the app or for reminders: reminders can't alert then. */
    val canAlert: Boolean
        get() {
            if (!manager.areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            val channel = manager.getNotificationChannel(CHANNEL) ?: return true // created on first use
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }

    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS is checked first (Android 13+); older versions need none
    fun show(due: DueReminder, now: Instant) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // denied: the reminder still counts as gone off; the editor shows that alerts are off
        }
        ensureChannel()
        val reminder = due.reminder
        val noteName = due.noteTitle.ifBlank { due.notePreview.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
            .ifBlank { context.getString(R.string.untitled) }
        val title = due.taskText ?: noteName
        val body = if (due.taskText != null) context.getString(R.string.reminder_in_note, noteName) else due.notePreview.takeIf { due.noteTitle.isNotBlank() }
        val missed = Duration.between(due.dueAt, now) > LATE
        val dueLabel = ReminderDates.format(context, LocalDateTime.ofInstant(due.dueAt, ZoneId.systemDefault()))

        val open = PendingIntent.getActivity(
            context,
            reminder.id.value.hashCode(),
            openNoteIntent(context, reminder.noteId.value, due.noteType.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            reminder.id.value.hashCode(),
            Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(ReminderAlarmReceiver.ACTION_SNOOZE)
                .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminder.id.value),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(body?.let { NotificationCompat.BigTextStyle().bigText(it) })
            .setSubText(if (missed) context.getString(R.string.reminder_missed, dueLabel) else null)
            .setWhen(due.dueAt.toEpochMilli())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.reminder_snooze, SNOOZE_MINUTES), snooze)
            .build()
        try {
            manager.notify(reminder.id.value, NOTIFICATION_ID, notification)
        } catch (e: SecurityException) { // permission revoked between the check and the call
            return
        }
    }

    fun cancel(id: ReminderId) = manager.cancel(id.value, NOTIFICATION_ID)

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.reminder_channel_description)
            },
        )
    }

    companion object {
        const val CHANNEL = "reminders"
        private const val NOTIFICATION_ID = 1
        private val LATE: Duration = Duration.ofMinutes(15)
        private val SNOOZE_MINUTES = ReminderScheduler.SNOOZE.toMinutes().toInt()

        const val ACTION_OPEN_NOTE = "com.appname.notes.action.OPEN_NOTE"
        const val EXTRA_NOTE_ID = "noteId"
        const val EXTRA_NOTE_TYPE = "noteType"

        fun openNoteIntent(context: Context, noteId: String, noteType: String): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_NOTE)
                .putExtra(EXTRA_NOTE_ID, noteId)
                .putExtra(EXTRA_NOTE_TYPE, noteType)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
