package com.halo.app.nativeactions

import com.halo.app.data.routine.RoutineEntity
import com.halo.app.data.routine.RoutineRunEntity
import com.halo.app.data.watch.ContinuationWatchEntity
import org.json.JSONObject

/** Use canonical persistence fields so older trigger and action encodings are retained byte-for-byte. */
internal object PrivateLibraryAutomationCodec {
    fun encode(row: RoutineEntity): JSONObject = with(row) {
        JSONObject().put("name", name).put("enabled", enabled).put("trigger", triggerJson)
            .put("conditions", conditionsJson).put("actions", actionsJson).put("schemaVersion", schemaVersion)
            .put("createdAt", createdAt).put("updatedAt", updatedAt).put("lastRunAt", lastRunAt)
            .put("nextRunAt", nextRunAt).put("delivery", deliveryJson).put("grantDigest", grantDigest)
            .put("minimumIntervalSeconds", minimumIntervalSeconds)
    }

    fun routine(id: String, value: JSONObject): RoutineEntity = with(value) {
        RoutineEntity(id, getString("name"), getBoolean("enabled"), getString("trigger"), getString("conditions"),
            getString("actions"), getInt("schemaVersion"), getLong("createdAt"), getLong("updatedAt"), number("lastRunAt"),
            number("nextRunAt"), getString("delivery"), string("grantDigest"), getLong("minimumIntervalSeconds"))
    }

    fun encode(row: RoutineRunEntity): JSONObject = with(row) {
        JSONObject().put("id", id).put("agentRunId", agentRunId).put("triggerAt", triggerAt).put("state", state)
            .put("source", source).put("signalId", signalId).put("rootOccurrenceId", rootOccurrenceId)
            .put("causedByOccurrenceId", causedByOccurrenceId).put("actionsCompleted", actionsCompleted)
            .put("actionsTotal", actionsTotal).put("finishedAt", finishedAt).put("reason", reason)
            .put("deliveryOutcome", deliveryOutcome).put("coalescedCount", coalescedCount)
    }

    fun run(routineId: String, value: JSONObject): RoutineRunEntity = with(value) {
        RoutineRunEntity(getString("id"), routineId, getString("agentRunId"), getLong("triggerAt"), getString("state"),
            getString("source"), string("signalId"), string("rootOccurrenceId"), string("causedByOccurrenceId"),
            getInt("actionsCompleted"), getInt("actionsTotal"), number("finishedAt"), string("reason"),
            string("deliveryOutcome"), getInt("coalescedCount"))
    }

    fun encode(row: ContinuationWatchEntity): JSONObject = with(row) {
        JSONObject().put("runId", runId).put("interactionId", interactionId).put("conditionType", conditionType)
            .put("condition", conditionJson).put("behaviorType", behaviorType).put("behavior", behaviorJson)
            .put("presentationText", presentationText).put("status", status).put("createdAt", createdAt)
            .put("expiresAt", expiresAt).put("deduplicationKey", deduplicationKey).put("matchMode", matchMode)
            .put("matchedAt", matchedAt).put("claimState", claimState)
    }

    fun watch(id: String, value: JSONObject): ContinuationWatchEntity = with(value) {
        ContinuationWatchEntity(id, string("runId"), string("interactionId"), getString("conditionType"),
            getString("condition"), getString("behaviorType"), getString("behavior"), string("presentationText"),
            getString("status"), getLong("createdAt"), number("expiresAt"), string("deduplicationKey"), getString("matchMode"),
            number("matchedAt"), getString("claimState"))
    }

    private fun JSONObject.string(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.number(key: String): Long? = if (isNull(key)) null else getLong(key)
}
