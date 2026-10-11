package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.skills.SkillInstructionSource
import com.halo.runtime.cognition.skills.SkillRegistry
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.skills.SkillId
import com.halo.sdk.runtime.skills.SkillAvailability

class UseSkillToolExecutor(
    private val instructionSource: SkillInstructionSource,
    /**
     * Resolved on use, not at construction: the registry is built from the tool catalogue,
     * which contains this executor, so holding it directly would close a construction cycle.
     */
    private val skillRegistryProvider: () -> SkillRegistry? = { null },
) : ToolExecutor {

    constructor(instructionSource: SkillInstructionSource, skillRegistry: SkillRegistry?) :
        this(instructionSource, { skillRegistry })

    override fun definitions(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                id = "halo_use_skill",
                name = "halo_use_skill",
                description = "Load operational guidance and enable tools for a Halo skill.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "skillId" to mapOf("type" to "string", "description" to "The skill ID to load (e.g. halo.notebook)"),
                        "offset" to mapOf("type" to "integer", "minimum" to 0, "description" to "Character offset for continuing a long skill instruction")
                    ),
                    "required" to listOf("skillId")
                )
            )
        )
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val rawSkillId = toolCall.arguments["skillId"] as? String
            ?: return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Missing 'skillId' argument")

        val skillId = SkillId(rawSkillId)
        val skillRegistry = skillRegistryProvider()
        skillRegistry?.refreshAvailability()
        val descriptor = skillRegistry?.get(skillId)
        if (skillRegistry != null && descriptor == null) {
            return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Skill $rawSkillId is not registered", com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        }
        if (descriptor != null && !descriptor.enabled) {
            return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Skill $rawSkillId is disabled", com.halo.runtime.cognition.tools.ToolRejectionKind.TOOL_DISABLED)
        }
        if (descriptor != null && descriptor.availability !is SkillAvailability.Available &&
            descriptor.availability !is SkillAvailability.PartiallyAvailable) {
            return ToolExecutionResult.Rejected(
                toolCall.id,
                toolCall.functionName,
                "Skill $rawSkillId is unavailable: ${descriptor.availability::class.simpleName}",
                kind = ToolRejectionKind.CAPABILITY_UNAVAILABLE,
            )
        }
        val instruction = instructionSource.load(skillId)
            ?: return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Skill instructions not found for $rawSkillId", com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE)

        val toolsExposed = descriptor?.tools.orEmpty().filter { name ->
            val owner = skillRegistry?.skillIdForTool(name)?.let { skillRegistry.get(it) } ?: descriptor
            owner?.enabled == true && when (val state = owner.availability) {
                SkillAvailability.Available -> true
                is SkillAvailability.PartiallyAvailable -> name !in state.unavailableTools
                else -> false
            }
        }
        val offset = (toolCall.arguments["offset"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        if (offset >= instruction.body.length && instruction.body.isNotEmpty()) {
            return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Offset is beyond the skill instruction")
        }
        val end = (offset + INSTRUCTION_CHUNK_CHARS).coerceAtMost(instruction.body.length)
        val hasMore = end < instruction.body.length

        val output = buildString {
            append("Loaded operational guidance for skill ").append(skillId.value).append(":\n")
            append("skillSchemaVersion=").append(descriptor?.schemaVersion ?: "unknown")
            append(" skillRevision=").append(instruction.revision ?: "bundled")
            append(" offset=").append(offset).append(" end=").append(end)
            append(" totalChars=").append(instruction.body.length)
            append(" hasMore=").append(hasMore).append('\n')
            append(instruction.body.substring(offset, end))
            if (hasMore) append("\nContinue with halo_use_skill skillId=").append(skillId.value).append(" offset=").append(end)
            if (toolsExposed.isNotEmpty()) {
                append("\nAvailable tools for this skill: ").append(toolsExposed.joinToString(", "))
                /*
                 * AGENTIC-001: guidance alone is not enough. The successful load must
                 * change the NEXT provider request, so the result declares the tools
                 * and the skill activation in machine-readable form. Runtime parses
                 * these lines and re-plans exposure before the next round.
                 */
                append('\n').append(ToolExposureDirective.emitSkill(skillId.value))
                append('\n').append(ToolExposureDirective.emit(toolsExposed))
            }
        }

        return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, output)
    }

    private companion object {
        const val INSTRUCTION_CHUNK_CHARS = 6_000
    }
}
