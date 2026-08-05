package com.halo.runtime.kernel.service

import com.halo.runtime.kernel.Proposal

class ConsensusEngine {
    fun evaluate(proposals: List<Proposal>): Proposal? {
        if (proposals.isEmpty()) return null
        
        // Simple consensus: highest confidence wins.
        // In reality, this would weight by agent authority or domain.
        return proposals.maxByOrNull { it.confidence }
    }
}
