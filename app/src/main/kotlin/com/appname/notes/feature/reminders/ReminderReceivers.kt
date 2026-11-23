package com.appname.notes.feature.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appname.notes.core.domain.model.ReminderId
import com.appname.notes.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun scheduler(): ReminderScheduler

    @ApplicationScope
    fun appScope(): CoroutineScope
}

/**
 * Runs [work] after onReceive returns, keeping the process alive until it finishes (at most ~9 s; Android
 * allows 10 s for a receiver).
 */
private fun BroadcastReceiver.runAsync(context: Context, work: suspend (ReminderScheduler) -> Unit) {
    val entry = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
    val pending = goAsync()
    entry.appScope().launch {
        try {
            withTimeoutOrNull(WORK_TIMEOUT_MS) { work(entry.scheduler()) }
        } finally {
            pending.finish()
        }
    }
}

private const val WORK_TIMEOUT_MS = 9_000L

/** The reminder alarm rang, or "Snooze" was tapped on a reminder notification. Not exported: only our intents. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RING -> runAsync(context) { it.deliverDue() }
            ACTION_SNOOZE -> {
                val id = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
                runAsync(context) { it.snooze(ReminderId(id)) }
            }
        }
    }

    companion object {
        const val ACTION_RING = "com.appname.notes.action.REMINDER_RING"
        const val ACTION_SNOOZE = "com.appname.notes.action.REMINDER_SNOOZE"
        const val EXTRA_REMINDER_ID = "reminderId"
    }
}

/**
 * Re-arms the reminder alarm after events that clear or change it: reboot, app update, the clock or time zone
 * being changed, and the "Alarms & reminders" permission being granted (switching to exact timing).
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        runAsync(context) { it.rescheduleNow() }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (Android 12+).
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
