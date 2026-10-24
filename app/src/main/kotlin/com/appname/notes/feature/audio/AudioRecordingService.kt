package com.appname.notes.feature.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.appname.notes.MainActivity
import com.appname.notes.R
import com.appname.notes.core.common.logging.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import javax.inject.Inject

/**
 * Records from the microphone as a foreground service (FR-012): Android shows a "Recording" notification with
 * a Stop button, and recording continues when the user leaves the note or locks the screen.
 */
@AndroidEntryPoint
class AudioRecordingService : Service() {

    @Inject lateinit var controller: RecordingController

    @Inject lateinit var logger: AppLogger

    private var recorder: MediaRecorder? = null
    private var startedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY // a recording is never restarted by itself after the process died
    }

    private fun start() {
        val recording = controller.current()
        if (recording == null || recorder != null) {
            stopSelf()
            return
        }
        // Must be in the foreground within seconds of being started, before capturing.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        } catch (e: RuntimeException) { // e.g. permission revoked meanwhile, or started from the background
            logger.warn(TAG, "Couldn't start recording in the foreground", e)
            controller.onCaptureFailed()
            stopSelf()
            return
        }
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            // ADTS: a stream of self-contained frames, so a recording cut off by a crash is still playable.
            r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(RecordingController.SAMPLE_RATE)
            r.setAudioEncodingBitRate(RecordingController.BIT_RATE)
            r.setAudioChannels(1)
            r.setOutputFile(recording.file.path)
            r.prepare()
            r.start()
        } catch (e: IOException) {
            fail(r, e)
            return
        } catch (e: RuntimeException) { // microphone busy or unavailable
            fail(r, e)
            return
        }
        recorder = r
        startedAt = SystemClock.elapsedRealtime()
        controller.onCaptureStarted(startedAt)
        // Show the running time in the notification.
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(startedAtWallClock()))
    }

    private fun fail(r: MediaRecorder, e: Exception) {
        logger.warn(TAG, "Microphone unavailable", e)
        r.release()
        controller.onCaptureFailed()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stop() {
        val r = recorder
        var duration = 0L
        if (r != null) {
            duration = SystemClock.elapsedRealtime() - startedAt
            try {
                r.stop()
            } catch (e: RuntimeException) { // stopped right after starting: no audio was written
                duration = 0
            }
            r.release()
            recorder = null
        }
        controller.onCaptureFinished(duration)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Destroyed without Stop (e.g. the system ended the service): keep what was recorded.
        if (recorder != null) stop()
        super.onDestroy()
    }

    private fun startedAtWallClock() = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - startedAt)

    private fun notification(chronometerBase: Long? = null): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.audio_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.audio_channel_description)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(this, 1, stopIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(getString(R.string.audio_recording))
            .setContentText(controller.current()?.displayName.orEmpty())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.audio_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (chronometerBase != null) {
                    setUsesChronometer(true)
                    setWhen(chronometerBase)
                    setShowWhen(true)
                }
            }
            .build()
    }

    companion object {
        private const val TAG = "AudioRecordingService"
        private const val ACTION_START = "com.appname.notes.audio.START"
        private const val ACTION_STOP = "com.appname.notes.audio.STOP"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 42

        fun startIntent(context: Context) = Intent(context, AudioRecordingService::class.java).setAction(ACTION_START)
        fun stopIntent(context: Context) = Intent(context, AudioRecordingService::class.java).setAction(ACTION_STOP)
    }
}
