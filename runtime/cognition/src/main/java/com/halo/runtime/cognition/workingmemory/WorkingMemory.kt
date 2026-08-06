package com.halo.runtime.cognition.workingmemory

import com.halo.icl.ontology.Goal
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.blackboard.Interpretation
import com.halo.cognition.language.PlaceEntity
import com.halo.cognition.language.PersonEntity
import com.halo.sdk.runtime.Observation

interface WorkingMemory {
    suspend fun currentGoal(): Goal?

    suspend fun activeTask(): Task?

    suspend fun latestObservation(): Observation?

    suspend fun latestInterpretation(): Interpretation?

    suspend fun focusedPerson(): PersonEntity?

    suspend fun currentLocation(): PlaceEntity?
}
