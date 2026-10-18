package com.halo.app.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import org.json.JSONObject

/** Execution preferences are additive. Existing role bindings and native model tuning are not rewritten. */
class WorkloadPreferences(private val storage: Storage) {
    fun profile(workload: LocalWorkload): WorkloadExecutionProfile {
        val raw=storage.getString("workload.execution.v1.${workload.name}") ?: return WorkloadExecutionProfile()
        return try {
            val j=JSONObject(raw)
            WorkloadExecutionProfile(backend=WorkloadBackend.valueOf(j.getString("backend")),
                residency=ResidencyPreference.valueOf(j.getString("residency")),timeoutMs=j.getLong("timeoutMs"),
                maxOutputTokens=j.getInt("output"),contextTokens=j.getInt("context"),
                allowRemote=j.getBoolean("remote"),allowFallback=j.getBoolean("fallback"),inheritRoutingPolicy=j.optBoolean("inheritRouting",true))
        } catch(_: Exception) { WorkloadExecutionProfile() }
    }
    fun save(workload: LocalWorkload,profile: WorkloadExecutionProfile) {
        val j=JSONObject().put("schema",1).put("backend",profile.backend.name).put("residency",profile.residency.name)
            .put("timeoutMs",profile.timeoutMs).put("output",profile.maxOutputTokens).put("context",profile.contextTokens)
            .put("remote",profile.allowRemote).put("fallback",profile.allowFallback)
            .put("inheritRouting",profile.inheritRoutingPolicy)
        storage.putString("workload.execution.v1.${workload.name}",j.toString())
    }
    /** Explicit standalone embedding role. Never a conversational AiRoleBinding. */
    data class EmbeddingChoice(val modelId: String,val sha256: String,val dimension: Int)
    fun selectedEmbedding(): EmbeddingChoice? = try {
        val json=storage.getString("workload.role.embedding.v1")?.let(::JSONObject) ?: return null
        val id=json.getString("modelId")
        val sha=json.getString("sha256")
        val dim=json.getInt("dimension")
        if(id.isBlank() || id.length>200 || !sha.matches(Regex("[a-f0-9]{64}")) ||
            dim !in 1..16384) null else EmbeddingChoice(id,sha,dim)
    } catch (_: Exception) { null }
    fun selectEmbedding(modelId: String,sha256: String,dimension: Int) {
        require(modelId.isNotBlank() && modelId.length<=200 && modelId.none { it.isISOControl() })
        require(sha256.matches(Regex("[a-f0-9]{64}")) && dimension in 1..16384)
        storage.putString("workload.role.embedding.v1",
            JSONObject().put("modelId",modelId).put("sha256",sha256).put("dimension",dimension).toString())
    }
    fun clearEmbedding() { storage.putString("workload.role.embedding.v1","") }

    fun assignment(workload: LocalWorkload,bindings: Map<AiModelRole,AiRoleBinding>,default: WorkloadTarget?): WorkloadAssignment {
        val role=workload.legacyRole
        val specialized = if(workload==LocalWorkload.EMBED) selectedEmbedding()?.let { choice ->
            WorkloadAssignment(workload, WorkloadSelection.EXPLICIT,
                WorkloadTarget("provider.litert.embedding",choice.modelId,choice.sha256))
        } else null
        val inherited=if(role==AiModelRole.CHAT) WorkloadAssignment(workload,WorkloadSelection.INHERITED,default)
            else role?.let { bindings[it]?.asWorkloadAssignment() }
        return (specialized ?: inherited ?: WorkloadAssignment(workload,WorkloadSelection.AUTOMATIC)).let {
            if(it.selection==WorkloadSelection.INHERITED) it.copy(target=default,execution=profile(workload))
            else it.copy(execution=profile(workload))
        }
    }
}
