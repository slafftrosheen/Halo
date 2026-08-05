package com.halo.runtime.cognition

import com.halo.sdk.runtime.Observation

interface Brain {
    suspend fun perceive(observation: Observation)
    suspend fun think()
    suspend fun decide(): Decision
}
