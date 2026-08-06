package com.halo.runtime.cognition.workingmemory

import com.halo.icl.ontology.Goal
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.blackboard.Interpretation
import com.halo.runtime.cognition.blackboard.Knowledge
import com.halo.cognition.language.PlaceEntity
import com.halo.cognition.language.PersonEntity
import com.halo.sdk.runtime.Observation

data class WorkingMemorySnapshot(
    val currentGoal: Goal?,
    val activeTask: Task?,
    val latestObservation: Observation?,
    val latestInterpretation: Interpretation?,
    val focusedPerson: PersonEntity?,
    val currentLocation: PlaceEntity?,
    val recentKnowledge: List<Knowledge> = emptyList()
)

suspend fun WorkingMemory.snapshot(
    recentKnowledge: List<Knowledge> = emptyList()
): WorkingMemorySnapshot = WorkingMemorySnapshot(
    currentGoal = currentGoal(),
    activeTask = activeTask(),
    latestObservation = latestObservation(),
    latestInterpretation = latestInterpretation(),
    focusedPerson = focusedPerson(),
    currentLocation = currentLocation(),
    recentKnowledge = recentKnowledge
)
