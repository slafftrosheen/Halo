package com.halo.app.ai

import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Transient app policy; never rewrites the user's provider, model, roles or routing preferences. */
class ParentControlledAiRouter(
    private val delegate: AiProviderRouter,
    private val remoteAllowed: () -> Boolean,
    private val policyChanges: Flow<Boolean>,
    private val profiles: PrivacyModeStore? = null,
) : AiProviderRouter by delegate {
    override val remoteDispatchRevision: Long? get() = profiles?.revision
    private fun constrained(workload: AiWorkloadSpec) = workload.copy(allowRemote = workload.allowRemote && remoteAllowed(),
        allowFallback = workload.allowFallback && (profiles == null || profiles.mode.value == PrivacyMode.PRIVATE ||
            profiles.profile(PrivacyMode.CLOUD).localFallback))
    private fun requireRemote() {
        if (!remoteAllowed()) throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY,
            "Cloud processing is off. Use the Private / Full Power switch at the top of Halo, or choose an on-device model. Kids sessions also need grown-up approval.")
    }
    private suspend fun <T> resolveWithGuidance(block: suspend () -> T): T = try { block() }
        catch (failure: AiProviderException) {
            if (!remoteAllowed()) throw AiProviderException(failure.type,
                "${failure.message}. Cloud processing is off. Use the Private / Full Power switch at the top of Halo, or choose an on-device model. Kids sessions also need grown-up approval.",
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
    private fun guarded(target: AiExecutionTarget, workload: AiWorkloadSpec, intentRevision: Long?): AiExecutionTarget {
        profiles?.takeIf { it.mode.value == PrivacyMode.CLOUD }?.profile(PrivacyMode.CLOUD)?.let { profile ->
            val role = AiRoleRequirements.role(workload)
            val binding = delegate.roleBinding(role)
            val expected = if (role != AiModelRole.CHAT && binding.selection == AiRoleSelection.EXPLICIT)
                binding.providerId else profile.providerId
            val qualifiedLocalRole = role != AiModelRole.CHAT && binding.selection == AiRoleSelection.AUTOMATIC &&
                target.model.executionLocation == AiExecutionLocation.ON_DEVICE
            val explicitLocalFallback = profile.localFallback && target.model.executionLocation == AiExecutionLocation.ON_DEVICE
            if (target.providerId != expected && !qualifiedLocalRole && !explicitLocalFallback)
                throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE,
                    "The Full Power profile does not select this provider. Review Mode profiles.")
        }
        if (target.model.executionLocation == AiExecutionLocation.ON_DEVICE) return target
        requireRemote()
        val original = target.provider
        val provider = object : AiInferenceProvider by original {
            override suspend fun generate(request: AiRequest, context: AiContext): AiResponse =
                withProfileIntent(target, intentRevision, request, context) { whilePermitted { original.generate(request, context) } }
            override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = channelFlow {
                withProfileIntent(target, intentRevision, request, context) {
                    whilePermitted { original.stream(request, context).collect { send(it) } }
                }
            }
        }
        return target.copy(provider = provider)
    }
    private suspend fun <T> withProfileIntent(target: AiExecutionTarget, revision: Long?, request: AiRequest,
        context: AiContext, block: suspend () -> T): T {
        val policy = profiles ?: return block()
        val categories = buildSet {
            addAll(context.remoteDataCategories)
            add(RemoteDataCategory.TEXT)
            if (request.image != null) add(RemoteDataCategory.IMAGES)
            if (request.audio != null) add(RemoteDataCategory.AUDIO)
            if (request.video != null) add(RemoteDataCategory.VIDEO)
            if (context.memories.isNotEmpty()) add(RemoteDataCategory.MEMORIES)
            if (context.screenContext != null) add(RemoteDataCategory.SCREEN)
            if (context.deviceState != null) add(RemoteDataCategory.DEVICE_STATE)
            if (context.recentEvents.isNotEmpty()) add(RemoteDataCategory.RECENT_EVENTS)
            if (context.availableTools.isNotEmpty()) add(RemoteDataCategory.TOOLS)
        }
        val intent = RemoteDispatchIntent(RemoteDispatchRequest(target.providerId, RemoteOperation.INFERENCE, categories),
            revision ?: policy.revision)
        val job = currentCoroutineContext().job
        val lease = policy.acquire(intent.request, intent.revision, recordActivity = false) { job.cancel(CancellationException("Mode profile changed")) }
        try {
            lease.requireCurrent()
            val result = withContext(RemoteDispatchContext.intent.asContextElement(intent)) { block() }
            lease.finish(RemoteDispatchOutcome.COMPLETED)
            return result
        } catch (failure: Throwable) {
            lease.finish(if (failure is CancellationException) RemoteDispatchOutcome.CANCELLED else RemoteDispatchOutcome.FAILED)
            throw failure
        }
    }
    override suspend fun resolveExecutionTarget(request: AiRequest): AiExecutionTarget =
        resolveExecutionTarget(request, AiWorkloadSpec())
    override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget {
        val revision = profiles?.revision
        return resolveWithGuidance { guarded(delegate.resolveExecutionTarget(request, constrained(workload)), workload, revision) }
    }
    override suspend fun resolveFallbackExecutionTarget(request: AiRequest, workload: AiWorkloadSpec, failedProviderId: String): AiExecutionTarget? {
        val revision = profiles?.revision
        val effective = constrained(workload)
        if (!effective.allowFallback) return null
        // Full Power permits an explicit local fallback, never another remote destination.
        val fallback = delegate.resolveFallbackExecutionTarget(request,
            if (profiles != null) effective.copy(allowRemote = false) else effective, failedProviderId) ?: return null
        return guarded(fallback, effective, revision)
    }
    override suspend fun resolvePinnedExecutionTarget(request: AiRequest, workload: AiWorkloadSpec,
        providerId: String, modelId: String): AiExecutionTarget {
        val revision = profiles?.revision
        return resolveWithGuidance { guarded(delegate.resolvePinnedExecutionTarget(request, constrained(workload), providerId, modelId), workload, revision) }
    }
    override suspend fun getEligibleProvider(request: AiRequest): AiProvider = resolveExecutionTarget(request).provider
    override suspend fun execute(request: AiRequest, context: AiContext): AiResponse = execute(request, AiWorkloadSpec(), context)
    override suspend fun execute(request: AiRequest, workload: AiWorkloadSpec, context: AiContext): AiResponse {
        val intentRevision = profiles?.revision
        val effective = constrained(workload)
        return if (profiles != null) {
            val target = resolveExecutionTarget(request, effective)
            val pinned = effective.copy(pinnedTarget = AiModelRef(target.providerId, target.modelId))
            if (target.model.executionLocation == AiExecutionLocation.ON_DEVICE) delegate.execute(request, pinned, context)
            else withProfileIntent(target, intentRevision, request, context) { delegate.execute(request, pinned, context) }
        } else if (effective.allowRemote) whilePermitted { delegate.execute(request, effective, context) }
        else resolveWithGuidance { delegate.execute(request, effective, context) }
    }
}
