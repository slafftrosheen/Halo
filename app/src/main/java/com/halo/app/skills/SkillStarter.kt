package com.halo.app.skills

import com.halo.runtime.cognition.skills.BundledSkillInstructionSource
import com.halo.sdk.runtime.skills.*
import java.util.UUID

/** Starters customize guidance while retaining the existing host tools and permission gates. */
enum class SkillStarter(val title: String, val builtInId: String) {
    WORKSPACE("Workspace helper", "halo.notebook"),
    APPS("Phone app helper", "halo.apps"),
    STUDY("Study helper", "halo.study"),
    CALENDAR("Calendar helper", "halo.calendar");

    val instructions: String get() = BundledSkillInstructionSource.defaultBuiltInInstructions().getValue(builtInId)

    fun packageBytes(name: String, instructions: String, source: SkillDescriptor, id: String = "custom.${UUID.randomUUID()}"): ByteArray {
        require(source.id.value == builtInId && source.source == SkillSource.BUILT_IN) { "Skill starter is unavailable" }
        require(name.isNotBlank() && name.length <= 80 && name.none { it == '\n' || it == '\r' || it == '"' }) {
            "Use a skill name of 1 to 80 characters without quotes or line breaks"
        }
        val skill = source
        val permissions = skill.requiredPermissions + skill.toolDefinitions.flatMap { it.requiredPermissions } +
            skill.requirements.filterIsInstance<SkillRequirement.Permission>().map { it.permission }
        val risk = (skill.toolDefinitions.map { it.risk } + skill.risk).maxBy { it.ordinal }
        val bytes = buildString {
            appendLine("---")
            appendLine("id: $id")
            appendLine("name: \"${name.trim()}\"")
            appendLine("description: ${skill.description}")
            appendLine("category: ${skill.category.name}")
            appendLine("risk: ${risk.name}")
            appendLine("tools: ${skill.tools.sorted().joinToString(",")}")
            appendLine("permissions: ${permissions.sortedBy { it.name }.joinToString(",") { it.name }}")
            appendLine("requirements: ${skill.requirements.joinToString(",") { requirement -> when (requirement) {
                is SkillRequirement.Permission -> "permission:${requirement.permission.name}"
                is SkillRequirement.MinSdk -> "minsdk:${requirement.api}"
                is SkillRequirement.ToolRegistered -> "tool:${requirement.toolId}"
                is SkillRequirement.DeviceConnected -> "device:${requirement.deviceKind}"
                is SkillRequirement.ProviderCapability -> "provider:${requirement.capability}"
                is SkillRequirement.RuntimeFeature -> "runtime:${requirement.feature}"
            } }}")
            appendLine("schemaVersion: 2")
            appendLine("---")
            append(instructions)
        }.toByteArray(Charsets.UTF_8)
        SkillMarkdownPackageParser.parse("starter:${this.name}", bytes)
        return bytes
    }
}
