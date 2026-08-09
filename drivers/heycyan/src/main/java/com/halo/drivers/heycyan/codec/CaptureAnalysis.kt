package com.halo.drivers.heycyan.codec

const val CURRENT_ANALYSIS_FORMAT_VERSION = 1

enum class ProtocolFindingKind {
    CONNECTION_HANDSHAKE,
    HEARTBEAT,
    ACK,
    NACK,
    BATTERY_UPDATE,
    BUTTON_PRESS,
    STATE_TRANSITION,
    UNKNOWN
}

enum class AnalysisConfidence {
    OBSERVED,
    CORRELATED,
    VERIFIED,
    HYPOTHESIS,
    UNKNOWN
}

/**
 * A durable pointer into an immutable capture packet. A null offset/length
 * refers to the complete packet; otherwise the range is [offset, offset + length).
 */
data class PacketEvidenceReference(
    val captureId: String,
    val packetSequence: Long,
    val offset: Int? = null,
    val length: Int? = null
)

/** An analyst's interpretation, kept separate from the raw capture document. */
data class PacketAnnotation(
    val annotationId: String,
    val target: PacketEvidenceReference,
    val kind: ProtocolFindingKind,
    val confidence: AnalysisConfidence = AnalysisConfidence.UNKNOWN,
    val note: String? = null,
    val evidence: List<PacketEvidenceReference> = emptyList()
)

/**
 * A set of annotations over one or more imported captures. This is persisted as
 * an analysis sidecar and never written into [BleCapture].
 */
data class CaptureAnalysis(
    val schemaVersion: Int = CURRENT_ANALYSIS_FORMAT_VERSION,
    val analysisId: String,
    val captureIds: List<String> = emptyList(),
    val annotations: List<PacketAnnotation> = emptyList()
)

internal object CaptureAnalysisValidator {
    fun validate(analysis: CaptureAnalysis, captures: Collection<BleCapture>) {
        require(analysis.analysisId.isNotBlank()) { "Analysis ID must not be blank" }
        require(analysis.captureIds.distinct().size == analysis.captureIds.size) {
            "Analysis capture IDs must be unique"
        }

        val capturesById = captures.associateBy { it.captureId }
        require(capturesById.keys.containsAll(analysis.captureIds)) {
            val missing = analysis.captureIds.filterNot(capturesById::containsKey)
            "Analysis references unavailable captures: ${missing.joinToString()}"
        }
        val declaredCaptureIds = analysis.captureIds.toSet()
        val annotationIds = mutableSetOf<String>()

        analysis.annotations.forEach { annotation ->
            require(annotation.annotationId.isNotBlank()) {
                "Annotation ID must not be blank"
            }
            require(annotationIds.add(annotation.annotationId)) {
                "Duplicate annotation ID: ${annotation.annotationId}"
            }
            validateReference(annotation.target, declaredCaptureIds, capturesById)
            annotation.evidence.forEach { evidence ->
                validateReference(evidence, declaredCaptureIds, capturesById)
            }
        }
    }

    private fun validateReference(
        reference: PacketEvidenceReference,
        declaredCaptureIds: Set<String>,
        capturesById: Map<String, BleCapture>
    ) {
        require(reference.captureId in declaredCaptureIds) {
            "Evidence capture is not declared by the analysis: ${reference.captureId}"
        }
        val capture = requireNotNull(capturesById[reference.captureId])
        val packet = capture.packets.firstOrNull { it.sequence == reference.packetSequence }
        require(packet != null) {
            "Packet sequence ${reference.packetSequence} was not found in capture ${reference.captureId}"
        }

        when {
            reference.offset == null -> require(reference.length == null) {
                "Evidence length requires an evidence offset"
            }
            else -> {
                require(reference.offset >= 0) { "Evidence offset must not be negative" }
                require(reference.offset <= packet.payload.size) {
                    "Evidence offset exceeds packet length in capture ${reference.captureId}"
                }
                if (reference.length != null) {
                    require(reference.length >= 0) { "Evidence length must not be negative" }
                    require(reference.offset + reference.length <= packet.payload.size) {
                        "Evidence range exceeds packet length in capture ${reference.captureId}"
                    }
                }
            }
        }
    }
}