package com.halo.app.attention

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.halo.runtime.cognition.attention.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant

/** Android adapters share the existing event/TTS path; a request event is not speech completion. */
class AndroidAttentionHost(
    private val context: Context,
    private val eventBus: EventBus,
    scope: CoroutineScope,
) : AttentionContextProvider, AttentionSink {
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val revisions = MutableStateFlow(0L)
    override val changes: Flow<Unit> = revisions.map { Unit }
    @Volatile private var foreground = false
    @Volatile private var interacting = false
    @Volatile private var cognitiveActive = false
    @Volatile private var speechActive = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh() }
    }
    init {
        scope.launch {
            eventBus.events().collect { event ->
                when (event) {
                    is CognitiveRequestStarted -> cognitiveActive = true
                    is CognitiveCompleted, is CognitiveFailed -> cognitiveActive = false
                    is TtsStarted -> speechActive = true
                    is TtsCompleted, is TtsFailed, is TtsCancelled -> speechActive = false
                    else -> return@collect
                }
                refresh()
            }
        }
        val filter = IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED).apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
    }
    fun foreground(value: Boolean) { foreground = value; refresh() }
    fun interacting(value: Boolean) { interacting = value; refresh() }
    fun refresh() { revisions.update { it + 1 } }
    override fun snapshot() = AttentionContext(
        foregroundState = if (foreground) ForegroundState.HALO_FOREGROUND else ForegroundState.OTHER_APP_FOREGROUND,
        screenInteractive = context.getSystemService(android.os.PowerManager::class.java).isInteractive,
        activeInteraction = interacting || cognitiveActive,
        activeSpeechSession = speechActive,
        doNotDisturb = if (notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL)
            DoNotDisturbState.OFF else DoNotDisturbState.ACTIVE,
        currentTime = Instant.now(),
    )
    override fun supports(channel: DeliveryChannel) = channel == DeliveryChannel.InApp ||
        channel == DeliveryChannel.AndroidNotification || channel == DeliveryChannel.PhoneSpeech || channel is DeliveryChannel.WearableSpeech

    override suspend fun deliver(request: AttentionRequest, channel: DeliveryChannel): AttentionSinkResult {
        val text = request.text()
        return when (channel) {
            DeliveryChannel.InApp -> {
                if (!foreground) return AttentionSinkResult(false, "Halo is not in foreground")
                surface(request)
                AttentionSinkResult(true)
            }
            DeliveryChannel.AndroidNotification -> {
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    return AttentionSinkResult(false, "POST_NOTIFICATIONS permission required")
                if (!notifications.areNotificationsEnabled()) return AttentionSinkResult(false, "Notifications disabled")
                notifications.createNotificationChannel(NotificationChannel("halo_attention", "Halo updates", NotificationManager.IMPORTANCE_DEFAULT))
                if (notifications.getNotificationChannel("halo_attention").importance == NotificationManager.IMPORTANCE_NONE)
                    return AttentionSinkResult(false, "Notification channel disabled")
                val notification = android.app.Notification.Builder(context, "halo_attention")
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Halo")
                    .setContentText(text).setStyle(android.app.Notification.BigTextStyle().bigText(text)).setAutoCancel(true).build()
                notifications.notify(request.id.value, 0, notification)
                AttentionSinkResult(true, "Accepted by Android NotificationManager")
            }
            DeliveryChannel.PhoneSpeech, is DeliveryChannel.WearableSpeech -> coroutineScope {
                com.halo.app.di.ManualDependencyGraph.startAndroidSpeaker()
                val transaction = "attention:${request.id.value}"
                val target = if (channel is DeliveryChannel.WearableSpeech) SpeechOutputTarget.Device(channel.deviceId, null, "Wearable", AudioDeviceKind.WEARABLE) else SpeechOutputTarget.Phone
                val result = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(60_000) {
                        eventBus.events().first { event ->
                            (event is TtsCompleted && event.transactionId == transaction && event.target == target) ||
                                (event is TtsFailed && event.transactionId == transaction) ||
                                (event is TtsCancelled && event.transactionId == transaction)
                        }
                    }
                }
                eventBus.publish(TtsRequested(text = text, transactionId = transaction, target = target,
                    fallback = SpeechOutputFallback.DISALLOW_PHONE))
                when (val event = result.await()) {
                    is TtsCompleted -> AttentionSinkResult(true)
                    is TtsFailed -> AttentionSinkResult(false, event.reason)
                    is TtsCancelled -> AttentionSinkResult(false, "Speech cancelled")
                    else -> AttentionSinkResult(false, "No speech completion evidence")
                }
            }
            else -> AttentionSinkResult(false, "Unsupported channel")
        }
    }
    suspend fun surfaceSilently(request: AttentionRequest, surface: SilentSurface): Boolean {
        if (surface != SilentSurface.ACTIVITY) return false
        this.surface(request)
        return true
    }
    private suspend fun surface(request: AttentionRequest) {
        eventBus.publish(AssistantResponsePublished(text = request.text(), delivery = ResponseDelivery.VISUAL_ONLY,
            transactionId = request.interactionId?.value ?: "attention:${request.id.value}"))
    }
    private fun AttentionRequest.text(): String = when (val intent = deliveryIntent) {
        is DeliveryIntent.Inform -> "${intent.content.title}: ${intent.content.body}"
        is DeliveryIntent.RequestApproval -> intent.summary
        is DeliveryIntent.RequestClarification -> intent.question
        is DeliveryIntent.ActionRequired -> intent.summary
    }
}
