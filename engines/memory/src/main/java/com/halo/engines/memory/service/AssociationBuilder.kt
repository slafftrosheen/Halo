package com.halo.engines.memory.service

import com.halo.runtime.cognition.memory.Memory
import com.halo.runtime.cognition.memory.RelationshipMemory
import com.halo.engines.memory.store.KnowledgeStore

class AssociationBuilder(private val knowledgeStore: KnowledgeStore) {
    suspend fun buildAssociation(sourceId: String, targetId: String, type: String): RelationshipMemory {
        val relation = RelationshipMemory(
            importance = 1.0f,
            recency = kotlinx.datetime.Clock.System.now(),
            confidence = 1.0f,
            frequency = 1,
            sourceNodeId = sourceId,
            targetNodeId = targetId,
            relationType = type
        )
        knowledgeStore.save(relation)
        return relation
    }
}

