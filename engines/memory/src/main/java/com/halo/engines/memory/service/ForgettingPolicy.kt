package com.halo.engines.memory.service

import com.halo.runtime.cognition.memory.Memory
import kotlinx.datetime.Clock

class ForgettingPolicy {
    fun shouldForget(memory: Memory): Boolean {
        val now = Clock.System.now()
        
        // Example: If it has a TTL and it expired
        if (memory.ttl != null) {
            val expiryTime = memory.recency.plus(memory.ttl!!)
            if (now > expiryTime) {
                return true
            }
        }
        
        // Example: Decay based on low importance and low frequency
        if (memory.importance < 0.2f && memory.frequency == 1) {
            val age = now - memory.recency
            if (age.inWholeDays > 7) {
                return true
            }
        }
        
        return false
    }
}

