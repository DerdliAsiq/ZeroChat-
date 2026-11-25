package com.example.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sends scheduled messages that are due, even if the app process was killed in the meantime.
 * Each scheduled message carries a pre-generated envelope id, so a send that races with the
 * in-app dispatcher is delivered only once.
 */
class ScheduledMessageWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val dao = ZeroChatServices.vaultDao(applicationContext)
        val repository = ZeroChatServices.repository(applicationContext)
        val due = dao.getDueScheduledEnvelopes(uid, System.currentTimeMillis())
        if (due.isNotEmpty()) {
            val rooms = runCatching { repository.observeUserRooms().first() }.getOrElse { return Result.retry() }
            for (item in due) {
                val room = rooms.firstOrNull { it.id == item.roomId }
                val payload = runCatching { JSONObject(item.payloadJson) }.getOrNull()
                if (room == null || payload == null) {
                    dao.deleteScheduledEnvelope(item.id)
                    continue
                }
                repository.sendMessage(room, payload, item.ttlSeconds, item.envelopeId)
                    .onSuccess { dao.deleteScheduledEnvelope(item.id) }
                    .onFailure { return Result.retry() }
            }
        }
        dao.nextScheduledAt(uid)?.let { schedule(applicationContext, it) }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "zerochat_scheduled_messages"

        /** (Re)schedules the worker for [atMillis]; the earliest pending message wins. */
        fun schedule(context: Context, atMillis: Long) {
            val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
