package com.halo.app.nativeactions

import com.halo.app.data.PersistedConversation
import com.halo.app.data.PersistedTurn
import com.halo.app.data.agent.AgentRunEntity
import com.halo.app.data.agent.AgentStepEntity
import com.halo.app.data.memory.EpisodeEntity
import com.halo.app.data.memory.ReflectionStateEntity
import org.json.JSONArray
import org.json.JSONObject

internal enum class PrivateHistoryKind { CONVERSATION, AGENT_RUN }
internal data class PrivateHistoryRef(val kind: PrivateHistoryKind, val id: String)

internal data class PrivateConversationHistory(val conversation: PersistedConversation, val turns: List<PersistedTurn>,
    val contextPolicy: String? = null) {
    init {
        require(turns.size <= 5000 && turns.all { it.conversationId == conversation.id })
        contextPolicy?.let { raw ->
            require(raw.length <= 2048)
            val policy = JSONObject(raw)
            require(policy.keys().asSequence().all { it in contextPolicyKeys && policy.get(it) is Boolean })
        }
    }

    fun encode() = JSONObject().put("version", 1).put("kind", PrivateHistoryKind.CONVERSATION.name)
        .put("created", conversation.createdAt).put("updated", conversation.updatedAt).put("title", conversation.title)
        .put("active", conversation.active).put("archived", conversation.archived).put("contextPolicy", contextPolicy)
        .put("turns", JSONArray(turns.map { row ->
            JSONObject().put("id", row.id).put("user", row.userText).put("assistant", row.assistantText)
                .put("timestamp", row.timestamp).put("metadata", row.metadata)
        }))

    companion object {
        fun decode(id: String, json: JSONObject) = with(json) {
            require(getInt("version") == 1 && getString("kind") == PrivateHistoryKind.CONVERSATION.name)
            PrivateConversationHistory(PersistedConversation(id, getLong("created"), optBoolean("active", false), getString("title"), getLong("updated"),
                getBoolean("archived")), rows("turns", 5000).map { turn ->
                PersistedTurn(turn.getString("id"), id, turn.getString("user"), turn.getString("assistant"),
                    turn.getLong("timestamp"), turn.getString("metadata"))
            }, stringOrNull("contextPolicy"))
        }

        private val contextPolicyKeys = setOf("memories", "skillsTools", "workspace", "cloudWorkspace", "cloudScreen", "cloudImages", "cloudMemories")
    }
}

internal data class PrivateAgentHistory(val run: AgentRunEntity, val steps: List<AgentStepEntity>,
    val episodes: List<EpisodeEntity>, val reflection: ReflectionStateEntity?) {
    init {
        require((run.state in terminalStates || run.state == waitingForEvent) && steps.size <= 5000 && episodes.size <= 500)
        require(steps.all { it.runId == run.id && it.interactionId == run.interactionId } && episodes.all { it.runId == run.id })
        require(reflection == null || reflection.id == "run:${run.id}" && reflection.status in terminalReflections)
    }

    fun encode(): JSONObject = JSONObject().put("version", 1).put("kind", PrivateHistoryKind.AGENT_RUN.name)
        .put("run", with(run) { JSONObject().put("interaction", interactionId).put("request", request)
            .put("started", startedAt).put("updated", updatedAt).put("completed", completedAt).put("state", state)
            .put("failure", failureReason).put("parent", parentRunId).put("root", rootRunId).put("role", delegatedRole)
            .put("depth", delegationDepth).put("provider", pinnedProviderId).put("model", pinnedModelId)
            .put("local", pinnedTargetLocal).put("inputs", delegatedInputRefs) })
        .put("steps", JSONArray(steps.map { row -> with(row) { JSONObject().put("id", id).put("ordinal", ordinal)
            .put("type", type).put("schema", schemaVersion).put("payload", payloadJson).put("key", idempotencyKey)
            .put("state", state).put("created", createdAt).put("completed", completedAt) } }))
        .put("episodes", JSONArray(episodes.map { row -> with(row) { JSONObject().put("id", id).put("json", json)
            .put("endedAt", endedAt).put("importance", importance).put("uses", usageCount).put("last", lastReferencedAt) } }))
        .put("reflection", reflection?.let { row -> with(row) { JSONObject().put("version", policyVersion)
            .put("status", status).put("mode", mode).put("updated", updatedAt) } })

    companion object {
        val terminalStates = setOf("COMPLETED", "FAILED", "CANCELLED", "INTERRUPTED")
        val terminalReflections = setOf("NOT_REQUIRED", "COMPLETED", "FAILED", "SKIPPED")
        const val waitingForEvent = "WAITING_FOR_EVENT"

        fun decode(id: String, json: JSONObject): PrivateAgentHistory {
            require(json.getInt("version") == 1 && json.getString("kind") == PrivateHistoryKind.AGENT_RUN.name)
            val run = with(json.getJSONObject("run")) {
                AgentRunEntity(id, getString("interaction"), getString("request"), getLong("started"), getLong("updated"),
                    longOrNull("completed"), getString("state"), stringOrNull("failure"), stringOrNull("parent"), stringOrNull("root"),
                    stringOrNull("role"), getInt("depth"), stringOrNull("provider"), stringOrNull("model"),
                    if (isNull("local")) null else getBoolean("local"), stringOrNull("inputs"))
            }
            val steps = json.rows("steps", 5000).map { row -> with(row) {
                AgentStepEntity(getString("id"), id, run.interactionId, getInt("ordinal"), getString("type"), getInt("schema"),
                    getString("payload"), stringOrNull("key"), getString("state"), getLong("created"), longOrNull("completed"))
            } }
            val episodes = json.rows("episodes", 500).map { row -> with(row) {
                EpisodeEntity(getString("id"), id, getString("json"), getLong("endedAt"), getInt("importance"), getLong("uses"), longOrNull("last"))
            } }
            val reflection = json.optJSONObject("reflection")?.let { row -> with(row) {
                ReflectionStateEntity("run:$id", getInt("version"), getString("status"), getString("mode"), getLong("updated"))
            } }
            return PrivateAgentHistory(run, steps, episodes, reflection)
        }
    }
}

internal fun JSONObject.historyString(key: String): String? = stringOrNull(key)?.takeIf(String::isNotBlank)
private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
private fun JSONObject.rows(key: String, limit: Int): List<JSONObject> = getJSONArray(key).let { rows ->
    require(rows.length() <= limit)
    (0 until rows.length()).map(rows::getJSONObject)
}
