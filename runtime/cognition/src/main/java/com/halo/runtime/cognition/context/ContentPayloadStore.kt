package com.halo.runtime.cognition.context

enum class PayloadClassification { TOOL_RESULT, WEB_CONTENT, MEMORY_RESULT, FILE_EXCERPT, MCP_RESULT, CHILD_AGENT_RESULT, MODEL_AUXILIARY }
enum class PayloadOwnerKind { AGENT_RUN, CONVERSATION, WORKSPACE, MEMORY }
data class PayloadOwner(val kind: PayloadOwnerKind, val id: String)
data class PayloadRef(
    val id: String,
    val sha256: String,
    val mediaType: String,
    val byteCount: Long,
    val createdAtMs: Long,
    val classification: PayloadClassification,
    val trust: ContextTrust,
    val provenance: String,
    val remoteAllowed: Boolean = false,
)
enum class LongTaskFailure {
    CONTEXT_BUDGET_EXCEEDED, COMPACTION_UNAVAILABLE, PAYLOAD_STORE_FULL, PAYLOAD_MISSING,
    DELEGATION_NOT_ALLOWED, DELEGATION_DEPTH_EXCEEDED, CHILD_LIMIT_REACHED, CHILD_FAILED,
    CHILD_CANCELLED, SOURCE_NOT_ALLOWED, EVIDENCE_TOO_LARGE,
}
sealed interface PayloadOutcome<out T> {
    data class Value<T>(val value: T) : PayloadOutcome<T>
    data class Failed(val reason: LongTaskFailure) : PayloadOutcome<Nothing>
}
data class PayloadLimits(
    val singleBytes: Long = 4L * 1024 * 1024,
    val perOwnerBytes: Long = 8L * 1024 * 1024,
    val totalBytes: Long = 64L * 1024 * 1024,
    val graceMs: Long = 24L * 60 * 60 * 1000,
    val readBytes: Int = 8 * 1024,
)
data class PayloadStoreStats(val count: Int, val bytes: Long, val referencedBytes: Long, val unreferencedBytes: Long, val oldestUnreferencedMs: Long?)

/** The opaque reference plus owner authorizes access; a digest alone never does. */
interface ContentPayloadStore {
    suspend fun put(owner: PayloadOwner, bytes: ByteArray, classification: PayloadClassification,
        trust: ContextTrust, provenance: String, remoteAllowed: Boolean = false,
        mediaType: String = "text/plain; charset=utf-8"): PayloadOutcome<PayloadRef>
    suspend fun read(owner: PayloadOwner, refId: String, offset: Long = 0,
        maxBytes: Int = 4096, targetIsLocal: Boolean = true): PayloadOutcome<ByteArray>
    suspend fun reference(owner: PayloadOwner, refId: String): PayloadRef?
    suspend fun retain(source: PayloadOwner, refId: String, destination: PayloadOwner): Boolean
    suspend fun release(owner: PayloadOwner)
    suspend fun stats(): PayloadStoreStats
    suspend fun collectUnreferenced(): Int
}
