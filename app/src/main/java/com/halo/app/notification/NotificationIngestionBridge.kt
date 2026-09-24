package com.halo.app.notification

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.halo.app.data.notification.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.time.Instant

sealed interface NotificationListenerState {
    data object PermissionMissing : NotificationListenerState
    data object Connecting : NotificationListenerState
    data object Connected : NotificationListenerState
    data object Disconnected : NotificationListenerState
}
interface NotificationAccessController {
    val state: StateFlow<NotificationListenerState>
    fun openNotificationAccessSettings()
    fun isAccessGranted(): Boolean
}
interface NotificationBridgeOwner { val notificationBridge: NotificationIngestionBridge }
class NotificationIngestionBridge(private val context: Context,
    private val repository: RoomNotificationRepository, scope: CoroutineScope) : NotificationAccessController {
    private val mutableState = MutableStateFlow<NotificationListenerState>(NotificationListenerState.PermissionMissing)
    override val state = mutableState.asStateFlow()
    private val events = Channel<suspend () -> Unit>(capacity = 256)
    init {
        scope.launch {
            refresh()
            repository.prune(Instant.now().minus(RoomNotificationRepository.RETENTION))
            for (event in events) try { event() } catch (e: CancellationException) { throw e }
                catch (e: Exception) { android.util.Log.w("NotificationBridge", "Ingestion failed", e) }
        }
    }
    private fun enqueue(event: suspend () -> Unit) {
        if (!events.trySend(event).isSuccess) android.util.Log.w("NotificationBridge", "Ingestion queue full; event dropped")
    }
    fun posted(item: IncomingNotification) = enqueue { repository.ingest(item) }
    fun removed(key: String) = enqueue { repository.markRemoved(key, Instant.now()) }
    fun connected(items: List<IncomingNotification>) {
        mutableState.value = NotificationListenerState.Connected
        enqueue {
            repository.reconcileActive(items.map { it.key })
            items.forEach { repository.ingest(it) }
        }
    }
    fun disconnected() { mutableState.value = NotificationListenerState.Disconnected; refresh() }
    fun refresh() {
        if (!isAccessGranted()) mutableState.value = NotificationListenerState.PermissionMissing
        else if (mutableState.value == NotificationListenerState.PermissionMissing) mutableState.value = NotificationListenerState.Connecting
    }
    override fun isAccessGranted(): Boolean {
        val component = ComponentName(context, HaloNotificationListenerService::class.java)
        return if (android.os.Build.VERSION.SDK_INT >= 27) {
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        } else {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
                .split(':').any { ComponentName.unflattenFromString(it) == component }
        }
    }
    override fun openNotificationAccessSettings() {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
