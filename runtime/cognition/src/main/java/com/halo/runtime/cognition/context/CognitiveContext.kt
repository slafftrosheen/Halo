package com.halo.runtime.cognition.context

import com.halo.icl.ontology.Goal
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.workingmemory.WorkingMemorySnapshot
import com.halo.sdk.runtime.Observation
import kotlinx.datetime.Instant

data class CognitiveContext(
    val observation: Observation,
    val workingMemory: WorkingMemorySnapshot,
    val goals: List<Goal>,
    val tasks: List<Task>,
    val timestamp: Instant
)
