package com.halo.app.automation

import com.halo.app.data.routine.RoomRoutineRepository
import com.halo.runtime.cognition.device.*
import com.halo.runtime.cognition.routine.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.Instant

class RoutinePresenceObserver(private val presence: DevicePresenceRepository,
    private val repository: RoomRoutineRepository, private val executor: RoutineExecutor) {
    private data class Transition(val device: String, val connected: Boolean, val at: Instant)
    suspend fun observe(): Unit = coroutineScope {
        val events = Channel<Transition>(128)
        launch {
            for (event in events) {
                repository.all().filter { it.enabled }.filter { r ->
                    when (val t = r.trigger) {
                        is RoutineTrigger.DeviceConnected -> event.connected && t.deviceId == event.device
                        is RoutineTrigger.DeviceDisconnected -> !event.connected && t.deviceId == event.device
                        else -> false
                    }
                }.forEach { executor.execute(it.id, event.at) }
            }
        }
        var previous: Map<String, DevicePresence>? = null
        try {
            presence.presenceMap.collect { current ->
                val prior = previous
                previous = current.toMap()
                if (prior != null) current.forEach { (id, now) ->
                    // Initial discovery is a baseline, never a synthetic connection event.
                    val before = prior[id] ?: return@forEach
                    if (before.state.connected() != now.state.connected()) {
                        events.send(Transition(id, now.state.connected(), now.updatedAt))
                    }
                }
            }
        } finally { events.close() }
    }
    private fun DevicePresenceState.connected() = this == DevicePresenceState.CONNECTED || this == DevicePresenceState.READY
}
