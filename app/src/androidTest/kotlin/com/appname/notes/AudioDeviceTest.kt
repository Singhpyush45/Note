package com.appname.notes

import android.Manifest
import android.app.Application
import android.media.MediaPlayer
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.Attachment
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.di.AppDataEntryPoint
import com.appname.notes.feature.audio.RecordingController
import com.appname.notes.feature.audio.RecordingState
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Audio notes on a real Android (FR-012): recording through the foreground service, and recovery after a kill. */
@RunWith(AndroidJUnit4::class)
class AudioDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val data = EntryPointAccessors.fromApplication(context.applicationContext as Application, AppDataEntryPoint::class.java)
    private val recordings = data.recordings()

    private fun newNote(): NoteId = runBlocking { (data.notes().createNote(null) as AppResult.Success).value }

    private fun attachments(id: NoteId): List<Attachment> = runBlocking { (data.attachments().list(id) as AppResult.Success).value }

    private fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    @Test
    fun aRecordingInterruptedByTheAppBeingKilledIsAttachedAtTheNextStart() = runBlocking {
        val note = newNote()
        // What a killed recording leaves behind: the audio so far and the sidecar naming its note.
        val id = "019a-interrupted-${System.nanoTime()}"
        val dir = File(context.filesDir, RecordingController.DIR).apply { mkdirs() }
        val audio = File(dir, "$id.aac").apply { writeBytes(ByteArray(16_000)) } // 2 s at 64 kbit/s
        File(dir, "$id.note").writeText("${note.value}\nRecording 2026-10-24 18.30.aac")

        assertThat(recordings.recoverInterrupted()).isAtLeast(1)

        val a = attachments(note).single()
        assertThat(a.id.value).isEqualTo(id) // the id its markers use
        assertThat(a.kind).isEqualTo(AttachmentKind.AUDIO)
        assertThat(a.displayName).isEqualTo("Recording 2026-10-24 18.30.aac")
        assertThat(a.durationMs).isEqualTo(2_000)
        assertThat(audio.exists()).isFalse()
        assertThat(File(dir, "$id.note").exists()).isFalse()
    }

    @Test
    fun recordingThroughTheServiceIsAttachedAndPlayable() {
        val automation = instrumentation.uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val note = newNote()
        // Recording is started from a visible screen, as from the editor (Android requires that for the microphone).
        val screen = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
            recordAndCheck(note)
        } finally {
            screen.close()
        }
    }

    private fun recordAndCheck(note: NoteId) {
        instrumentation.runOnMainSync { assertThat(recordings.start(note)).isTrue() }
        val started = waitFor { (recordings.state.value as? RecordingState.Recording)?.recording?.startedAtElapsed != null }
        assertWithMessage("recording started (error: ${recordings.errors.value}, state: ${recordings.state.value})").that(started).isTrue()
        Thread.sleep(2_000)
        instrumentation.runOnMainSync { recordings.stop() }

        assertWithMessage("recording attached").that(waitFor { attachments(note).isNotEmpty() }).isTrue()
        val a = attachments(note).single()
        assertThat(a.kind).isEqualTo(AttachmentKind.AUDIO)
        assertThat(a.mimeType).isEqualTo("audio/aac")
        assertThat(a.durationMs!!).isIn(1_500L..5_000L)
        assertThat(recordings.state.value).isEqualTo(RecordingState.Idle)
        // The stored file plays.
        val file = File(File(context.filesDir, "attachments"), a.fileName)
        val player = MediaPlayer()
        try {
            player.setDataSource(file.path)
            player.prepare()
            assertThat(player.duration).isGreaterThan(0)
        } finally {
            player.release()
        }
    }
}
