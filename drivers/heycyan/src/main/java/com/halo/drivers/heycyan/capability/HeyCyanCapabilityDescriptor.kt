package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.CapabilityStatus
import com.halo.drivers.heycyan.protocol.ProtocolEvidenceReference

data class HeyCyanCapabilityDescriptor(
    val id: String,
    val name: String,
    val status: CapabilityStatus,
    val evidence: List<ProtocolEvidenceReference>,
    val reason: String? = null,
)
