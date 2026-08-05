package com.halo.runtime.kernel

import java.util.UUID

sealed interface AgentMessage {
    val id: UUID
    val senderId: String
    val receiverId: String?
}

data class Question(
    override val id: UUID = UUID.randomUUID(),
    override val senderId: String,
    override val receiverId: String?,
    val query: String
) : AgentMessage

data class Request(
    override val id: UUID = UUID.randomUUID(),
    override val senderId: String,
    override val receiverId: String?,
    val action: String,
    val payload: Any?
) : AgentMessage

data class Proposal(
    override val id: UUID = UUID.randomUUID(),
    override val senderId: String,
    override val receiverId: String?,
    val proposalType: String,
    val confidence: Float,
    val reasoning: String,
    val payload: Any?
) : AgentMessage

data class Response(
    override val id: UUID = UUID.randomUUID(),
    override val senderId: String,
    override val receiverId: String,
    val replyToId: UUID,
    val payload: Any?
) : AgentMessage

data class Notification(
    override val id: UUID = UUID.randomUUID(),
    override val senderId: String,
    override val receiverId: String?,
    val eventType: String,
    val message: String
) : AgentMessage

data class Reservation(
    override val id: UUID = UUID.randomUUID(),
    override val senderId: String,
    override val receiverId: String,
    val resource: String,
    val requiredTimeMs: Long
) : AgentMessage
