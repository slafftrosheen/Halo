package com.halo.app.di

import com.halo.sdk.runtime.skills.SkillDescriptor
import com.halo.sdk.runtime.skills.SkillId
import com.halo.sdk.runtime.skills.SkillRisk
import com.halo.sdk.runtime.skills.SkillToolDefinition

/**
 * Merge policy for built-in skill declarations.
 *
 * Most of Halo's built-in skills are declared twice: in the Runtime registry, and as a shipped
 * manifest that additionally carries schema version, required permissions and content provenance.
 * Exactly one declaration wins the skill identity, and the losing declaration must never make the
 * effective result weaker than either input.
 *
 * That invariant is why this is not a plain "last one wins" map. Without it an asset silently
 * shipped a weaker risk than the registry for the same skill id, which is the worst possible
 * direction for a classification that gates approval. The merged descriptor therefore keeps the
 * strictest declared risk at both skill and tool level, and unions tools and permissions so that
 * neither declaration can drop a requirement the other asserted.
 */
internal object BuiltInSkillMerge {

    /**
     * @param defaults registry-owned declarations; the fallback for ids with no shipped manifest.
     * @param manifests shipped asset manifests, which win the skill identity when an id matches.
     */
    fun merge(defaults: List<SkillDescriptor>, manifests: List<SkillDescriptor>): List<SkillDescriptor> {
        val byId = LinkedHashMap<SkillId, SkillDescriptor>()
        defaults.forEach { byId[it.id] = it }
        manifests.forEach { manifest ->
            val existing = byId[manifest.id]
            byId[manifest.id] = if (existing == null) manifest else hardened(manifest, existing)
        }
        return byId.values.toList()
    }

    /**
     * [manifest] supplies identity, schema and provenance; [defaults] is consulted for the risk
     * floor, the tool/permission union and the approval defaults of tools it declared first.
     */
    private fun hardened(manifest: SkillDescriptor, defaults: SkillDescriptor): SkillDescriptor {
        val approvals = defaults.toolDefinitions.associate { it.toolId to it.defaultApproval }
        val toolRisks = LinkedHashMap<String, SkillRisk>()
        defaults.toolDefinitions.forEach { toolRisks[it.toolId] = it.risk }
        manifest.toolDefinitions.forEach { definition ->
            toolRisks[definition.toolId] = strictest(toolRisks[definition.toolId], definition.risk)
        }
        return manifest.copy(
            risk = strictest(manifest.risk, defaults.risk),
            tools = defaults.tools + manifest.tools,
            requiredPermissions = defaults.requiredPermissions + manifest.requiredPermissions,
            toolDefinitions = toolRisks.map { (toolId, risk) ->
                val approval = approvals[toolId]
                if (approval == null) {
                    SkillToolDefinition(toolId = toolId, risk = risk)
                } else {
                    SkillToolDefinition(toolId = toolId, risk = risk, defaultApproval = approval)
                }
            },
        )
    }

    /**
     * `SkillRisk` declares its constants in ascending severity, so ordinal order is severity order.
     * A null left-hand side means the tool was declared by only one of the two documents.
     */
    private fun strictest(candidate: SkillRisk?, other: SkillRisk): SkillRisk =
        if (candidate == null || other.ordinal >= candidate.ordinal) other else candidate
}
