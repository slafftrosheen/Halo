package com.halo.runtime.cognition.context

import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.ObservationKnowledge
import com.halo.sdk.runtime.ContextRequirement
import com.halo.sdk.runtime.ImageObservationPayload
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.VisualFreshness

interface ContextAcquisitionCoordinator {
    suspend fun acquire(
        plan: ContextAcquisitionPlan,
        query: ContextQuery,
    ): ContextAcquisitionResult
}

/**
 * Gathers context without knowing anything about providers or host SDKs.
 * Exact interaction/origin evidence is preferred; active hardware capture is
 * delegated through [VisualContextSource].
 */
class DefaultContextAcquisitionCoordinator(
    private val assistantContexts: AssistantContextRepository? = null,
    private val workspaceContext: WorkspaceContextSource? = null,
    private val blackboard: Blackboard? = null,
    private val visualContextSource: VisualContextSource? = null,
    private val tokenEstimator: ContextTokenEstimator = ConservativeContextTokenEstimator(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ContextAcquisitionCoordinator {

    override suspend fun acquire(
        plan: ContextAcquisitionPlan,
        query: ContextQuery,
    ): ContextAcquisitionResult {
        val candidates = mutableListOf<ContextCandidate>()
        val omitted = mutableListOf<OmittedContextCandidate>()
        val unsatisfied = mutableListOf<ContextNeed>()
        val mediaFailures = mutableListOf<com.halo.sdk.runtime.multimodal.MediaFailure>()
        val observations = blackboard?.flow?.value
            ?.mapNotNull { (it.payload as? ObservationKnowledge)?.observation }
            .orEmpty()

        for (need in plan.needs.filter { it.requirement != ContextRequirement.NONE }) {
            val candidate = when (need.kind) {
                ContextKind.SCREEN_TEXT -> acquireScreen(plan, need)
                ContextKind.VISUAL_IMAGE -> acquireImage(plan, query, need, observations, omitted, mediaFailures)
                ContextKind.WORKSPACE_REFERENCE -> acquireWorkspace(query)
                ContextKind.DEVICE_STATE,
                ContextKind.RECENT_EVENTS -> null
            }

            if (candidate != null) {
                candidates += candidate
            } else if (need.requirement == ContextRequirement.REQUIRED) {
                unsatisfied += need
            }
        }

        return ContextAcquisitionResult(
            candidates = candidates
                .distinctBy { it.id }
                .sortedWith(candidateComparator())
                .take(query.maximumCandidates.coerceAtLeast(1)),
            omitted = omitted,
            unsatisfiedRequired = unsatisfied,
            mediaFailures = mediaFailures,
        )
    }

    private fun acquireScreen(
        plan: ContextAcquisitionPlan,
        need: ContextNeed,
    ): ContextCandidate? {
        if (need.affinity !in setOf(ContextAffinity.EXACT_INTERACTION, ContextAffinity.ANY_RECENT)) {
            return null
        }
        val snapshot = assistantContexts?.screenFor(plan.interactionId.value) ?: return null
        val text = snapshot.nodes.asSequence()
            .flatMap { sequenceOf(it.text, it.contentDescription) }
            .filterNotNull()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString("\n")
            .take(MAX_SCREEN_TEXT_CHARS)

        if (text.isBlank()) return null

        return ContextCandidate(
            id = ContextCandidateId("screen:${plan.interactionId.value}:${snapshot.capturedAtMs}"),
            source = ContextSource.ScreenContext,
            content = ContextContent.ScreenContent(
                packageName = snapshot.packageName,
                title = snapshot.title,
                text = text,
                uri = snapshot.webUri,
                capturedAtMs = snapshot.capturedAtMs,
                provenance = snapshot.provenance,
            ),
            trust = ContextTrust.EXTERNAL_UNTRUSTED,
            relevance = 1.0f,
            priority = ContextPriority.CRITICAL,
            estimatedTokens = tokenEstimator.estimate(text),
            freshness = ContextFreshness.REALTIME,
        )
    }

    private suspend fun acquireImage(
        plan: ContextAcquisitionPlan,
        query: ContextQuery,
        need: ContextNeed,
        observations: List<Observation>,
        omitted: MutableList<OmittedContextCandidate>,
        failures: MutableList<com.halo.sdk.runtime.multimodal.MediaFailure>,
    ): ContextCandidate? {
        val freshness = need.visualFreshness ?: VisualFreshness.RECENT_CAPTURE

        query.explicitImage?.let { image ->
            return if (image.interactionId == plan.interactionId.value) imageCandidate(image) else null
        }

        // Exact assistant screenshot always carries the same interaction id.
        if (need.affinity == ContextAffinity.EXACT_INTERACTION) {
            assistantContexts?.imageFor(plan.interactionId.value)
                ?.takeIf { imageFreshEnough(it, freshness) }
                ?.let { return imageCandidate(it) }
        }

        val existing = selectImageObservation(
            observations = observations,
            interactionId = plan.interactionId.value,
            originDeviceId = query.origin?.hostId,
            affinity = need.affinity,
            freshness = freshness,
        )
        if (existing != null) {
            return imageCandidate(existing.toImageContext(freshness))
        }

        // A REFERENCE must already exist by stable identity; never replace it
        // with a fresh camera capture.
        if (freshness == VisualFreshness.REFERENCE) return null

        val originDeviceId = query.origin?.hostId
        if (originDeviceId != null &&
            need.affinity in setOf(ContextAffinity.ORIGIN_DEVICE, ContextAffinity.ANY_RECENT)
        ) {
            val captureResult = visualContextSource?.capture(
                VisualContextCaptureRequest(
                    interactionId = plan.interactionId,
                    originDeviceId = originDeviceId,
                    freshness = freshness,
                )
            )
            val captured = captureResult?.getOrNull()
            captureResult?.exceptionOrNull()?.let { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                failures += (error as? com.halo.sdk.runtime.multimodal.MediaAcquisitionException)?.failure
                    ?: com.halo.sdk.runtime.multimodal.MediaFailure.TRANSFER_FAILED
            }

            if (captured != null && imageFreshEnough(captured, freshness)) {
                return imageCandidate(captured)
            }
        }

        return null
    }

    private suspend fun acquireWorkspace(query: ContextQuery): ContextCandidate? {
        val objectId = query.activeWorkspaceObjectId ?: return null
        val owner = workspaceContext
        val snapshot = if (query.explicitWorkspaceText != null) {
            // The host already resolved this selection. Recheck its owner token; never
            // replace a stale explicit selection with another object's current content.
            val text = query.explicitWorkspaceText.takeIf(String::isNotBlank) ?: return null
            if (owner?.resolvedTextRequiresVersion == true) {
                val expected = query.workspaceVersion ?: return null
                val current = owner.snapshot(objectId, expected) ?: return null
                if (current.objectId != objectId || current.version != expected) return null
                // A range selected at the same document revision cannot authorize arbitrary
                // older whole-source inline text or another range's already-copied excerpt.
                if (current.textAnchor != null && current.text != text) return null
                current.copy(text = text)
            } else {
            val before = owner?.versionFor(objectId)
            if (query.workspaceVersion != null && before != query.workspaceVersion) return null
            // An unversioned old excerpt cannot acquire today's owner revision merely
            // because the owner was stable while this already-copied text was checked.
            if (query.workspaceVersion == null && before != null) return null
            val after = owner?.versionFor(objectId)
            if (before != after || query.workspaceVersion != null && after != query.workspaceVersion) return null
            WorkspaceContextEntry(objectId, text, query.workspaceVersion)
            }
        } else owner?.snapshot(objectId, query.workspaceVersion) ?: return null
        if (snapshot.objectId != objectId || query.workspaceVersion != null && snapshot.version != query.workspaceVersion) return null
        val text = snapshot.text
        return ContextCandidate(
            id = ContextCandidateId("workspace:$objectId"),
            source = ContextSource.ExplicitWorkspaceContext,
            content = ContextContent.Text(text),
            trust = ContextTrust.EXTERNAL_UNTRUSTED,
            relevance = 1.0f,
            priority = ContextPriority.HIGH,
            estimatedTokens = tokenEstimator.estimate(text),
            freshness = ContextFreshness.RECENT,
            objectId = objectId,
            sourceVersion = snapshot.version,
            textAnchor = snapshot.textAnchor,
        )
    }

    private fun selectImageObservation(
        observations: List<Observation>,
        interactionId: String,
        originDeviceId: String?,
        affinity: ContextAffinity,
        freshness: VisualFreshness,
    ): Observation? {
        val imageObservations = observations.asReversed()
            .filter { it.payload is ImageObservationPayload }

        val matching = when (affinity) {
            ContextAffinity.EXACT_INTERACTION ->
                imageObservations.filter { it.interactionId == interactionId }
            ContextAffinity.ORIGIN_DEVICE ->
                imageObservations.filter {
                    originDeviceId != null &&
                        (it.sourceDeviceId == originDeviceId || it.origin?.hostId == originDeviceId)
                }
            ContextAffinity.EXPLICIT_REFERENCE ->
                emptyList()
            ContextAffinity.ANY_RECENT ->
                if (freshness == VisualFreshness.REFERENCE) imageObservations.filter { it.interactionId == interactionId } else imageObservations
        }

        return matching.firstOrNull { observationFreshEnough(it, freshness) }
    }

    private fun Observation.toImageContext(freshness: VisualFreshness): ImageContext {
        val payload = payload as ImageObservationPayload
        return ImageContext(
            imageBytes = payload.imageBytes.copyOf(),
            mediaRef = payload.mediaRef,
            mimeType = payload.mimeType,
            timestampMs = timestamp,
            assetId = id.value,
            interactionId = interactionId,
            originDeviceId = sourceDeviceId ?: origin?.hostId,
            source = if (origin?.source == com.halo.sdk.host.ObservationSource.AndroidAssist) {
                ContextSource.AssistantScreenshot
            } else {
                ContextSource.VisualObservation
            },
            trust = ContextTrust.EXTERNAL_UNTRUSTED,
            freshness = freshness,
        )
    }

    private fun imageCandidate(image: ImageContext): ContextCandidate =
        ContextCandidate(
            id = ContextCandidateId(
                "image:${image.assetId ?: image.interactionId ?: image.timestampMs}"
            ),
            source = image.source,
            content = ContextContent.ImageContent(image),
            trust = image.trust,
            relevance = 1.0f,
            priority = ContextPriority.CRITICAL,
            // Image tokenization is provider-specific. This is only a stable
            // generic budget estimate, never a measured provider token count.
            estimatedTokens = APPROX_IMAGE_TOKENS,
            freshness = when (image.freshness) {
                VisualFreshness.CURRENT_VIEW -> ContextFreshness.REALTIME
                VisualFreshness.RECENT_CAPTURE -> ContextFreshness.RECENT
                VisualFreshness.REFERENCE -> ContextFreshness.RECENT
            },
            objectId = image.assetId,
        )

    private fun observationFreshEnough(
        observation: Observation,
        freshness: VisualFreshness,
    ): Boolean = ageAccepted(observation.timestamp, freshness)

    private fun imageFreshEnough(
        image: ImageContext,
        freshness: VisualFreshness,
    ): Boolean = ageAccepted(image.timestampMs, freshness)

    private fun ageAccepted(timestampMs: Long, freshness: VisualFreshness): Boolean {
        if (freshness == VisualFreshness.REFERENCE) return true
        val age = nowMs() - timestampMs
        return age >= 0L && age <= freshness.maxAgeMs
    }

    private fun candidateComparator(): Comparator<ContextCandidate> =
        compareBy<ContextCandidate>(
            { priorityRank(it.priority) },
            { freshnessRank(it.freshness) },
            { -it.relevance },
            { it.id.value },
        )

    private fun priorityRank(priority: ContextPriority): Int = when (priority) {
        ContextPriority.CRITICAL -> 0
        ContextPriority.HIGH -> 1
        ContextPriority.NORMAL -> 2
        ContextPriority.LOW -> 3
    }

    private fun freshnessRank(freshness: ContextFreshness): Int = when (freshness) {
        ContextFreshness.REALTIME -> 0
        ContextFreshness.RECENT -> 1
        ContextFreshness.STALE -> 2
    }

    private companion object {
        const val MAX_SCREEN_TEXT_CHARS = 14 * 1024
        const val APPROX_IMAGE_TOKENS = 1_000
    }
}
