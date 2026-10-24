package com.appname.notes.feature.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.ReminderRepeat
import com.appname.notes.di.AppDataEntryPoint
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The alarm and notification side of reminders (FR-010) on the JVM, with the real app and database:
 * the one system alarm follows the soonest reminder, ringing notifies what is due, Snooze, denied
 * notifications, and re-arming after reboot. The emulator test (ReminderDeviceTest) lets a real alarm ring.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ReminderSchedulerRobolectricTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val data = EntryPointAccessors.fromApplication(app, AppDataEntryPoint::class.java)
    private val scheduler = data.reminderScheduler()
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val zone = ZoneId.systemDefault()

    @Before
    fun grant() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun <T> AppResult<T>.ok(): T = (this as AppResult.Success).value

    private fun note(title: String, vararg tasks: String): NoteId = runBlocking {
        val id = data.notes().createNote(null).ok()
        val blocks = if (tasks.isEmpty()) {
            listOf(TextBlock("b1", text = RichText("Bring the lab manual")))
        } else {
            tasks.mapIndexed { i, t -> TextBlock("t$i", BlockKind.CHECKLIST, RichText(t)) }
        }
        data.notes().saveContent(id, title, NoteDocument(blocks = blocks)).ok()
        id
    }

    private fun localIn(d: Duration): LocalDateTime = LocalDateTime.now(zone).plus(d).withNano(0)

    private fun nextAlarm() = Shadows.shadowOf(alarms).peekNextScheduledAlarm()

    /**
     * Waits for the alarm to be at [expected] (null: none). The app also re-arms from a database observer
     * in the background, so the last write may land a moment after rescheduleNow().
     */
    private fun awaitAlarm(expected: Long?) {
        val deadline = System.currentTimeMillis() + 10_000
        while (nextAlarm()?.triggerAtMs != expected && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertThat(nextAlarm()?.triggerAtMs).isEqualTo(expected)
    }

    private fun android.app.Notification.title() = extras.getCharSequence("android.title")?.toString()

    private fun shown() = Shadows.shadowOf(notifications).allNotifications

    @Test
    fun `the alarm follows the soonest reminder and is cancelled when none is left`() = runBlocking {
        val id = note("Physics")
        val later = data.reminders().add(id, null, localIn(Duration.ofHours(5)), ReminderRepeat.NONE, zone).ok()
        val sooner = data.reminders().add(id, null, localIn(Duration.ofHours(2)), ReminderRepeat.NONE, zone).ok()
        scheduler.rescheduleNow()
        awaitAlarm(sooner.triggerAt.toEpochMilli())
        assertThat(nextAlarm()!!.type).isEqualTo(AlarmManager.RTC_WAKEUP)

        data.reminders().remove(sooner.id).ok()
        scheduler.rescheduleNow()
        awaitAlarm(later.triggerAt.toEpochMilli())

        data.reminders().remove(later.id).ok()
        scheduler.rescheduleNow()
        awaitAlarm(null)
    }

    @Test
    fun `without exact-alarm access the alarm is still set, inexactly`() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertThat(scheduler.canScheduleExact).isFalse()
        val r = data.reminders().add(note("Physics"), null, localIn(Duration.ofHours(1)), ReminderRepeat.NONE, zone).ok()
        scheduler.rescheduleNow()
        awaitAlarm(r.triggerAt.toEpochMilli())
    }

    @Test
    fun `ringing shows the task and its note, marks missed ones, and Snooze moves it on`() = runBlocking {
        val id = note("Lab", "Submit the report")
        // Due a minute ago (on time) and three hours ago (missed: the phone was off).
        val task = data.reminders().add(id, "t0", localIn(Duration.ofMinutes(-1)), ReminderRepeat.NONE, zone).ok()
        data.reminders().add(note("Physics"), null, localIn(Duration.ofHours(-3)), ReminderRepeat.NONE, zone).ok()

        scheduler.deliverDue()
        val posted = shown()
        assertThat(posted).hasSize(2)
        val taskNotification = posted.single { it.title() == "Submit the report" }
        assertThat(taskNotification.extras.getCharSequence("android.text").toString()).contains("Lab")
        assertThat(taskNotification.extras.getCharSequence("android.subText")).isNull()
        assertThat(taskNotification.actions.map { it.title.toString() }).containsExactly("Snooze 10 min")
        assertThat(taskNotification.contentIntent).isNotNull()
        val missed = posted.single { it.title() == "Physics" }
        assertThat(missed.extras.getCharSequence("android.subText").toString()).startsWith("Missed")
        // Nothing left to ring.
        awaitAlarm(null)

        scheduler.snooze(task.id)
        assertThat(shown().map { it.title() }).containsExactly("Physics")
        val snoozedTo = data.reminders().nextDue().ok()!!
        assertThat(Duration.between(Instant.now(), snoozedTo).toMinutes()).isIn(9L..10L)
        awaitAlarm(snoozedTo.toEpochMilli())
    }

    @Test
    fun `with notifications refused, reminders still go off quietly and the app says alerts are off`() = runBlocking {
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(data.reminderNotifier().canAlert).isFalse()
        val id = note("Physics")
        data.reminders().add(id, null, localIn(Duration.ofMinutes(-1)), ReminderRepeat.NONE, zone).ok()
        scheduler.deliverDue()
        assertThat(shown()).isEmpty()
        assertThat(data.reminders().nextDue().ok()).isNull() // taken, not retried forever
    }

    @Test
    fun `a reboot re-arms the alarm`() {
        val r = runBlocking { data.reminders().add(note("Physics"), null, localIn(Duration.ofHours(3)), ReminderRepeat.DAILY, zone).ok() }
        awaitAlarm(r.triggerAt.toEpochMilli()) // set by the app's observer
        alarms.cancel(ReminderScheduler.ringIntent(app)) // what a reboot does
        assertThat(nextAlarm()).isNull()
        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(app.packageName))
        awaitAlarm(r.triggerAt.toEpochMilli())
    }
}
