package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.skills.SkillInstructionSource
import com.halo.runtime.cognition.skills.SkillRegistry
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.skills.SkillId

class UseSkillToolExecutor(
    private val instructionSource: SkillInstructionSource,
    private val skillRegistry: SkillRegistry? = null,
) : ToolExecutor {

    override fun definitions(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                id = "halo_use_skill",
                name = "halo_use_skill",
                description = "Load operational guidance and enable tools for a Halo skill.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "skillId" to mapOf("type" to "string", "description" to "The skill ID to load (e.g. halo.notebook)")
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
        val instruction = instructionSource.load(skillId)
            ?: return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Skill instructions not found for $rawSkillId")

        val descriptor = skillRegistry?.get(skillId)
        val toolsExposed = descriptor?.tools ?: emptyList()

        val output = buildString {
            append("Loaded operational guidance for skill ").append(skillId.value).append(":\n")
            append("skillSchemaVersion=").append(descriptor?.schemaVersion ?: "unknown")
            append(" skillRevision=").append(instruction.revision ?: "bundled").append('\n')
            append(instruction.body.take(2000))
            if (toolsExposed.isNotEmpty()) {
                append("\nAvailable tools for this skill: ").append(toolsExposed.joinToString(", "))
            }
        }

        return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, output)
    }
}
