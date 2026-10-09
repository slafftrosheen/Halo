package com.halo.app.data.watch

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.watch.ContinuationWakePolicy
import com.halo.runtime.cognition.watch.ContinuationWatch
import com.halo.runtime.cognition.watch.ContinuationWatchId
import com.halo.runtime.cognition.watch.WatchBehavior
import com.halo.runtime.cognition.watch.WatchCondition
import com.halo.runtime.cognition.watch.WatchMatchMode
import com.halo.runtime.cognition.watch.WatchStatus
import com.halo.sdk.runtime.HaloDispatchers
import com.halo.sdk.runtime.productionHaloDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.Instant

/** Treat updating the same active watch as a replacement, not as a new quota entry. */
internal fun watchQuotaAvailable(activeIds: Collection<String>, incomingId: String, limit: Int): Boolean =
    activeIds.count { it != incomingId } < limit

class RoomContinuationWatchRepository(
    private val dao: ContinuationWatchDao,
    private val dispatchers: HaloDispatchers = productionHaloDispatchers()
) {
    private val saveMutex = Mutex()

    suspend fun save(watch: ContinuationWatch): Unit = withContext(dispatchers.io) {
        // Serialize quota checks and inserts owned by this repository instance.
        saveMutex.withLock {
            if (watch.status == WatchStatus.ACTIVE) {
                val active = dao.getAllActiveWatches()
                if (!watchQuotaAvailable(active.map { it.id }, watch.id.value, 100)) {
                    error("Global active watch limit (100) reached")
                }
                watch.runId?.let { runId ->
                    val inRun = active.filter { it.runId == runId.value }
                    if (!watchQuotaAvailable(inRun.map { it.id }, watch.id.value, 4)) {
                        error("Active watch limit per run (4) reached")
                    }
                }
            }
            dao.insertWatch(watch.toEntity())
        }
    }

    suspend fun findById(id: ContinuationWatchId): ContinuationWatch? = withContext(dispatchers.io) {
        dao.getWatchById(id.value)?.toDomain()
    }

    suspend fun findByDedupKey(key: String): ContinuationWatch? = withContext(dispatchers.io) {
        dao.getActiveWatchByDedupKey(key)?.toDomain()
    }

    fun observeActiveWatches(): Flow<List<ContinuationWatch>> {
        return dao.observeActiveWatches().map { list -> list.map { it.toDomain() } }
    }

    fun observeRecentWatches(): Flow<List<ContinuationWatch>> {
        return dao.observeRecentWatches().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getAllActiveWatches(): List<ContinuationWatch> = withContext(dispatchers.io) {
        dao.getAllActiveWatches().map { it.toDomain() }
    }

    suspend fun claimMatch(id: ContinuationWatchId, now: Instant): Boolean = withContext(dispatchers.io) {
        dao.claimWatchTransaction(id.value, now.toEpochMilli()) > 0
    }

    suspend fun markSatisfied(id: ContinuationWatchId): Boolean = withContext(dispatchers.io) {
        dao.satisfyWatch(id.value) > 0
    }

    suspend fun markExpired(id: ContinuationWatchId): Boolean = withContext(dispatchers.io) {
        dao.expireWatch(id.value) > 0
    }

    suspend fun cancelWatch(id: ContinuationWatchId): Boolean = withContext(dispatchers.io) {
        dao.cancelWatch(id.value) > 0
    }

    suspend fun cancelActiveWatchesByRunId(runId: AgentRunId): Int = withContext(dispatchers.io) {
        dao.cancelActiveWatchesByRunId(runId.value)
    }

    companion object {
        fun ContinuationWatchEntity.toDomain(): ContinuationWatch {
            val condObj = JSONObject(conditionJson)
            val condition: WatchCondition = when (conditionType) {
                "NETWORK_AVAILABLE" -> WatchCondition.NetworkAvailable
                "NETWORK_LOST" -> WatchCondition.NetworkLost
                "BATTERY_BELOW" -> WatchCondition.BatteryBelow(condObj.getInt("percent"))
                "BATTERY_ABOVE" -> WatchCondition.BatteryAbove(condObj.getInt("percent"))
                "DEVICE_CONNECTED" -> WatchCondition.DeviceConnected(condObj.getString("deviceId"))
                "DEVICE_DISCONNECTED" -> WatchCondition.DeviceDisconnected(condObj.getString("deviceId"))
                "APP_FOREGROUNDED" -> WatchCondition.AppForegrounded(condObj.getString("packageName"))
                "NOTIFICATION_FROM_APP" -> WatchCondition.NotificationFromApp(condObj.getString("packageName"))
                "PROVIDER_AVAILABLE" -> WatchCondition.ProviderAvailable(condObj.getString("providerId"))
                "CAPABILITY_AVAILABLE" -> WatchCondition.CapabilityAvailable(condObj.getString("capabilityId"))
                else -> error("Unknown conditionType $conditionType")
            }

            val behObj = JSONObject(behaviorJson)
            val behavior: WatchBehavior = when (behaviorType) {
                "NOTIFY" -> WatchBehavior.Notify(behObj.optString("message", "Follow-up condition happened"))
                "MAKE_RUN_RESUMABLE" -> WatchBehavior.MakeRunResumable(
                    AgentRunId(behObj.getString("runId")),
                    ContinuationWakePolicy.valueOf(behObj.optString("wakePolicy", "SURFACE_TO_USER"))
                )
                "RECONCILE_RUN" -> WatchBehavior.ReconcileRun(AgentRunId(behObj.getString("runId")))
                else -> error("Unknown behaviorType $behaviorType")
            }

            return ContinuationWatch(
                id = ContinuationWatchId(id),
                runId = runId?.let { AgentRunId(it) },
                interactionId = interactionId?.let { InteractionId(it) },
                condition = condition,
                behavior = behavior,
                status = WatchStatus.valueOf(status),
                createdAt = Instant.ofEpochMilli(createdAt),
                expiresAt = expiresAt?.let { Instant.ofEpochMilli(it) },
                deduplicationKey = deduplicationKey,
                matchMode = WatchMatchMode.valueOf(matchMode),
                matchedAt = matchedAt?.let { Instant.ofEpochMilli(it) },
                presentationText = presentationText
            )
        }

        fun ContinuationWatch.toEntity(): ContinuationWatchEntity {
            val (cType, cObj) = when (val c = condition) {
                WatchCondition.NetworkAvailable -> "NETWORK_AVAILABLE" to JSONObject()
                WatchCondition.NetworkLost -> "NETWORK_LOST" to JSONObject()
                is WatchCondition.BatteryBelow -> "BATTERY_BELOW" to JSONObject().put("percent", c.percent)
                is WatchCondition.BatteryAbove -> "BATTERY_ABOVE" to JSONObject().put("percent", c.percent)
                is WatchCondition.DeviceConnected -> "DEVICE_CONNECTED" to JSONObject().put("deviceId", c.deviceId)
                is WatchCondition.DeviceDisconnected -> "DEVICE_DISCONNECTED" to JSONObject().put("deviceId", c.deviceId)
                is WatchCondition.AppForegrounded -> "APP_FOREGROUNDED" to JSONObject().put("packageName", c.packageName)
                is WatchCondition.NotificationFromApp -> "NOTIFICATION_FROM_APP" to JSONObject().put("packageName", c.packageName)
                is WatchCondition.ProviderAvailable -> "PROVIDER_AVAILABLE" to JSONObject().put("providerId", c.providerId)
                is WatchCondition.CapabilityAvailable -> "CAPABILITY_AVAILABLE" to JSONObject().put("capabilityId", c.capabilityId)
            }

            val (bType, bObj) = when (val b = behavior) {
                is WatchBehavior.Notify -> "NOTIFY" to JSONObject().put("message", b.message)
                is WatchBehavior.MakeRunResumable -> "MAKE_RUN_RESUMABLE" to JSONObject().put("runId", b.runId.value).put("wakePolicy", b.wakePolicy.name)
                is WatchBehavior.ReconcileRun -> "RECONCILE_RUN" to JSONObject().put("runId", b.runId.value)
            }

            return ContinuationWatchEntity(
                id = id.value,
                runId = runId?.value,
                interactionId = interactionId?.value,
                conditionType = cType,
                conditionJson = cObj.toString(),
                behaviorType = bType,
                behaviorJson = bObj.toString(),
                presentationText = presentationText,
                status = status.name,
                createdAt = createdAt.toEpochMilli(),
                expiresAt = expiresAt?.toEpochMilli(),
                deduplicationKey = deduplicationKey,
                matchMode = matchMode.name,
                matchedAt = matchedAt?.toEpochMilli(),
                claimState = if (status == WatchStatus.MATCH_CLAIMED || status == WatchStatus.SATISFIED) "CLAIMED" else "UNCLAIMED"
            )
        }
    }
}
