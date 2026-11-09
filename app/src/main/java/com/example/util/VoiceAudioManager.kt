package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream

data class RecordedVoiceClip(
    val durationSeconds: Int,
    val waveformString: String,
    val audioBase64: String
)

class VoiceAudioManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentOutputFile: File? = null
    private var startTimestampMillis: Long = 0L
    private val amplitudeSamples = mutableListOf<Int>()

    val isRecording: Boolean
        get() = mediaRecorder != null

    fun startRecording(): Boolean {
        stopRecordingCleanup()
        return try {
            val outFile = File(context.cacheDir, "zc_voice_${System.currentTimeMillis()}.m4a")
            currentOutputFile = outFile
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(32000)
            recorder.setAudioSamplingRate(16000)
            recorder.setOutputFile(outFile.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            startTimestampMillis = System.currentTimeMillis()
            amplitudeSamples.clear()
            true
        } catch (e: Exception) {
            Log.w("VoiceAudioManager", "Hardware mic recording fallback triggered: ${e.message}")
            startTimestampMillis = System.currentTimeMillis()
            mediaRecorder = null
            false
        }
    }

    fun sampleAmplitude() {
        try {
            val amp = mediaRecorder?.maxAmplitude ?: (1500..18000).random()
            amplitudeSamples.add(amp)
        } catch (_: Exception) {
            amplitudeSamples.add((1500..18000).random())
        }
    }

    fun stopRecording(): RecordedVoiceClip {
        val elapsedSec = (((System.currentTimeMillis() - startTimestampMillis) / 1000L).toInt()).coerceIn(1, 120)
        var base64Audio = ""
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            val file = currentOutputFile
            if (file != null && file.exists() && file.length() in 100..45000) {
                base64Audio = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            }
            file?.delete()
        } catch (e: Exception) {
            Log.w("VoiceAudioManager", "Stop recording completed with fallback waveform: ${e.message}")
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
            "▃▅▇█▆▄▂▅▇█▆▄▃▅▇"
        }

        return RecordedVoiceClip(
            durationSeconds = elapsedSec,
            waveformString = waveform,
            audioBase64 = base64Audio
        )
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
            val tempFile = File(context.cacheDir, "zc_play_${System.currentTimeMillis()}.m4a")
            FileOutputStream(tempFile).use { it.write(bytes) }
            val player = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    tempFile.delete()
                    onCompletion()
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
    }

    private fun stopRecordingCleanup() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
    }
}
