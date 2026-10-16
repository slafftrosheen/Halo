package com.halo.app.ai

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*

/** Speculation never selects a different model, starts a download or initiates a remote request. */
class WorkloadPreparationController(private val context: Context,private val storage: Storage,
    private val models: LiteRtModelManager,private val provider: ()->LiteRtLocalProvider?,
    private val resources: NativeResourceCoordinator,private val lab: ()->WorkloadLabController,
    private val qualifications: WorkloadQualificationStore) {
    suspend fun prepare(target: AiExecutionTarget,workload: AiWorkloadSpec): Boolean {
        if(!storage.getBoolean("workload_prewarm_enabled",false) || !target.provider.isLocal) return false
        val local=provider() ?: return false
        val descriptor=models.resolveModel(target.modelId)?.takeIf { it.verified } ?: return false
        val kind=LocalWorkload.from(AiRoleRequirements.role(workload))
        val candidate=lab().candidate(descriptor,kind)
        resources.budgetReevaluation?.invoke()
        val budget=resources.budget.value
        val thermal=if(Build.VERSION.SDK_INT>=29) context.getSystemService(PowerManager::class.java).currentThermalStatus else 3
        val request=WorkloadRequest(kind,qualifications.environment(local,descriptor,kind,budget.allowConcurrentHeavyweights),availableBytes=budget.availableBytes)
        val decision=WorkloadPreparationPolicy.decide(candidate,request,WorkloadPreparationConditions(true,budget.totalBytes,budget.availableBytes,
            budget.pressure==NativeMemoryPressure.NORMAL,thermal,
            resources.snapshot.value.any { it.kind==NativeResourceKind.HEAVY_ASR && it.executing },false))
        if(!decision.prepare) return false
        currentCoroutineContext().ensureActive()
        val configured=storage.getString("workload.execution.v1.${kind.name}")!=null
        val profile=WorkloadPreferences(storage).profile(kind).takeIf { configured }
        // Existing provider owns the actual lease and model lock, including cancellation cleanup.
        return local.loadModelIfNeeded(target.modelId,AiRequest(modelId=target.modelId,priority=InferencePriority.BACKGROUND,localExecutionProfile=profile))
    }
}
