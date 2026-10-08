package com.halo.app.daily

import com.halo.app.data.routine.RoomRoutineRepository
import com.halo.app.data.study.*
import com.halo.app.study.StudyUseCases
import com.halo.app.ui.screens.notifications.*
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.attention.*
import com.halo.runtime.cognition.device.*
import com.halo.runtime.cognition.routine.*
import com.halo.sdk.runtime.InteractionAudioAffinity
import com.halo.sdk.runtime.embodiment.DeviceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.time.Instant

/** Read-only aggregation. The dependency surface deliberately contains no execution or write APIs. */
class DailyAssistantController(
    attention: Flow<List<QueuedAttentionRequest>>, runs: Flow<List<AgentRun>>,
    inbox: Flow<NotificationInboxState>, due: Flow<List<StudyPracticeItem>>,
    lessons: Flow<List<StudySessionRecord>>, routines: Flow<List<Routine>>,
    routineRuns: Flow<List<RoutineRunSummary>>, learning: Flow<Int>,
    presence: Flow<Map<String, DevicePresence>>, affinity: Flow<InteractionAudioAffinity?>,
    private val registry: DeviceRegistry, private val allowLearning: suspend () -> Boolean,
    scope: CoroutineScope, private val now: () -> Instant = Instant::now,
    private val log: (String) -> Unit = {},
) {
    private val refreshes = MutableStateFlow(0L)
    private val work = combine(attention, runs, inbox, due, lessons) { a, r, n, d, s ->
        DailyAssistantInput(attention = a, runs = r, notifications = n.items.size,
            replyable = n.items.count { it.reply is ReplyCapability.Available }, dueCards = d, lessons = s)
    }
    private val context = combine(routines, routineRuns, learning, presence, affinity) { r, h, l, p, a ->
        val active = p.values.filter { it.state in setOf(DevicePresenceState.READY, DevicePresenceState.CONNECTED) }
        suspend fun labels(state: DevicePresenceState) = active.filter { it.state == state }.sortedBy { it.deviceId }.map {
            safeDeviceLabel(registry.getDevice(it.deviceId)?.identity?.displayName?.takeUnless { name -> name == it.deviceId })
        }
        DailyAssistantInput(routines = r, routineRuns = h, learningCount = l,
            readyDeviceLabels = labels(DevicePresenceState.READY), connectedDeviceLabels = labels(DevicePresenceState.CONNECTED),
            voice = a?.let { DailyVoiceFact(safeDeviceLabel(it.originLabel), safeDeviceLabel(it.intendedTargetLabel),
                it.resolvedEndpointLabel?.let(::safeDeviceLabel), it.routeState) })
    }
    val snapshot: StateFlow<DailyAssistantSnapshot> = combine(work, context, refreshes) { w, c, _ ->
        DailyAssistantProjector.project(w.copy(routines = c.routines, routineRuns = c.routineRuns,
            learningCount = c.learningCount, showLearning = allowLearning(), readyDeviceLabels = c.readyDeviceLabels,
            connectedDeviceLabels = c.connectedDeviceLabels, voice = c.voice), now())
    }.distinctUntilChanged().onEach {
        log("HALO_DAILY_ASSISTANT_PROJECT uniqueItemCount=${it.items.size} primaryKind=${it.primary?.kind} needsYouCount=${it.counts.needsYou} dueCount=${it.counts.due}")
    }.flowOn(Dispatchers.IO).stateIn(scope, SharingStarted.WhileSubscribed(5000), DailyAssistantSnapshot())

    fun refresh() { refreshes.value += 1 }
    fun recordAction(action: DailyAssistantAction) { log("HALO_DAILY_ASSISTANT_ACTION actionKind=${action.javaClass.simpleName}") }
}

internal fun safeDeviceLabel(label: String?): String = label?.trim()?.takeIf {
    it.isNotEmpty() && !Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}").containsMatchIn(it) &&
        !Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}").containsMatchIn(it)
}?.take(80) ?: "External device"
