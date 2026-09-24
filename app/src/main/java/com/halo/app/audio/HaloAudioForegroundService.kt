package com.halo.app.audio

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import java.util.UUID

/** Session-scoped foreground ownership. No capture or playback is restarted after process death. */
class HaloAudioForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); instance = this }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        updateForeground()
        return START_NOT_STICKY
    }

    private fun updateForeground() {
        if (requests.isEmpty()) {
            if (instance === this) instance = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Halo audio", NotificationManager.IMPORTANCE_LOW))
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Halo audio active")
                .setContentText(if (requests.values.any { it.kind != Kind.PLAYBACK }) "Listening for your voice" else "Speaking a response")
                .setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setOnlyAlertOnce(true)
                .apply { if (launch != null) setContentIntent(PendingIntent.getActivity(this@HaloAudioForegroundService,
                    0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) }
                .build()
            if (Build.VERSION.SDK_INT >= 29) {
                val types = requests.values.fold(0) { mask, request -> mask or when (request.kind) {
                    Kind.PHONE_INPUT -> if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
                    Kind.WEARABLE_INPUT -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    Kind.PLAYBACK -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } }
                startForeground(NOTIFICATION, notification, types)
            } else startForeground(NOTIFICATION, notification)
            requests.values.forEach { it.ready.complete(Unit) }
        } catch (error: Exception) {
            val denied = requests.filterValues { !it.ready.isCompleted }.keys.toList()
            denied.forEach { id -> requests.remove(id)?.ready?.completeExceptionally(error) }
            if (requests.isEmpty()) {
                if (instance === this) instance = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            requests.values.forEach { it.ready.completeExceptionally(IllegalStateException("Audio service stopped")) }
            requests.clear()
        }
        super.onDestroy()
    }

    enum class Kind { PHONE_INPUT, WEARABLE_INPUT, PLAYBACK }
    private data class Request(val kind: Kind, val ready: CompletableDeferred<Unit>)
    class Lease internal constructor(private val id: String) {
        suspend fun release() = withContext(NonCancellable + Dispatchers.Main.immediate) {
            if (requests.remove(id) != null) instance?.updateForeground()
        }
    }

    companion object {
        private const val CHANNEL = "halo_audio"
        private const val NOTIFICATION = 24018
        // These fields are accessed only on the Android main dispatcher.
        private val requests = linkedMapOf<String, Request>()
        private var instance: HaloAudioForegroundService? = null
        suspend fun acquire(context: Context, kind: Kind): Lease {
            val id = UUID.randomUUID().toString()
            try {
                return withContext(Dispatchers.Main.immediate) {
                    val request = Request(kind, CompletableDeferred())
                    requests[id] = request
                    // Android enforces background-start and microphone while-in-use restrictions.
                    context.startForegroundService(Intent(context, HaloAudioForegroundService::class.java))
                    withTimeout(5_000L) { request.ready.await() }
                    Lease(id)
                }
            } catch (error: Exception) {
                // Also covers cancellation while dispatching the acquired lease back to its caller.
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    requests.remove(id)
                    instance?.updateForeground()
                }
                throw error
            }
        }
    }
}
