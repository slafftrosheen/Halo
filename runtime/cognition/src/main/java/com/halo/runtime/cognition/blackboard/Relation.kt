package com.halo.runtime.cognition.blackboard

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
