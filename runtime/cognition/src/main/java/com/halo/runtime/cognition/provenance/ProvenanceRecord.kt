package com.halo.runtime.cognition.provenance

sealed interface ProvenanceSource {
    data class Conversation(val conversationId: String, val turnId: String? = null) : ProvenanceSource
    data class WorkspaceObject(val id: String) : ProvenanceSource
    data class ImportedUri(val uriFingerprint: String) : ProvenanceSource
    data class DeviceCapture(val deviceId: String, val captureId: String) : ProvenanceSource
    data class AgentRun(val runId: String) : ProvenanceSource
}

enum class ProvenanceOperation {
    IMPORTED,
    CAPTURED,
    CREATED_FROM,
    SUMMARIZED_FROM,
    TRANSFORMED_FROM,
    EXTRACTED_FROM,
    USER_CREATED,
}

data class ProvenanceRecord(
    val objectId: String,
    val source: ProvenanceSource,
    val sourceRevision: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val operation: ProvenanceOperation = ProvenanceOperation.USER_CREATED,
    val metadata: Map<String, String> = emptyMap(),
)
