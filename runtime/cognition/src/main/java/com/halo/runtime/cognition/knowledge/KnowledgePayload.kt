package com.halo.runtime.cognition.knowledge

import com.halo.runtime.cognition.Decision
import com.halo.runtime.cognition.Goal
import com.halo.runtime.cognition.Task
import com.halo.sdk.runtime.Observation

sealed interface KnowledgePayload

data class ObservationPayload(
    val observation: Observation
) : KnowledgePayload

data class InterpretationPayload(
    val interpretation: Interpretation
) : KnowledgePayload

data class GoalPayload(
    val goal: Goal
) : KnowledgePayload

data class TaskPayload(
    val task: Task
) : KnowledgePayload

data class DecisionPayload(
    val decision: Decision
) : KnowledgePayload
