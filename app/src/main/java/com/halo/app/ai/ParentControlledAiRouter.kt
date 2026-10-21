package com.halo.app.ai

import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Transient app policy; never rewrites the user's provider, model, roles or routing preferences. */
class ParentControlledAiRouter(
    private val delegate: AiProviderRouter,
    private val remoteAllowed: () -> Boolean,
    private val policyChanges: Flow<Boolean>,
) : AiProviderRouter by delegate {
    private fun constrained(workload: AiWorkloadSpec) = workload.copy(allowRemote = workload.allowRemote && remoteAllowed())
    private fun requireRemote() {
        if (!remoteAllowed()) throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY,
            "Cloud processing is off. Use the Private / Cloud switch at the top of halo, or choose an on-device model. Kids sessions also need grown-up approval.")
    }
    private suspend fun <T> resolveWithGuidance(block: suspend () -> T): T = try { block() }
        catch (failure: AiProviderException) {
            if (!remoteAllowed()) throw AiProviderException(failure.type,
                "${failure.message}. Cloud processing is off. Use the Private / Cloud switch at the top of halo, or choose an on-device model. Kids sessions also need grown-up approval.",
                failure.details, cause = failure)
            throw failure
        }
    private suspend fun <T> whilePermitted(block: suspend () -> T): T = coroutineScope {
        requireRemote()
        val task = async(start = CoroutineStart.LAZY) { requireRemote(); block() }
        val watch = launch(start = CoroutineStart.UNDISPATCHED) {
            policyChanges.collect { allowed -> if (!allowed) task.cancel(CancellationException("Cloud permission changed")) }
        }
        try { task.await() } finally { watch.cancel() }
    }
    private fun guarded(target: AiExecutionTarget): AiExecutionTarget {
        if (target.model.executionLocation == AiExecutionLocation.ON_DEVICE) return target
        requireRemote()
        val original = target.provider
        val provider = object : AiInferenceProvider by original {
            override suspend fun generate(request: AiRequest, context: AiContext): AiResponse =
                whilePermitted { original.generate(request, context) }
            override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = channelFlow {
                whilePermitted { original.stream(request, context).collect { send(it) } }
            }
        }
        return target.copy(provider = provider)
    }
    override suspend fun resolveExecutionTarget(request: AiRequest): AiExecutionTarget =
        resolveExecutionTarget(request, AiWorkloadSpec())
    override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget =
        resolveWithGuidance { guarded(delegate.resolveExecutionTarget(request, constrained(workload))) }
    override suspend fun resolveFallbackExecutionTarget(request: AiRequest, workload: AiWorkloadSpec, failedProviderId: String): AiExecutionTarget? =
        delegate.resolveFallbackExecutionTarget(request, constrained(workload), failedProviderId)?.let(::guarded)
    override suspend fun resolvePinnedExecutionTarget(request: AiRequest, workload: AiWorkloadSpec,
        providerId: String, modelId: String): AiExecutionTarget =
        resolveWithGuidance { guarded(delegate.resolvePinnedExecutionTarget(request, constrained(workload), providerId, modelId)) }
    override suspend fun getEligibleProvider(request: AiRequest): AiProvider = resolveExecutionTarget(request).provider
    override suspend fun execute(request: AiRequest, context: AiContext): AiResponse = execute(request, AiWorkloadSpec(), context)
    override suspend fun execute(request: AiRequest, workload: AiWorkloadSpec, context: AiContext): AiResponse {
        val effective = constrained(workload)
        return if (effective.allowRemote) whilePermitted { delegate.execute(request, effective, context) }
        else resolveWithGuidance { delegate.execute(request, effective, context) }
    }
}
