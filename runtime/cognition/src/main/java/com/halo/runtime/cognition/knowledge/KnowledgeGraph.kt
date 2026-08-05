package com.halo.runtime.cognition.knowledge

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

data class KnowledgeEdge(
    val from: KnowledgeId,
    val to: KnowledgeId,
    val relation: RelationType
)

enum class RelationType {
    CAUSED_BY,
    REFERS_TO,
    DERIVED_FROM,
    LOCATED_AT,
    BELONGS_TO,
    RELATED_TO
}
