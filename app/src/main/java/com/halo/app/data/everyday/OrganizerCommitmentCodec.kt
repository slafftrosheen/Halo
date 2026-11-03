package com.halo.app.data.everyday

import com.halo.app.everyday.organizer.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

internal object OrganizerCommitmentCodec {
    private fun JSONObject.text(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    fun encode(value: OrganizerCommitment): JSONObject = JSONObject().apply {
        value.validate()
        put("schema", 1); put("owner", value.owner.name); put("purpose", value.purpose.name)
        put("intent", value.intent); put("state", value.state.name); put("createdBy", value.createdBy.name)
        put("nextHumanAction", value.nextHumanAction)
        put("actions", JSONArray(value.actions.map { JSONObject().put("capability", it.capability).put("arguments", it.argumentsJson) }))
        put("sources", JSONArray(value.sources.map { JSONObject().put("id", it.id).put("version", it.version) }))
        value.recovery?.let { put("recovery", JSONObject().put("planId", it.planId).put("routineId", it.routineId).put("runId", it.runId)
            .put("recordedAt", it.recordedAt.toEpochMilli()).put("uncertain", it.uncertain).put("reviewed", it.reviewed)) }
        value.execution?.let { put("execution", JSONObject().put("planId", it.planId).put("routineId", it.routineId)
            .put("routineVersion", it.routineVersion).put("runId", it.runId).put("scheduling", it.scheduling).put("claimedStep", it.claimedStep)
            .put("lastEvidenceIds", JSONArray(it.lastEvidenceIds)).put("planNonce", it.planNonce)) }
        value.result?.let { put("result", JSONObject().put("summary", it.summary).put("confirmedAt", it.confirmedAt.toEpochMilli())
            .put("evidenceIds", JSONArray(it.evidenceIds)).put("conditionMatched", it.conditionMatched).put("newMatch", it.newMatch).put("messages", JSONArray(it.messages)).put("waiting", it.waiting)
            .put("sourceRefs", JSONArray(it.sourceRefs.map { ref -> JSONObject().put("id", ref.id).put("version", ref.version) }))) }
        value.stopwatch?.let { put("stopwatch", JSONObject().put("accumulatedMs", it.accumulatedMs).put("startedAt", it.startedAt?.toEpochMilli())
            .put("elapsedStartMs", it.elapsedStartMs).put("bootId", it.bootId).put("laps", JSONArray(it.laps))) }
    }
    fun decode(json: JSONObject): OrganizerCommitment {
        require(json.toString().length <= 120000 && json.getInt("schema") == 1)
        val actions = json.getJSONArray("actions").also { require(it.length() <= 6) }
        val sources = json.getJSONArray("sources").also { require(it.length() <= 10) }
        val execution = json.optJSONObject("execution")?.let { OrganizerExecutionBinding(it.getString("planId"), it.getString("routineId"),
            it.text("routineVersion"), it.text("runId"), it.getString("scheduling"), it.optInt("claimedStep", -1), it.optJSONArray("lastEvidenceIds")?.strings().orEmpty(), it.optString("planNonce", "")) }
        val result = json.optJSONObject("result")?.let { OrganizerWorkResult(it.getString("summary"), Instant.ofEpochMilli(it.getLong("confirmedAt")),
            it.getJSONArray("evidenceIds").strings(), if (it.isNull("conditionMatched")) null else it.getBoolean("conditionMatched"),
            if (it.isNull("newMatch")) null else it.getBoolean("newMatch"), it.optJSONArray("messages")?.strings().orEmpty(), it.optBoolean("waiting", false),
            it.optJSONArray("sourceRefs")?.let { refs -> require(refs.length() <= 100); (0 until refs.length()).map { i -> refs.getJSONObject(i).let { r -> OrganizerSourceRef(r.getString("id"), r.getLong("version")) } } }.orEmpty()) }
        val stopwatch = json.optJSONObject("stopwatch")?.let { w -> StopwatchState(w.getLong("accumulatedMs"),
            if (w.isNull("startedAt")) null else Instant.ofEpochMilli(w.getLong("startedAt")),
            if (w.isNull("elapsedStartMs")) null else w.getLong("elapsedStartMs"), w.text("bootId"),
            w.getJSONArray("laps").let { laps -> require(laps.length() <= 50); (0 until laps.length()).map { laps.getLong(it) } }) }
        return OrganizerCommitment(CommitmentOwner.valueOf(json.getString("owner")), CommitmentPurpose.valueOf(json.getString("purpose")),
            json.getString("intent"), HaloWorkState.valueOf(json.getString("state")),
            (0 until actions.length()).map { actions.getJSONObject(it).let { a -> OrganizerPlannedAction(a.getString("capability"), a.getString("arguments")) }
                .also { com.halo.app.automation.RoutineArguments.decode(it.argumentsJson) } },
            (0 until sources.length()).map { sources.getJSONObject(it).let { s -> OrganizerSourceRef(s.getString("id"), s.getLong("version")) } },
            execution, result, json.text("nextHumanAction"), CommitmentCreator.valueOf(json.getString("createdBy")), stopwatch,
            json.optJSONObject("recovery")?.let { OrganizerWorkRecovery(it.getString("planId"), it.getString("routineId"), it.text("runId"),
                Instant.ofEpochMilli(it.getLong("recordedAt")), it.getBoolean("uncertain"), it.getBoolean("reviewed")) }).also { it.validate() }
    }
}
