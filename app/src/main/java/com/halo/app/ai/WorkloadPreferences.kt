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
    fun assignment(workload: LocalWorkload,bindings: Map<AiModelRole,AiRoleBinding>,default: WorkloadTarget?): WorkloadAssignment {
        val role=workload.legacyRole
        val inherited=if(role==AiModelRole.CHAT) WorkloadAssignment(workload,WorkloadSelection.INHERITED,default)
            else role?.let { bindings[it]?.asWorkloadAssignment() }
        return (inherited ?: WorkloadAssignment(workload,WorkloadSelection.AUTOMATIC)).let {
            if(it.selection==WorkloadSelection.INHERITED) it.copy(target=default,execution=profile(workload))
            else it.copy(execution=profile(workload))
        }
    }
}
