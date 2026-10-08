package com.halo.app.continuity

import com.halo.sdk.runtime.ActionReceipt
import com.halo.sdk.runtime.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Metadata only. The existing capture store remains the sole artifact owner. */
data class RecentCapture(val objectId: String, val sourceLabel: String, val mediaKind: String, val timestamp: Long)
class RecentCaptureProjection(events: EventBus, scope: CoroutineScope, private val now: () -> Long = System::currentTimeMillis) {
    private val _recent = MutableStateFlow<RecentCapture?>(null)
    val recent = _recent.asStateFlow()
    init {
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { events.events().collect { event ->
            if (event is ActionReceipt && event.category == "Vision" && event.title == "Image received" && event.stage == "Completed" && event.objectId != null) {
                _recent.value = RecentCapture(event.objectId!!, if (event.sourceDeviceId != null) "Wearable" else "Phone camera", "Photo", event.timestamp)
            }
        } }
        scope.launch { while (true) { delay(5_000); if (_recent.value?.let { now() - it.timestamp >= 300_000 } == true) _recent.value = null } }
    }
    fun dismiss() { _recent.value = null }
}
