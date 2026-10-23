package com.halo.runtime.cognition.tools

/** Additive feature integration contract. Does not authorize, execute or attest a voice/provider test. */
enum class ToolInputChannel { TEXT, VOICE_FINAL }
enum class ToolOperation { QUERY, CREATE, EDIT, EXPORT, PRESENT, SELECT }
enum class ToolResultEncoding { TEXT, JSON }
data class ToolOperability(
    val version: Int = 1,
    val capabilityId: CapabilityId,
    val owner: String,
    val operation: ToolOperation,
    val channels: Set<ToolInputChannel> = ToolInputChannel.entries.toSet(),
    val resultEncoding: ToolResultEncoding,
    val resultSchema: Map<String, Any>,
    val sourceVersionArguments: Set<String> = emptySet(),
    val resultIdFields: Set<String> = emptySet(),
    val limitations: Set<String> = emptySet(),
)

data class ToolOperabilityProblem(val toolId: String, val code: String)
data class ToolFeatureOperability(val featureId: String, val toolIds: Set<String>, val exception: String? = null)

/** Review gate over actual registered descriptors. Availability/authorization remain with their existing owners. */
object ToolOperabilityReview {
    fun inspect(features: List<ToolFeatureOperability>, descriptors: List<ToolDescriptor>): List<ToolOperabilityProblem> {
        val registered = descriptors.associateBy { it.name }
        return buildList {
            features.forEach { feature ->
                if (feature.toolIds.isEmpty() && feature.exception.isNullOrBlank()) add(ToolOperabilityProblem(feature.featureId, "NO_CALLABLE_OPERATION"))
                feature.toolIds.forEach tool@ { id ->
                    val descriptor = registered[id]
                    if (descriptor == null) { add(ToolOperabilityProblem(id, "NOT_REGISTERED")); return@tool }
                    val contract = descriptor.operability
                    if (contract == null) { add(ToolOperabilityProblem(id, "NO_CONTRACT")); return@tool }
                    fun requireField(valid: Boolean, code: String) { if (!valid) add(ToolOperabilityProblem(id, code)) }
                    requireField(contract.version == 1, "UNSUPPORTED_CONTRACT")
                    requireField(contract.capabilityId.value.isNotBlank() && contract.owner.isNotBlank(), "MISSING_OWNER")
                    requireField(contract.channels == ToolInputChannel.entries.toSet(), "INPUT_PARITY_MISSING")
                    requireField(descriptor.definition.description.isNotBlank(), "NO_DISCOVERY_DESCRIPTION")
                    val schema = descriptor.definition.parameters
                    val properties = schema["properties"] as? Map<*, *> ?: emptyMap<Any, Any>()
                    requireField(schema["type"] == "object" && schema["additionalProperties"] == false, "ARGUMENTS_NOT_CLOSED")
                    requireField(contract.sourceVersionArguments.all { it in properties }, "MISSING_SOURCE_VERSION_FIELD")
                    requireField(contract.resultSchema["type"] == if (contract.resultEncoding == ToolResultEncoding.JSON) "object" else "string", "RESULT_SCHEMA_MISSING")
                    val resultFields = contract.resultSchema["properties"] as? Map<*, *> ?: emptyMap<Any, Any>()
                    requireField(contract.resultIdFields.all { it in resultFields }, "MISSING_RESULT_ID_FIELD")
                    requireField(descriptor.execution.timeoutMs > 0, "NO_TIMEOUT")
                    // A keyed replay declaration is meaningful only if the function actually accepts that key.
                    if (descriptor.security.replaySafety == ReplaySafety.IDEMPOTENT_WITH_KEY)
                        requireField(properties.keys.any { it in setOf("operationId", "handoffId", "idempotencyKey") }, "NO_REPLAY_KEY")
                }
            }
        }.distinct()
    }
}
