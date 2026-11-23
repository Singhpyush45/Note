package com.appname.notes.feature.voice

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import com.appname.notes.MainActivity
import com.appname.notes.R
import kotlin.math.abs
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Listens and transcribes in the foreground (a "Transcribing" notification with Stop), so it keeps working while the
 * user switches to another app — e.g. the video being transcribed. Audio is read in 0.1 s pieces and fed to Vosk on
 * a background thread; nothing is recorded to a file or sent anywhere.
 */
class TranscribeService : Service() {

    @Volatile private var running = false
    private var worker: Thread? = null
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_STOP -> finish()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        if (running) return
        val pack = VoicePack.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_PACK) }
        val source = VoiceSource.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_SOURCE) } ?: VoiceSource.MICROPHONE
        if (pack == null) return finish()
        val phone = source == VoiceSource.PHONE_AUDIO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val type = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> if (phone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
            phone -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        } catch (e: RuntimeException) {
            LiveTranscriber.fail(LiveTranscriber.Problem.AUDIO)
            return finish()
        }
        if (phone) {
            val code = intent.getIntExtra(EXTRA_CODE, 0)
            val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val p = runCatching { if (data != null) mpm?.getMediaProjection(code, data) else null }.getOrNull()
            if (p == null) {
                LiveTranscriber.fail(LiveTranscriber.Problem.AUDIO)
                return finish()
            }
            p.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (running) {
                            LiveTranscriber.warn(LiveTranscriber.Problem.STOPPED)
                            finish()
                        }
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            projection = p
        }
        running = true
        val dir = VoicePacks.dir(this, pack).path
        worker = Thread({ listen(dir, phone) }, "transcribe").apply { start() }
    }

    @SuppressLint("MissingPermission") // asked for before starting; a refusal ends in the catch below
    private fun openAudio(phone: Boolean): AudioRecord? {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(CHUNK * 2)
        return try {
            val record = if (phone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val config = AudioPlaybackCaptureConfiguration.Builder(projection!!)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(min * 4).setAudioPlaybackCaptureConfig(config).build()
            } else {
                // VOICE_RECOGNITION: tuned for speech recognition (no call-style echo cancelling that drops speech).
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4)
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                null
            } else {
                record
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun listen(modelDir: String, phone: Boolean) {
        val model = try {
            loadModel(modelDir)
        } catch (e: Throwable) {
            LiveTranscriber.fail(LiveTranscriber.Problem.MODEL)
            main { finish() }
            return
        }
        val record = openAudio(phone)
        if (record == null) {
            LiveTranscriber.fail(LiveTranscriber.Problem.AUDIO)
            main { finish() }
            return
        }
        val recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
        recognizer.setWords(true) // word confidences, to drop noise heard as words
        val buf = ShortArray(CHUNK)
        var lastPartial = ""
        var silentChunks = 0
        try {
            record.startRecording()
            LiveTranscriber.listening()
            while (running) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) continue
                var peak = 0
                for (i in 0 until n) peak = maxOf(peak, abs(buf[i].toInt()))
                LiveTranscriber.level((peak / 32768f).coerceIn(0f, 1f))
                // Phone audio that stays completely silent: the playing app doesn't share its sound.
                if (phone) {
                    silentChunks = if (peak == 0) silentChunks + 1 else 0
                    LiveTranscriber.warn(if (silentChunks > SILENT_WARN_CHUNKS) LiveTranscriber.Problem.SILENT_PHONE_AUDIO else null)
                }
                if (recognizer.acceptWaveForm(buf, n)) {
                    LiveTranscriber.emit(LiveTranscriber.Event.Final(LiveTranscriber.confidentText(recognizer.result)))
                    lastPartial = ""
                } else {
                    val partial = LiveTranscriber.field(recognizer.partialResult, "partial")
                    if (partial != lastPartial) {
                        lastPartial = partial
                        LiveTranscriber.emit(LiveTranscriber.Event.Partial(partial))
                    }
                }
            }
            LiveTranscriber.emit(LiveTranscriber.Event.Final(LiveTranscriber.confidentText(recognizer.finalResult), last = true))
        } catch (e: Exception) {
            LiveTranscriber.fail(LiveTranscriber.Problem.AUDIO)
        } finally {
            runCatching { record.stop() }
            record.release()
            recognizer.close()
            LiveTranscriber.stopped()
        }
    }

    private fun finish() {
        running = false
        worker?.join(2_000)
        worker = null
        projection?.stop()
        projection = null
        LiveTranscriber.stopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) finish()
        super.onDestroy()
    }

    private fun main(block: () -> Unit) = Handler(Looper.getMainLooper()).post(block)

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.transcribe_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TranscribeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(getString(R.string.transcribe_notification))
            .setContentText(getString(R.string.transcribe_notification_text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.voice_stop), stop)
            .build()
    }

    companion object {
        const val ACTION_START = "com.appname.notes.transcribe.START"
        const val ACTION_STOP = "com.appname.notes.transcribe.STOP"
        const val EXTRA_PACK = "pack"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "transcription"
        private const val NOTIFICATION_ID = 4711
        private const val SAMPLE_RATE = 16_000
        private const val CHUNK = 1_600 // 0.1 s
        private const val SILENT_WARN_CHUNKS = 120 // 12 s of exact silence

        /** The loaded model stays in memory between sessions (loading takes a second or two). */
        private var model: Pair<String, Model>? = null

        @Synchronized
        private fun loadModel(dir: String): Model {
            model?.let { if (it.first == dir) return it.second }
            model?.second?.close()
            model = null
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            return Model(dir).also { model = dir to it }
        }
    }
}
