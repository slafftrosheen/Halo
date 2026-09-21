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

data class SkillDescriptor(
    val id: SkillId,
    val name: String,
    val description: String,
    val category: SkillCategory,
    val enabled: Boolean = true,
    val tools: Set<String> = emptySet(),
    val requiredPermissions: Set<HostPermission> = emptySet(),
    val risk: SkillRisk = SkillRisk.READ_ONLY,
    val availability: SkillAvailability = SkillAvailability.Available,
    val source: SkillSource = SkillSource.BUILT_IN
)
