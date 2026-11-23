package com.appname.notes.feature.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.domain.model.ReminderId
import com.appname.notes.core.domain.repository.ReminderRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Makes reminders go off (FR-010) with **one** system alarm, always set for the soonest pending reminder:
 * when it rings, every reminder due by then is notified and the alarm moves to the next one. One alarm instead
 * of one per reminder keeps the system's alarm list small and the battery cost bounded (NFR-08).
 *
 * - Exact timing needs the "Alarms & reminders" permission (Android 12+), which the user may refuse. Without it
 *   the alarm is inexact: Android may deliver it a few minutes late, and the reminder dialog says so.
 * - Alarms don't survive a reboot or force-stop; [SystemEventsReceiver] and app start call [rescheduleNow], and
 *   anything due meanwhile goes off at once (marked as missed when it's late).
 * - Reminders keep working with notifications denied; they just can't alert (the editor says so).
 */
class ReminderScheduler(
    private val context: Context,
    private val reminders: ReminderRepository,
    private val notifier: ReminderNotifier,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
    private val logger: AppLogger,
) {
    private val alarms: AlarmManager? = context.getSystemService(AlarmManager::class.java)
    private var watching: Job? = null

    /** Keeps the alarm on the soonest reminder while the app runs (reminders added, edited, trashed...). */
    fun start() {
        if (watching != null) return
        watching = appScope.launch {
            reminders.observeNextDue().distinctUntilChanged().collect { arm(it) }
        }
    }

    suspend fun rescheduleNow() {
        when (val r = reminders.nextDue()) {
            is AppResult.Success -> arm(r.value)
            is AppResult.Failure -> retryLater()
        }
    }

    /** True when reminders can go off at the exact minute (always below Android 12). */
    val canScheduleExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms?.canScheduleExactAlarms() == true

    /** The alarm rang: notify everything due and move the alarm on. */
    suspend fun deliverDue() {
        val now = clock.now()
        when (val r = reminders.takeDue(now, ZoneId.systemDefault())) {
            is AppResult.Success -> {
                r.value.forEach { notifier.show(it, now) }
                rescheduleNow()
            }
            // Storage trouble: try again later rather than ringing again immediately (a tight loop).
            is AppResult.Failure -> retryLater()
        }
    }

    /** "Snooze" on a notification. */
    suspend fun snooze(id: ReminderId, by: Duration = SNOOZE) {
        notifier.cancel(id)
        reminders.snooze(id, clock.now().plus(by))
        rescheduleNow()
    }

    private fun retryLater() {
        logger.warn(TAG, "Couldn't read reminders; retrying in ${RETRY.toMinutes()} min")
        arm(clock.now().plus(RETRY))
    }

    private fun arm(at: Instant?) {
        val am = alarms ?: return
        val ring = ringIntent(context)
        if (at == null) {
            am.cancel(ring)
            return
        }
        val time = at.toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, ring)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, ring)
            }
        } catch (e: SecurityException) { // permission revoked between the check and the call
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, ring)
        }
    }

    companion object {
        private const val TAG = "Reminders"
        val SNOOZE: Duration = Duration.ofMinutes(10)
        private val RETRY: Duration = Duration.ofMinutes(15)

        internal fun ringIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_RING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
