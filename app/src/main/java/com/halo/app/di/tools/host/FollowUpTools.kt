package com.halo.app.di.tools.host

import com.halo.app.data.watch.RoomContinuationWatchRepository
import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.runtime.cognition.watch.ContinuationWatch
import com.halo.runtime.cognition.watch.ContinuationWatchId
import com.halo.runtime.cognition.watch.WatchBehavior
import com.halo.runtime.cognition.watch.WatchCondition
import com.halo.runtime.cognition.watch.WatchMatchMode
import com.halo.runtime.cognition.watch.WatchStatus
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class FollowUpTools(
    private val repository: RoomContinuationWatchRepository
) : ToolExecutor {

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_create_watch",
            name = "halo_create_watch",
            description = "Create a durable follow-up watch for supported platform signals.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "conditionType" to mapOf(
                        "type" to "string",
                        "enum" to listOf(
                            "network_available",
                            "network_lost",
                            "battery_below",
                            "device_connected",
                            "app_foregrounded",
                            "notification_posted",
                            "provider_available",
                            "capability_available"
                        )
                    ),
                    "targetId" to mapOf("type" to "string"),
                    "percent" to mapOf("type" to "integer"),
                    "message" to mapOf("type" to "string")
                ),
                "required" to listOf("conditionType"),
                "additionalProperties" to false
            )
        ),
        ToolDefinition(
            id = "halo_list_watches",
            name = "halo_list_watches",
            description = "List active follow-up watches.",
            parameters = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "additionalProperties" to false
            )
        ),
        ToolDefinition(
            id = "halo_cancel_watch",
            name = "halo_cancel_watch",
            description = "Cancel an active follow-up watch by ID or target.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "watchId" to mapOf("type" to "string"),
                    "targetId" to mapOf("type" to "string")
                ),
                "additionalProperties" to false
            )
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind = com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)

        return when (toolCall.functionName) {
            "halo_create_watch" -> {
                val conditionType = toolCall.arguments["conditionType"] as? String ?: return reject("Missing conditionType")
                val targetId = toolCall.arguments["targetId"] as? String
                val percent = (toolCall.arguments["percent"] as? Number)?.toInt()
                val message = toolCall.arguments["message"] as? String ?: "Follow-up condition met"

                val condition: WatchCondition = when (conditionType) {
                    "network_available" -> WatchCondition.NetworkAvailable
                    "network_lost" -> WatchCondition.NetworkLost
                    "battery_below" -> {
                        val p = percent ?: 20
                        WatchCondition.BatteryBelow(p)
                    }
                    "device_connected" -> {
                        val tid = targetId ?: return reject("targetId required for device_connected")
                        WatchCondition.DeviceConnected(tid)
                    }
                    "app_foregrounded" -> {
                        val pkg = targetId ?: return reject("targetId required for app_foregrounded")
                        WatchCondition.AppForegrounded(pkg)
                    }
                    "notification_posted" -> {
                        val pkg = targetId ?: return reject("targetId required for notification_posted")
                        WatchCondition.NotificationFromApp(pkg)
                    }
                    "provider_available" -> {
                        val pid = targetId ?: return reject("targetId required for provider_available")
                        WatchCondition.ProviderAvailable(pid)
                    }
                    "capability_available" -> {
                        val cid = targetId ?: return reject("targetId required for capability_available")
                        WatchCondition.CapabilityAvailable(cid)
                    }
                    else -> return reject("Unsupported conditionType $conditionType")
                }

                val watchId = "watch-" + UUID.randomUUID().toString().take(8)
                val dedupKey = "$conditionType:${targetId ?: percent ?: "none"}:standalone"
                
                val watch = ContinuationWatch(
                    id = ContinuationWatchId(watchId),
                    runId = null,
                    interactionId = null,
                    condition = condition,
                    behavior = WatchBehavior.Notify(message),
                    status = WatchStatus.ACTIVE,
                    createdAt = Instant.now(),
                    expiresAt = Instant.now().plus(7, ChronoUnit.DAYS),
                    deduplicationKey = dedupKey,
                    matchMode = WatchMatchMode.NEXT_TRANSITION,
                    presentationText = message
                )

                try {
                    repository.save(watch)
                    ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Created follow-up watch $watchId for $conditionType")
                } catch (e: Exception) {
                    reject("Failed to save watch", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
                }
            }

            "halo_list_watches" -> {
                val active = repository.getAllActiveWatches()
                val summary = if (active.isEmpty()) {
                    "No active follow-up watches."
                } else {
                    active.joinToString("\n") { w ->
                        "id=${w.id.value}; condition=${w.condition::class.simpleName}; text=${w.presentationText ?: "none"}; expires=${w.expiresAt}"
                    }
                }
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, summary)
            }

            "halo_cancel_watch" -> {
                val watchId = toolCall.arguments["watchId"] as? String
                val targetId = toolCall.arguments["targetId"] as? String

                val active = repository.getAllActiveWatches()
                val toCancel = active.filter { w ->
                    (watchId != null && w.id.value == watchId) ||
                        (targetId != null && (w.presentationText?.contains(targetId, true) == true || w.deduplicationKey?.contains(targetId, true) == true))
                }

                if (toCancel.isEmpty()) {
                    return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "No matching active watch found to cancel.")
                }

                var count = 0
                for (w in toCancel) {
                    if (repository.cancelWatch(w.id)) count++
                }
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Cancelled $count follow-up watch(es).")
            }

            else -> reject("Unknown tool", com.halo.runtime.cognition.tools.ToolRejectionKind.TOOL_UNKNOWN)
        }
    }
}
