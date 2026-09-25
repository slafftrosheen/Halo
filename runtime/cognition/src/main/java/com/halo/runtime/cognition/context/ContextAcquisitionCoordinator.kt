package com.halo.runtime.cognition.context

interface ContextAcquisitionCoordinator {
    suspend fun acquire(
        plan: ContextAcquisitionPlan,
        query: ContextQuery,
    ): ContextAcquisitionResult
}

class DefaultContextAcquisitionCoordinator(
    private val assistantContexts: AssistantContextRepository? = null,
    private val workspaceContext: WorkspaceContextSource? = null,
) : ContextAcquisitionCoordinator {

    override suspend fun acquire(
        plan: ContextAcquisitionPlan,
        query: ContextQuery,
    ): ContextAcquisitionResult {
        val candidates = mutableListOf<ContextCandidate>()
        val omitted = mutableListOf<OmittedContextCandidate>()
        val unsatisfied = mutableListOf<ContextNeed>()

        for (need in plan.needs) {
            when (need.kind) {
                ContextKind.SCREEN_TEXT -> {
                    val snapshot = assistantContexts?.screenFor(plan.interactionId.value)
                    if (snapshot != null) {
                        val text = snapshot.nodes.mapNotNull { it.text }.joinToString("\n")
                        candidates.add(
                            ContextCandidate(
                                id = ContextCandidateId("screen:${plan.interactionId.value}"),
                                source = ContextSource.ScreenContext,
                                content = ContextContent.Text(text),
                                trust = ContextTrust.EXTERNAL_UNTRUSTED,
                                relevance = 1.0f,
                                priority = ContextPriority.HIGH,
                                estimatedTokens = estimateTokens(text),
                                freshness = ContextFreshness.REALTIME,
                            )
                        )
                    } else if (need.requirement == ContextRequirement.REQUIRED) {
                        unsatisfied.add(need)
                    }
                }
                ContextKind.VISUAL_IMAGE -> {
                    val boundImage = assistantContexts?.imageFor(plan.interactionId.value)
                    if (boundImage != null) {
                        candidates.add(
                            ContextCandidate(
                                id = ContextCandidateId("image:${boundImage.assetId ?: plan.interactionId.value}"),
                                source = boundImage.source,
                                content = ContextContent.ImageContent(
                                    assetId = boundImage.assetId ?: "bound",
                                    mimeType = boundImage.mimeType,
                                    uriString = "bytes:${boundImage.sizeBytes}",
                                ),
                                trust = boundImage.trust,
                                relevance = 1.0f,
                                priority = ContextPriority.CRITICAL,
                                estimatedTokens = 1000,
                                freshness = ContextFreshness.REALTIME,
                            )
                        )
                    } else if (need.requirement == ContextRequirement.REQUIRED) {
                        unsatisfied.add(need)
                    }
                }
                ContextKind.WORKSPACE_REFERENCE -> {
                    if (query.activeWorkspaceObjectId != null) {
                        val refText = workspaceContext?.content(true)?.firstOrNull()
                        if (refText != null) {
                            candidates.add(
                                ContextCandidate(
                                    id = ContextCandidateId("workspace:${query.activeWorkspaceObjectId}"),
                                    source = ContextSource.ExplicitWorkspaceContext,
                                    content = ContextContent.Text(refText),
                                    trust = ContextTrust.USER_AUTHORED,
                                    relevance = 1.0f,
                                    priority = ContextPriority.HIGH,
                                    estimatedTokens = estimateTokens(refText),
                                    freshness = ContextFreshness.RECENT,
                                    objectId = query.activeWorkspaceObjectId,
                                )
                            )
                        }
                    }
                }
                else -> Unit
            }
        }

        return ContextAcquisitionResult(
            candidates = candidates,
            omitted = omitted,
            unsatisfiedRequired = unsatisfied,
        )
    }

    private fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)
}
