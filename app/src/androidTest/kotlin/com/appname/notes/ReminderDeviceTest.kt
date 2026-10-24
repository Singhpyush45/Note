package com.appname.notes

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.core.domain.model.ReminderRepeat
import com.appname.notes.di.AppDataEntryPoint
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A real reminder alarm ringing on Android (FR-010): set a reminder a few seconds ahead, let AlarmManager
 * deliver it to the app's receiver, and find the notification.
 */
@RunWith(AndroidJUnit4::class)
class ReminderDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val data = EntryPointAccessors.fromApplication(context.applicationContext as Application, AppDataEntryPoint::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return condition()
    }

    @Before
    fun allow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        // "Alarms & reminders" access, as a user would grant it in Settings.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
    }

    @After
    fun clear() = notifications.cancelAll()

    @Test
    fun aReminderRingsAndShowsItsNotification() = runBlocking {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertWithMessage("exact alarms allowed for the test").that(context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()).isTrue()
        }
        val id = (data.notes().createNote(null) as AppResult.Success).value
        data.notes().saveContent(id, "Call the lab", NoteDocument(blocks = listOf(TextBlock("b1", text = RichText("Ask about Friday's slot")))))
        val zone = ZoneId.systemDefault()
        val at = LocalDateTime.now(zone).plusSeconds(6)
        val reminder = (data.reminders().add(id, null, at, ReminderRepeat.NONE, zone) as AppResult.Success).value
        data.reminderScheduler().rescheduleNow()

        val posted = waitFor(40_000) { notifications.activeNotifications.any { it.tag == reminder.id.value } }
        assertWithMessage("reminder notification posted").that(posted).isTrue()
        val n = notifications.activeNotifications.first { it.tag == reminder.id.value }.notification
        assertThat(n.extras.getCharSequence("android.title")?.toString()).isEqualTo("Call the lab")
        assertThat(n.channelId).isEqualTo("reminders")
        // Gone off: no longer pending, nothing else to ring.
        assertThat(data.reminders().observe(id).first().single().isPending).isFalse()
    }
}
