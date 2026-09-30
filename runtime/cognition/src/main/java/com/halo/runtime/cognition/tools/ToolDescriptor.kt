package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.device.CapabilityAffinity
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.resource.ResourceAccess
import com.halo.sdk.runtime.resource.ResourceId

/**
 * RUNTIME-017 portable tool fabric.
 *
 * [ToolDefinition] stays the provider-facing function schema (what the model sees).
 * [ToolDescriptor] is Runtime-owned metadata: what the tool means, what it requires,
 * how it may be exposed, retried, replayed and confirmed. No Android or vendor
 * imports may appear in this file; hosts translate descriptors into platform calls.
 */

@JvmInline
value class ToolId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class CapabilityId(val value: String) {
    override fun toString(): String = value
}

/** Semantic function of a tool. Never a platform, vendor or screen name. */
enum class ToolCategory {
    SYSTEM,
    INFORMATION,
    COMMUNICATION,
    WORKSPACE,
    FILES,
    MEDIA,
    NAVIGATION,
    AUTOMATION,
    HOST_CONTROL,
    DEVICE_CONTROL,
    PERCEPTION,
}

/** Whether the tool participates in discovery and default exposure. */
enum class ToolVisibility {
    /** Included in default bootstrap prompt exposure. */
    BOOTSTRAP,

    /** Ordinary discovery candidate via halo_discover_tools. */
    DISCOVERABLE,

    /** Never listed to models; callable only by explicit runtime wiring. */
    INTERNAL,

    /** Exists in the catalogue but hidden until something expands it. */
    HIDDEN,
}

enum class ToolRiskClass {
    READ_ONLY,
    REVERSIBLE,
    EXTERNAL_SIDE_EFFECT,
    SENSITIVE,
    DESTRUCTIVE,
}

enum class ConfirmationPolicy {
    NONE,

    /** Follow the user's stored approval policy (SkillRegistry default). */
    USER_POLICY,

    /** Always require an interactive confirmation. */
    ALWAYS,
}

/**
 * Replay semantics declared by the tool owner. Runtime uses this to decide
 * which failures may be retried transparently.
 */
enum class ReplaySafety {
    /** Re-execution cannot change state: reads and pure computations. */
    SAFE,

    /** Safe to retry only while carrying the same idempotency operation key. */
    IDEMPOTENT_WITH_KEY,

    /** Must reconcile actual external state before any retry. */
    RECONCILE_BEFORE_RETRY,

    /** Never replay automatically. */
    NEVER_REPLAY,
}

data class ToolSecurityPolicy(
    val risk: ToolRiskClass = ToolRiskClass.READ_ONLY,
    val confirmation: ConfirmationPolicy = ConfirmationPolicy.USER_POLICY,
    val replaySafety: ReplaySafety = ReplaySafety.SAFE,
) {
    companion object {
        val READ_ONLY = ToolSecurityPolicy(
            risk = ToolRiskClass.READ_ONLY,
            confirmation = ConfirmationPolicy.NONE,
            replaySafety = ReplaySafety.SAFE,
        )
        val REVERSIBLE = ToolSecurityPolicy(
            risk = ToolRiskClass.REVERSIBLE,
            confirmation = ConfirmationPolicy.USER_POLICY,
            replaySafety = ReplaySafety.SAFE,
        )
        val EXTERNAL = ToolSecurityPolicy(
            risk = ToolRiskClass.EXTERNAL_SIDE_EFFECT,
            confirmation = ConfirmationPolicy.USER_POLICY,
            replaySafety = ReplaySafety.NEVER_REPLAY,
        )
        val DESTRUCTIVE = ToolSecurityPolicy(
            risk = ToolRiskClass.DESTRUCTIVE,
            confirmation = ConfirmationPolicy.ALWAYS,
            replaySafety = ReplaySafety.NEVER_REPLAY,
        )
    }
}

enum class ToolConcurrency {
    /** May run alongside other parallel-safe tools. */
    PARALLEL_SAFE,

    /** Runs one at a time within the session. */
    SERIAL,

    /** Must not overlap with any other tool execution. */
    EXCLUSIVE,
}

data class ToolRetryPolicy(
    val maxAttempts: Int = 1,
    val delayMs: Long = 0,
    val retryableFailureKinds: Set<ToolFailureKind> = emptySet(),
) {
    companion object {
        val NONE = ToolRetryPolicy()
    }
}

data class ToolExecutionPolicy(
    val timeoutMs: Long = DEFAULT_TOOL_TIMEOUT_MS,
    val concurrency: ToolConcurrency = ToolConcurrency.SERIAL,
    val retry: ToolRetryPolicy = ToolRetryPolicy.NONE,
    val maxOutputChars: Int = DEFAULT_MAX_OUTPUT,
) {
    companion object {
        const val DEFAULT_TOOL_TIMEOUT_MS = 10_000L
        const val DEFAULT_MAX_OUTPUT = 4_000

        /** Global hard ceiling; per-tool policies are clamped to this. */
        const val HARD_CEILING_TIMEOUT_MS = 30_000L
        const val HARD_DELEGATION_TIMEOUT_MS = 90_000L
    }
}

/** Structured failure vocabulary. Retry decisions key on this, never on strings. */
enum class ToolFailureKind {
    TIMEOUT,
    CANCELLED,
    VALIDATION,
    PERMISSION,
    UNAVAILABLE,
    RESOURCE_BUSY,
    RESOURCE_UNAVAILABLE,
    TRANSIENT,
    PERMANENT,
    UNKNOWN,
}

/**
 * Semantic capability a tool needs before it can run.
 * Distinct from [com.halo.runtime.cognition.device.CapabilityRequirement],
 * which resolves physical device capabilities at execution time.
 */
sealed interface ToolRequirement {
    /** Semantic host/device capability advertised through the capability fabric. */
    data class Capability(val id: CapabilityId) : ToolRequirement

    /** Runtime permission granted by the host (host-specific permission strings are translated by adapters). */
    data class Permission(val permission: String) : ToolRequirement

    /** Another tool must be registered. */
    data class Tool(val id: ToolId) : ToolRequirement
}

/**
 * RUNTIME-019 portable presentation metadata. Drives approval copy, Activity
 * summaries and result surfaces without per-tool-name switches in hosts or
 * authorizers. Never contains icons/colors/platform concepts.
 */
data class ToolPresentation(
    val title: String,
    val actionVerb: String? = null,
    /** Argument key whose value names the target (e.g. "name", "destination"). */
    val targetArgument: String? = null,
    /** Argument keys that must never appear in previews or logs. */
    val sensitiveArguments: Set<String> = emptySet(),
)

data class ResourceRequirement(
    val resourceId: ResourceId,
    val access: ResourceAccess = ResourceAccess.EXCLUSIVE,
    val affinity: CapabilityAffinity = CapabilityAffinity.ANY,
)

data class ToolDescriptor(
    val id: ToolId,
    val definition: ToolDefinition,
    val category: ToolCategory,
    val keywords: Set<String> = emptySet(),
    val examples: List<String> = emptyList(),
    val visibility: ToolVisibility = ToolVisibility.DISCOVERABLE,
    val dependencies: Set<ToolId> = emptySet(),
    val requirements: Set<ToolRequirement> = emptySet(),
    val resources: Set<ResourceRequirement> = emptySet(),
    val security: ToolSecurityPolicy = ToolSecurityPolicy.READ_ONLY,
    val execution: ToolExecutionPolicy = ToolExecutionPolicy(),
    val presentation: ToolPresentation? = null,
    val automation: ToolAutomationMetadata = ToolAutomationMetadata(),
) {
    val name: String get() = definition.name
}
