package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

data class RecordedVoiceClip(
    val durationSeconds: Int,
    val waveformString: String,
    val audioBase64: String
)

class VoiceAudioManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlaybackFile: File? = null
    private var currentOutputFile: File? = null
    private var startTimestampMillis: Long = 0L
    private val amplitudeSamples = mutableListOf<Int>()

    val isRecording: Boolean
        get() = mediaRecorder != null

    fun startRecording(): Boolean {
        stopRecordingCleanup()
        val outFile = File(context.cacheDir, "zc_voice_${System.currentTimeMillis()}.m4a")
        currentOutputFile = outFile

        // Try standard 16 kHz / 24 kbps AAC first, then fallback to default encoder rate if device hardware rejects custom rate
        if (tryStartRecorder(outFile, customSamplingRate = true)) {
            return true
        }
        return tryStartRecorder(outFile, customSamplingRate = false)
    }

    private fun tryStartRecorder(outFile: File, customSamplingRate: Boolean): Boolean {
        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            if (customSamplingRate) {
                recorder.setAudioEncodingBitRate(24000)
                recorder.setAudioSamplingRate(16000)
            }
            recorder.setOutputFile(outFile.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            startTimestampMillis = System.currentTimeMillis()
            amplitudeSamples.clear()
            true
        } catch (e: Exception) {
            Log.w("VoiceAudioManager", "Mic start attempt (custom=$customSamplingRate) failed: ${e.message}")
            stopRecordingCleanup()
            false
        }
    }

    fun sampleAmplitude() {
        try {
            val amp = mediaRecorder?.maxAmplitude ?: 0
            amplitudeSamples.add(amp.coerceAtLeast(0))
        } catch (_: Exception) {
            amplitudeSamples.add(0)
        }
    }

    fun stopRecording(): RecordedVoiceClip {
        val elapsedSec = (((System.currentTimeMillis() - startTimestampMillis) / 1000L).toInt()).coerceIn(1, 120)
        var base64Audio = ""
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (stopErr: Exception) {
                    Log.w("VoiceAudioManager", "Recorder stop warning: ${stopErr.message}")
                }
                release()
            }
            mediaRecorder = null
            val file = currentOutputFile
            if (file != null && file.exists() && file.length() in 32..MAX_ATTACHMENT_BYTES) {
                base64Audio = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            }
            file?.delete()
        } catch (e: Exception) {
            Log.w("VoiceAudioManager", "Stop recording error: ${e.message}")
        } finally {
            stopRecordingCleanup()
        }

        val bars = listOf('▂', '▃', '▄', '▅', '▆', '▇', '█')
        val waveform = if (amplitudeSamples.isNotEmpty()) {
            amplitudeSamples.take(16).map { amp ->
                val idx = ((amp.coerceIn(0, 25000) / 25000f) * (bars.size - 1)).toInt()
                bars[idx]
            }.joinToString("")
        } else {
            "▃▅▆▄▇▅▃▆▇▄▅▃▆▄▅▃"
        }

        return RecordedVoiceClip(
            durationSeconds = elapsedSec,
            waveformString = waveform,
            audioBase64 = base64Audio
        )
    }

    /**
     * Records a short live audio slice during an active P2P voice/video call and returns Base64 AAC data.
     */
    suspend fun recordShortCallAudioChunk(durationMs: Long = 1400L): String = withContext(Dispatchers.IO) {
        val chunkFile = File(context.cacheDir, "zc_call_chunk_${System.currentTimeMillis()}.m4a")
        var recorder: MediaRecorder? = null
        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(16000)
            recorder.setAudioSamplingRate(16000)
            recorder.setOutputFile(chunkFile.absolutePath)
            recorder.prepare()
            recorder.start()
            delay(durationMs)
            try {
                recorder.stop()
            } catch (_: Exception) {
            }
            recorder.release()
            recorder = null
            if (chunkFile.exists() && chunkFile.length() in 32..60000) {
                return@withContext Base64.encodeToString(chunkFile.readBytes(), Base64.NO_WRAP)
            }
        } catch (_: Exception) {
        } finally {
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
            chunkFile.delete()
        }
        ""
    }

    /** Plays one clip and suspends until it finishes; cancelling the coroutine stops playback. */
    suspend fun playBase64AudioAndWait(audioBase64: String) {
        if (audioBase64.isBlank()) return
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val started = playBase64Audio(audioBase64) { if (cont.isActive) cont.resume(Unit) }
            if (!started && cont.isActive) cont.resume(Unit)
            cont.invokeOnCancellation { stopPlayback() }
        }
    }

    /** Puts audio routing back to normal after a call. */
    fun restoreNormalAudioMode() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }

    /** Stops an in-progress voice note recording and deletes its temporary file. */
    fun cancelRecording() {
        stopRecordingCleanup()
        currentOutputFile?.delete()
        currentOutputFile = null
    }

    fun setSpeakerphoneEnabled(enabled: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        } catch (_: Exception) {
        }
    }

    fun playBase64Audio(
        audioBase64: String,
        playbackSpeed: Float = 1.0f,
        onCompletion: () -> Unit
    ): Boolean {
        stopPlayback()
        if (audioBase64.isBlank()) {
            return false
        }
        return try {
            val bytes = Base64.decode(audioBase64, Base64.NO_WRAP)
            val tempFile = File(context.cacheDir, "zc_play_${System.nanoTime()}.m4a")
            FileOutputStream(tempFile).use { it.write(bytes) }
            currentPlaybackFile = tempFile
            val player = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    stopPlayback()
                    onCompletion()
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    onCompletion()
                    true
                }
                prepare()
                try {
                    playbackParams = PlaybackParams().setSpeed(playbackSpeed.coerceIn(0.5f, 2.5f))
                } catch (_: Exception) {
                }
                start()
            }
            mediaPlayer = player
            true
        } catch (e: Exception) {
            Log.w("VoiceAudioManager", "Playback fallback: ${e.message}")
            stopPlayback()
            false
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        // Decrypted audio never stays in the cache directory.
        currentPlaybackFile?.delete()
        currentPlaybackFile = null
    }

    private fun stopRecordingCleanup() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
    }
}

object ZeroChatNotificationHelper {
    private const val CHANNEL_MESSAGES = "zerochat_messages_channel"
    private const val CHANNEL_CALLS = "zerochat_calls_channel"
    const val CHANNEL_ONGOING_CALL = "zerochat_ongoing_call_channel"

    fun cancel(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val msgChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Yeni Mesajlar ve İstekler",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Şifreli sohbet mesajları ve bağlantı istekleri"
                enableVibration(true)
            }
            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Sesli ve Görüntülü Aramalar",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gelen sesli ve görüntülü aramalar"
                enableVibration(true)
            }
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_CALL,
                "Devam Eden Arama",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Arama sürerken mikrofon ve kameranın çalışmaya devam etmesi için" }
            nm.createNotificationChannel(msgChannel)
            nm.createNotificationChannel(callChannel)
            nm.createNotificationChannel(ongoingChannel)
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showMessageNotification(
        context: Context,
        notificationId: Int,
        senderTitle: String,
        messagePreview: String
    ) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_stat_zerochat)
                .setContentTitle(senderTitle)
                .setContentText(messagePreview)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                        .setSmallIcon(R.drawable.ic_stat_zerochat)
                        .setContentTitle("ZeroChat")
                        .setContentText("Yeni mesaj")
                        .build()
                )
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w("ZeroChatNotif", "Notification permission missing: ${e.message}")
        }
    }

    fun showIncomingCallNotification(
        context: Context,
        notificationId: Int,
        callerUsername: String,
        isVideo: Boolean
    ) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val callLabel = if (isVideo) "Gelen Görüntülü Arama" else "Gelen Sesli Arama"
            val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
                .setSmallIcon(R.drawable.ic_stat_zerochat)
                .setContentTitle("@$callerUsername • $callLabel")
                .setContentText("Yanıtlamak için dokunun")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w("ZeroChatNotif", "Call notification permission missing: ${e.message}")
        }
    }
}
