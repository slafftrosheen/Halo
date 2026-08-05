package com.halo.sdk.runtime

sealed interface Fact

interface Blackboard {
    suspend fun add(fact: Fact)
    suspend fun remove(fact: Fact)
    suspend fun query(predicate: (Fact) -> Boolean): List<Fact>
}
