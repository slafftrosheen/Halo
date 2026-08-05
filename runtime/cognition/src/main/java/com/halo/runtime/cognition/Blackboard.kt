package com.halo.runtime.cognition

interface Blackboard {
    suspend fun publish(fact: Fact)
    suspend fun facts(): List<Fact>
}
