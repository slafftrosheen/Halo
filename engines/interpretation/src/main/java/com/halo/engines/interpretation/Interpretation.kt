package com.halo.engines.interpretation

import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.HaloEvent
import java.util.UUID

@JvmInline
value class InterpretationId(val value: String)

data class Interpretation(
    val id: InterpretationId = InterpretationId(UUID.randomUUID().toString()),
    val observation: Observation,
    val summary: String,
    val confidence: Float,
    val targets: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

data class InterpretationCreated(
    val interpretation: Interpretation,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent
