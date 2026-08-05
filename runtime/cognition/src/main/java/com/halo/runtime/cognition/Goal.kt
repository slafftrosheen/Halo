package com.halo.runtime.cognition

import java.util.UUID

enum class GoalStatus {
    PENDING,
    EXECUTING,
    COMPLETED,
    CANCELLED,
    FAILED,
    EXPIRED
}

sealed interface Goal {
    val id: UUID
    val status: GoalStatus
    val priority: Int
    val confidence: Float
}

data class ConversationGoal(
    override val id: UUID = UUID.randomUUID(),
    override val status: GoalStatus = GoalStatus.PENDING,
    override val priority: Int = 0,
    override val confidence: Float = 1.0f,
    val topic: String
) : Goal

data class NavigationGoal(
    override val id: UUID = UUID.randomUUID(),
    override val status: GoalStatus = GoalStatus.PENDING,
    override val priority: Int = 1,
    override val confidence: Float = 1.0f,
    val destination: String
) : Goal

data class ReminderGoal(
    override val id: UUID = UUID.randomUUID(),
    override val status: GoalStatus = GoalStatus.PENDING,
    override val priority: Int = 0,
    override val confidence: Float = 1.0f,
    val reminderText: String
) : Goal

data class MediaGoal(
    override val id: UUID = UUID.randomUUID(),
    override val status: GoalStatus = GoalStatus.PENDING,
    override val priority: Int = 0,
    override val confidence: Float = 1.0f,
    val query: String
) : Goal

data class CommunicationGoal(
    override val id: UUID = UUID.randomUUID(),
    override val status: GoalStatus = GoalStatus.PENDING,
    override val priority: Int = 1,
    override val confidence: Float = 1.0f,
    val target: String
) : Goal

data class SearchGoal(
    override val id: UUID = UUID.randomUUID(),
    override val status: GoalStatus = GoalStatus.PENDING,
    override val priority: Int = 0,
    override val confidence: Float = 1.0f,
    val query: String
) : Goal

data class AutomationGoal(
    override val id: UUID = UUID.randomUUID(),
    override val status: GoalStatus = GoalStatus.PENDING,
    override val priority: Int = 0,
    override val confidence: Float = 1.0f,
    val action: String
) : Goal

data class LearningGoal(
    override val id: UUID = UUID.randomUUID(),
    override val status: GoalStatus = GoalStatus.PENDING,
    override val priority: Int = 0,
    override val confidence: Float = 1.0f,
    val subject: String
) : Goal
