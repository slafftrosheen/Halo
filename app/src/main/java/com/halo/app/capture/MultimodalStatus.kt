package com.halo.app.capture

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.capability.*
import com.halo.providers.litert.LiteRtLocalProvider

data class MultimodalStatus(val target: AiExecutionTarget?, val visionEvidence: String, val packs: List<RuntimeCapabilityStatus>)

object MultimodalDiagnostics {
    @Volatile var visionTarget: String = "Not inspected"
    @Volatile var visionEvidence: String = "Not inspected"
    @Volatile var capabilityStates: String = "Not inspected"
}

/** Metadata-only inspection; resolving a target never loads an engine or acquires a camera. */
suspend fun inspectMultimodal(router: AiProviderRouter): MultimodalStatus {
    val target = try {
        router.resolveExecutionTarget(AiRequest(text = "Vision configuration"),
            AiWorkloadSpec(kind = AiWorkloadKind.VISUAL_UNDERSTANDING, requiredCapabilities = setOf(AiCapability.TEXT, AiCapability.IMAGE),
                allowRemote = false, preferredRole = AiModelRole.VISION))
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { null }
    val local = target?.provider as? LiteRtLocalProvider
    val evidence = target?.modelId?.let { local?.visionQualificationForModel(it) }
    val registry = RuntimeCapabilityRegistry()
    val packs = RuntimeCapabilityRegistry.descriptors.map { descriptor ->
        val input = if (descriptor.id == RuntimeCapabilityId.LOCAL_VISION && local != null && target != null) RuntimeCapabilityEvidence(
            configured = true, installed = true, runtimeAvailable = true,
            capabilityCompatible = AiCapability.IMAGE in target.effectiveCapabilities,
            qualified = evidence == com.halo.providers.litert.VisionQualification.PROVEN,
            qualificationKey = local.visionQualificationKeyForModel(target.modelId),
            failed = evidence == com.halo.providers.litert.VisionQualification.FAILED,
        ) else RuntimeCapabilityEvidence()
        registry.resolve(descriptor, input)
    }
    val status = MultimodalStatus(target, evidence?.name?.lowercase()?.replace('_', ' ') ?: "Not configured", packs)
    MultimodalDiagnostics.visionTarget = target?.let { "${it.providerId}/${it.modelId} ${it.model.executionLocation}" } ?: "Local vision unavailable"
    MultimodalDiagnostics.visionEvidence = status.visionEvidence
    MultimodalDiagnostics.capabilityStates = packs.joinToString("; ") { "${it.descriptor.id}=${it.state}" }
    return status
}
