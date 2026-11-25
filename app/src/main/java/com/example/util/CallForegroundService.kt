package com.example.util

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Keeps the microphone (and camera for video calls) usable while a call is connected and the app
 * goes to the background. Android only grants "while-in-use" mic/camera access to background apps
 * that run a foreground service of the matching type.
 */
class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val peer = intent?.getStringExtra(EXTRA_PEER).orEmpty()
        val video = intent?.getBooleanExtra(EXTRA_VIDEO, false) == true
        ZeroChatNotificationHelper.ensureChannels(this)

        val openApp = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ZeroChatNotificationHelper.CHANNEL_ONGOING_CALL)
            .setSmallIcon(R.drawable.ic_stat_zerochat)
            .setContentTitle(if (video) "Görüntülü arama sürüyor" else "Sesli arama sürüyor")
            .setContentText(if (peer.isNotBlank()) "@$peer" else "ZeroChat")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(openApp)
            .build()

        var types = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (granted(Manifest.permission.RECORD_AUDIO)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (video && granted(Manifest.permission.CAMERA)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
        } catch (e: Exception) {
            // e.g. missing permission at start time: the call still works while the app is visible.
            Log.w("CallService", "startForeground failed: ${e.message}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val NOTIFICATION_ID = 7_001
        private const val EXTRA_PEER = "peer"
        private const val EXTRA_VIDEO = "video"

        fun start(context: Context, peerUsername: String, isVideo: Boolean) {
            val intent = Intent(context, CallForegroundService::class.java)
                .putExtra(EXTRA_PEER, peerUsername)
                .putExtra(EXTRA_VIDEO, isVideo)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w("CallService", "start failed: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}
