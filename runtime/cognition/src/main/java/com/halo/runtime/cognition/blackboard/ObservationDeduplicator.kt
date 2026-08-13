package com.halo.runtime.cognition.blackboard

import com.halo.sdk.runtime.Observation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ObservationDeduplicator {
    private val latestObservationState = mutableMapOf<String, Any?>()
    private val latestObservationFreshness = mutableMapOf<String, com.halo.sdk.runtime.Freshness>()

    /**
     * Filters a stream of observations, only emitting those that represent a meaningful state change.
     * State change is defined as either:
     * 1. The payload value has changed.
     * 2. The freshness status has changed (e.g. went from FRESH to STALE).
     */
    fun deduplicate(observations: Flow<Observation>): Flow<Observation> = flow {
        observations.collect { observation ->
            val key = "${observation.sourceDeviceId}_${observation.payload::class.java.simpleName}"
            val payload = observation.payload
            val freshness = observation.freshness

            val lastPayload = latestObservationState[key]
            val lastFreshness = latestObservationFreshness[key]

            if (payload != lastPayload || freshness != lastFreshness) {
                latestObservationState[key] = payload
                latestObservationFreshness[key] = freshness
                emit(observation)
            }
        }
    }
}
