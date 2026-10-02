package com.halo.app.watch

import android.util.Log
import com.halo.app.data.watch.RoomContinuationWatchRepository
import com.halo.runtime.cognition.agent.AgentRunCoordinator
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.AgentStepId
import com.halo.runtime.cognition.attention.AttentionController
import com.halo.runtime.cognition.attention.AttentionImportance
import com.halo.runtime.cognition.attention.AttentionRequest
import com.halo.runtime.cognition.attention.AttentionRequestId
import com.halo.runtime.cognition.attention.AttentionSource
import com.halo.runtime.cognition.attention.DeliveryIntent
import com.halo.runtime.cognition.attention.UserFacingContent
import com.halo.runtime.cognition.routine.AutomationEnvironmentSnapshot
import com.halo.runtime.cognition.routine.AutomationSignal
import com.halo.runtime.cognition.routine.AutomationSignalRegistry
import com.halo.runtime.cognition.routine.AutomationTrust
import com.halo.runtime.cognition.watch.ContinuationWakePolicy
import com.halo.runtime.cognition.watch.ContinuationWatch
import com.halo.runtime.cognition.watch.ContinuationWatchId
import com.halo.runtime.cognition.watch.ContinuationWatchMatcher
import com.halo.runtime.cognition.watch.WatchBehavior
import com.halo.runtime.cognition.watch.WatchCondition
import com.halo.runtime.cognition.watch.WatchMatchMode
import com.halo.runtime.cognition.watch.WatchStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class ContinuationWatchRuntime(
    private val registry: AutomationSignalRegistry,
    private val repository: RoomContinuationWatchRepository,
    private val attentionController: AttentionController?,
    private val agentRunCoordinator: AgentRunCoordinator? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun onStartup(environmentSnapshot: AutomationEnvironmentSnapshot? = null) {
        val now = Instant.now()
        val active = repository.getAllActiveWatches()
        for (watch in active) {
            if (watch.expiresAt != null && !now.isBefore(watch.expiresAt)) {
                repository.markExpired(watch.id)
                Log.i("HALO_WATCH_EXPIRED", "watch=${watch.id.value} condition=${watch.condition::class.simpleName}")
                val rId = watch.runId
                if (rId != null) {
                    agentRunCoordinator?.recordStep(
                        AgentStep.ExternalConditionExpired(
                            id = AgentStepId(UUID.randomUUID().toString()),
                            runId = rId,
                            createdAt = now,
                            watchId = watch.id.value,
                            conditionType = watch.condition::class.simpleName ?: "UNKNOWN",
                        )
                    )
                }
            } else if (watch.matchMode == WatchMatchMode.CURRENT_OR_NEXT && environmentSnapshot != null) {
                val satisfied = evaluateLevelCondition(watch.condition, environmentSnapshot)
                if (satisfied) {
                    processWatchMatch(watch, now, source = "baseline_reconciliation")
                }
            }
        }
    }

    suspend fun observe(): Unit = coroutineScope {
        onStartup()
        val seenSignalIds = LinkedHashSet<String>()

        registry.signals.collect { signal ->
            if (signal.trust == AutomationTrust.INTERNAL_TEST || !seenSignalIds.add(signal.id)) return@collect
            if (seenSignalIds.size > 512) seenSignalIds.remove(seenSignalIds.first())

            val activeWatches = repository.getAllActiveWatches()
            for (watch in activeWatches) {
                if (watch.expiresAt != null && Instant.now().isAfter(watch.expiresAt)) {
                    repository.markExpired(watch.id)
                    Log.i("HALO_WATCH_EXPIRED", "watch=${watch.id.value}")
                    continue
                }

                val matched = ContinuationWatchMatcher.matches(watch.condition, signal)
                if (matched) {
                    processWatchMatch(watch, signal.occurredAt, source = "signal:${signal.kind}")
                }
            }
        }
    }

    private suspend fun processWatchMatch(watch: ContinuationWatch, occurredAt: Instant, source: String) {
        val claimed = repository.claimMatch(watch.id, occurredAt)
        if (!claimed) return

        Log.i(
            "HALO_WATCH_MATCH",
            "HALO_WATCH_MATCH watch=${watch.id.value} condition=${watch.condition::class.simpleName} " +
                "run=${watch.runId?.value ?: "standalone"} mode=${watch.matchMode} source=$source"
        )
        Log.i(
            "HALO_WATCH_CLAIM",
            "HALO_WATCH_CLAIM watch=${watch.id.value} run=${watch.runId?.value ?: "standalone"}"
        )

        try {
            when (val b = watch.behavior) {
                is WatchBehavior.Notify -> {
                    if (attentionController != null) {
                        attentionController.submit(
                            AttentionRequest(
                                id = AttentionRequestId(UUID.randomUUID().toString()),
                                source = AttentionSource.FollowUp,
                                importance = AttentionImportance.NORMAL,
                                deliveryIntent = DeliveryIntent.Inform(
                                    UserFacingContent(
                                        title = "Follow-up",
                                        body = b.message
                                    )
                                ),
                                explanation = "Watch condition satisfied"
                            )
                        )
                    }
                    repository.markSatisfied(watch.id)
                    Log.i("HALO_WATCH_SATISFIED", "HALO_WATCH_SATISFIED watch=${watch.id.value}")
                }

                is WatchBehavior.MakeRunResumable -> {
                    val rId = watch.runId
                    if (rId != null) {
                        agentRunCoordinator?.recordStep(
                            AgentStep.ExternalConditionSatisfied(
                                id = AgentStepId(UUID.randomUUID().toString()),
                                runId = rId,
                                createdAt = occurredAt,
                                watchId = watch.id.value,
                                conditionType = watch.condition::class.simpleName ?: "UNKNOWN",
                                targetId = getTargetId(watch.condition),
                            )
                        )
                        // The run entered WAITING_FOR_EVENT when the continuation was registered.
                        // Matching records durable evidence and surfaces readiness; it never auto-runs
                        // and must not rewrite the waiting state at match time.
                        if (b.wakePolicy == ContinuationWakePolicy.SURFACE_TO_USER && attentionController != null) {
                            val text = watch.presentationText ?: "Condition met; task ready to continue."
                            attentionController.submit(
                                AttentionRequest(
                                    id = AttentionRequestId(UUID.randomUUID().toString()),
                                    source = AttentionSource.FollowUp,
                                    importance = AttentionImportance.NORMAL,
                                    deliveryIntent = DeliveryIntent.Inform(
                                        UserFacingContent(
                                            title = "Ready to continue",
                                            body = text
                                        )
                                    ),
                                    explanation = "Run condition met"
                                )
                            )
                        }
                    }
                    repository.markSatisfied(watch.id)
                    Log.i("HALO_WATCH_SATISFIED", "HALO_WATCH_SATISFIED watch=${watch.id.value} run=${watch.runId?.value}")
                    Log.i("HALO_CONTINUATION_READY", "HALO_CONTINUATION_READY watch=${watch.id.value} run=${watch.runId?.value}")
                }

                is WatchBehavior.ReconcileRun -> {
                    val rId = watch.runId
                    if (rId != null) {
                        agentRunCoordinator?.recordStep(
                            AgentStep.ExternalConditionSatisfied(
                                id = AgentStepId(UUID.randomUUID().toString()),
                                runId = rId,
                                createdAt = occurredAt,
                                watchId = watch.id.value,
                                conditionType = watch.condition::class.simpleName ?: "UNKNOWN",
                                targetId = getTargetId(watch.condition),
                            )
                        )
                    }
                    repository.markSatisfied(watch.id)
                    Log.i("HALO_WATCH_SATISFIED", "HALO_WATCH_SATISFIED watch=${watch.id.value} run=${watch.runId?.value}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("HALO_WATCH_FAIL", "Failed to process matched watch ${watch.id.value}", e)
        }
    }

    private fun evaluateLevelCondition(condition: WatchCondition, e: AutomationEnvironmentSnapshot): Boolean = when (condition) {
        WatchCondition.NetworkAvailable -> e.networkAvailable == true
        WatchCondition.NetworkLost -> e.networkAvailable == false
        is WatchCondition.BatteryBelow -> e.batteryPercent?.let { it < condition.percent } == true
        is WatchCondition.BatteryAbove -> e.batteryPercent?.let { it > condition.percent } == true
        is WatchCondition.DeviceConnected -> condition.deviceId in e.availableDevices
        is WatchCondition.DeviceDisconnected -> condition.deviceId !in e.availableDevices
        is WatchCondition.AppForegrounded -> e.foregroundPackage?.equals(condition.packageName, ignoreCase = true) == true
        is WatchCondition.ProviderAvailable -> condition.providerId in e.availableProviders
        else -> false
    }

    private fun getTargetId(condition: WatchCondition): String? = when (condition) {
        is WatchCondition.DeviceConnected -> condition.deviceId
        is WatchCondition.DeviceDisconnected -> condition.deviceId
        is WatchCondition.AppForegrounded -> condition.packageName
        is WatchCondition.NotificationFromApp -> condition.packageName
        is WatchCondition.ProviderAvailable -> condition.providerId
        is WatchCondition.CapabilityAvailable -> condition.capabilityId
        else -> null
    }
}
