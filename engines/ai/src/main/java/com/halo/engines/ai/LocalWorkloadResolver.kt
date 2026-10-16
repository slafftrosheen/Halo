package com.halo.engines.ai

import com.halo.sdk.provider.*

/** Pure enumeration. Never probes, initializes, downloads or authenticates. */
class LocalWorkloadResolver {
    fun resolve(request: WorkloadRequest,assignment: WorkloadAssignment,candidates: List<WorkloadCandidate>): WorkloadResolution {
        require(request.workload==assignment.workload)
        val profile=assignment.execution
        fun reject(code: String,state: WorkloadReadiness=WorkloadReadiness.UNAVAILABLE)=WorkloadResolution(
            request.workload,assignment.target,profile.backend,null,state,code)
        if(assignment.selection==WorkloadSelection.DISABLED) return reject("DISABLED",WorkloadReadiness.DISABLED)
        if(assignment.selection==WorkloadSelection.INHERITED && assignment.target==null) return reject("INHERITED_TARGET_UNRESOLVED")
        val explicit=assignment.selection!=WorkloadSelection.AUTOMATIC
        val ordered=if(explicit) candidates.filter { matches(assignment.target,it.target) }
            else {
                val preferred=listOfNotNull(assignment.target)+if(profile.allowFallback) assignment.fallbacks else emptyList()
                candidates.sortedBy { c -> preferred.indexOfFirst { matches(it,c.target) }.takeIf { it>=0 } ?: Int.MAX_VALUE }
            }
        var rejection=reject("NO_ELIGIBLE_CANDIDATE")
        for(candidate in ordered) {
            val target=candidate.target
            fun refused(reason: String,state: WorkloadReadiness=WorkloadReadiness.UNAVAILABLE) {
                rejection=WorkloadResolution(request.workload,target,profile.backend,null,state,reason)
            }
            if(request.workload !in candidate.declaredTasks) { refused("TASK_NOT_DECLARED"); continue }
            if(!candidate.local && (!profile.allowRemote || !request.remoteConsent || request.privateContent)) {
                refused("REMOTE_NOT_PERMITTED"); continue
            }
            if(candidate.local && !candidate.installed) { refused("NOT_INSTALLED"); continue }
            if(!candidate.deterministic && candidate.local && !candidate.nativeReadable) { refused("NATIVE_NOT_READABLE"); continue }
            val estimate=maxOf(profile.memoryEstimateBytes,candidate.memoryEstimateBytes)
            if(candidate.local && estimate>0 && (request.availableBytes<=0 || estimate>request.availableBytes)) {
                refused("MEMORY_NOT_ADMITTED"); continue
            }
            val proofs=candidate.proofs.filter { it.target==target && it.workload==request.workload &&
                it.environment==request.environment && it.correct && it.stable &&
                (request.workload!=LocalWorkload.TTS || it.audibleConfirmed==true) }
            val backend=if(profile.backend!=WorkloadBackend.AUTO) profile.backend else {
                proofs.filter { it.backend in candidate.supportedBackends }.minByOrNull { it.warmMs ?: it.coldMs ?: Long.MAX_VALUE }?.backend
                    ?: WorkloadBackend.CPU
            }
            if(backend !in candidate.supportedBackends) { refused("BACKEND_UNSUPPORTED"); continue }
            if(profile.requireTaskProof && !candidate.deterministic && proofs.none { it.backend==backend }) {
                refused("TASK_PROOF_MISSING",WorkloadReadiness.NEEDS_QUALIFICATION); continue
            }
            return WorkloadResolution(request.workload,target,profile.backend,null,
                if(candidate.resident || candidate.deterministic || !candidate.local) WorkloadReadiness.READY else WorkloadReadiness.NEEDS_LOADING,
                "ELIGIBLE",assignment.target!=null && !matches(assignment.target,target),estimate,
                !candidate.local,residency=profile.residency,backendPolicy=profile.backend)
        }
        return rejection
    }
    private fun matches(expected: WorkloadTarget?,actual: WorkloadTarget)=expected!=null && expected.providerId==actual.providerId &&
        expected.modelId==actual.modelId && (expected.artifactSha256==null || expected.artifactSha256==actual.artifactSha256)
}

/** Existing native parser/ownership is supplied by the host; this layer grants no authority. */
class LayeredWorkloadIntent(private val deterministic: (String)->String?,
    private val classifier: OptionalIntentClassifier?=null,
    private val capabilityExists: (String)->Boolean={false}) {
    suspend fun resolve(text: String,final: Boolean): WorkloadIntent {
        require(text.length<=32768)
        val command=deterministic(text)
        if(!final) return WorkloadIntent(ProvisionalIntentState.PARTIAL,0.0,LocalWorkload.INTENT,command)
        if(command!=null && capabilityExists(command)) return WorkloadIntent(ProvisionalIntentState.DETERMINISTIC,1.0,LocalWorkload.ACTION,command)
        val classified=classifier?.classify(text)
        if(classified!=null) {
            val intentId=classified.canonicalIntentId
            if(classified.confidence in .9..1.0 && intentId!=null && capabilityExists(intentId))
                return classified.copy(actionAuthorized=false)
        }
        return WorkloadIntent(ProvisionalIntentState.ASSISTANT,0.0,LocalWorkload.CHAT)
    }
}