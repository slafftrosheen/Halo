package com.halo.sdk.runtime.skills

@JvmInline
value class SkillId(val value: String)

enum class SkillCategory {
    WORKSPACE,
    DEVICE,
    COMMUNICATION,
    MEDIA,
    NAVIGATION,
    INFORMATION,
    AUTOMATION
}

enum class SkillRisk {
    READ_ONLY,
    REVERSIBLE,
    EXTERNAL_SIDE_EFFECT,
    SENSITIVE,
    DESTRUCTIVE
}

enum class HostPermission {
    MICROPHONE,
    CAMERA,
    LOCATION,
    NOTIFICATIONS,
    FILES,
    BLUETOOTH
}

sealed interface SkillRequirement {
    data class Permission(val permission: HostPermission) : SkillRequirement
    data class MinSdk(val api: Int) : SkillRequirement
    data class ToolRegistered(val toolId: String) : SkillRequirement
    data class DeviceConnected(val deviceKind: String) : SkillRequirement
    data class ProviderCapability(val capability: String) : SkillRequirement
    data class RuntimeFeature(val feature: String) : SkillRequirement
}

sealed interface SkillAvailability {
    data object Available : SkillAvailability
    data class MissingPermission(val permission: HostPermission) : SkillAvailability
    data class MissingDependency(val dependency: String) : SkillAvailability
    data object UnsupportedDevice : SkillAvailability
    data object Disabled : SkillAvailability
}

enum class SkillSource {
    BUILT_IN,
    CUSTOM,
    APP_FUNCTIONS
}

data class SkillToolDefinition(
    val toolId: String,
    val risk: SkillRisk = SkillRisk.READ_ONLY,
    val defaultApproval: String = "AUTOMATIC_SAFE"
)

data class SkillDescriptor(
    val id: SkillId,
    val name: String,
    val description: String,
    val category: SkillCategory,
    val schemaVersion: Int = 2,
    val enabled: Boolean = true,
    val tools: Set<String> = emptySet(),
    val toolDefinitions: List<SkillToolDefinition> = emptyList(),
    val requirements: List<SkillRequirement> = emptyList(),
    val requiredPermissions: Set<HostPermission> = emptySet(),
    val risk: SkillRisk = SkillRisk.READ_ONLY,
    val availability: SkillAvailability = SkillAvailability.Available,
    val source: SkillSource = SkillSource.BUILT_IN
)
