package com.halo.app.data.workspace

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WorkspaceRelationType {
    SOURCE_OF,
    DERIVED_FROM,
    REFERENCES,
    RELATED_TO,
    CONTINUES,
    ATTACHMENT_OF,
    GENERATED_FROM
}

@Entity(tableName = "workspace_relations")
data class WorkspaceRelationEntity(
    @PrimaryKey val id: String,
    val fromId: String,
    val type: String,
    val toId: String,
    val createdAt: Long
)
