package com.halo.runtime.cognition.blackboard

data class KnowledgeGraph(
    val edges: Set<KnowledgeEdge> = emptySet()
) {
    fun connect(edge: KnowledgeEdge): KnowledgeGraph =
        copy(edges = edges + edge)

    fun outgoing(from: KnowledgeId): Set<KnowledgeEdge> =
        edges.filterTo(linkedSetOf()) { it.from == from }

    fun incoming(to: KnowledgeId): Set<KnowledgeEdge> =
        edges.filterTo(linkedSetOf()) { it.to == to }
}
